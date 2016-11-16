/**
 * <p>Title: liteBatch</p>
 * <p>Description: 一个轻量级，高性能的快速批插工具</p>
 * <p>Copyright: Copyright (c) 2016</p>
 * @author Bryan.Zhang
 * @email 47483522@qq.com
 * @Date 2016-11-10
 * @version 1.0
 */
package com.litesalt.batch.handler;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import com.litesalt.batch.entity.DBColumnMetaData;
import com.litesalt.batch.entity.WrapItem;
import com.litesalt.batch.util.CamelCaseUtils;
import com.litesalt.batch.util.Reflections;

/**
 * 批插处理器
 */
public class RowBatchHandler<T> {
	
	private final Logger logger = Logger.getLogger(RowBatchHandler.class);
	
	private JdbcTemplate jdbcTemplate;
	
	private BlockingQueue<WrapItem<T>> queue;
	
	private Class<T> clazz;
	
	private String insertSql;
	
	private int queueCapacity = 1024*1024;
	
	private int submitCapacity;
	
	private Thread rowBatchThread;
	
	private Field[] fields;
	
	private long startTimeMillis;
	
	private Map<String, String> aliasMap;
	
	private List<String> excludeField = new ArrayList<String>();
	
	private Map<String, DBColumnMetaData> metaMap = new HashMap<String, DBColumnMetaData>();
	
	public RowBatchHandler(JdbcTemplate jdbcTemplate,int submitCapacity,Class<T> clazz) {
		this.jdbcTemplate = jdbcTemplate;
		this.queue = new LinkedBlockingQueue<WrapItem<T>>(queueCapacity);
		this.submitCapacity = submitCapacity;
		this.clazz = clazz;
		
		if(initDBMetaData()){
			fields = clazz.getDeclaredFields();
			prepareSql();
			excludeField.add("id");
			excludeField.add("serialVersionUID");
			
			rowBatchThread = new Thread(){
				public void run() {
					new RowDeal().deal();
				};
			};
			rowBatchThread.start();
			
			startTimeMillis = System.currentTimeMillis();
		}
	}
	
	public void insertWithBatch(WrapItem<T> item){
		try {
			queue.put(item);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public WrapItem<T> take(){
		try {
			return queue.take();
		} catch (InterruptedException e) {
			logger.error("take is interrupted", e);
			return null;
		}
	}
	
	public Class<T> getClazz(){
		return clazz;
	}
	
	private class RowDeal{
		
		private final Logger log = Logger.getLogger(RowDeal.class);
		
		private final List<T> batchList = new ArrayList<T>();
		
		public void deal(){
			while(true){
				try{
					WrapItem<T> wrapItem = take();
					//如果从队列里取到的值为关闭监听的信号，则做完剩余的缓冲List里的数据，线程退出
					if(wrapItem.isShutdownSignature()){
						rowBatch();
						break;
					}
					batchList.add(wrapItem.getT());
					if(batchList.size() >= submitCapacity){
						rowBatch();
					}
				}catch(Exception e){
					log.error("批次插入发生异常", e);
					break;
				}
			}
			logger.info("this batch spend " + (System.currentTimeMillis()-startTimeMillis) + " millisecond");
			log.info("linstenr is shut down!");
		}
		
		private void rowBatch() throws Exception{
			log.info("开始批次插入");
			jdbcTemplate.batchUpdate(insertSql, new BatchPreparedStatementSetter() {
				@Override
				public void setValues(PreparedStatement ps, int i) throws SQLException {
					T t = batchList.get(i);
					Object o = null;
					DBColumnMetaData metaData = null;
					int n = 0;
					for(Field field : fields){
						if(!excludeField.contains(field.getName())){
							n++;
							o = Reflections.invokeGetter(t, field.getName());
							metaData = metaMap.get(getAliasField(field.getName()));
							
							//如果值为null，还要看默认值，如果有默认值，取元数据中的默认值
							if(o == null){
								if(metaData.getColumnDef() == null){
									ps.setNull(n, Types.NULL);
									continue;
								}
								o = metaData.getColumnDef();
							}
							
							switch(metaData.getDataType()){
								case Types.CHAR:
									ps.setString(n, (String)o);
									break;
								case Types.VARCHAR:
									ps.setString(n, (String)o);
									break;
								case Types.NVARCHAR:
									ps.setString(n, (String)o);
									break;
								case Types.SMALLINT:
									ps.setShort(n, Short.parseShort(o.toString()));
									break;
								case Types.INTEGER:
									ps.setInt(n, Integer.parseInt(o.toString()));
									break;
								case Types.BIGINT:
									ps.setLong(n, Long.parseLong(o.toString()));
									break;
								case Types.TIMESTAMP:
									ps.setTimestamp(n, new Timestamp(((Date)o).getTime()));
									break;
								case Types.DECIMAL:
									ps.setBigDecimal(n, (BigDecimal)o);
									break;
							}
						}
					}
				}
				
				@Override
				public int getBatchSize() {
					return batchList.size();
				}
			});
			batchList.clear();
		}
	}
	
	private void prepareSql(){
		StringBuffer sql = new StringBuffer();
		sql.append(" insert into ").append(getAliasTable(clazz.getSimpleName()));
		sql.append("(");
		int i = 0;
		int m = 0;
		for(Field field : clazz.getDeclaredFields()){
			i++;
			if(!excludeField.contains(field.getName())){
				m++;
				sql.append(getAliasField(field.getName()));
				if(i < clazz.getDeclaredFields().length){
					sql.append(",");
				}
			}
		}
		sql.append(") values(");
		for(int n = 0;n<m; n++){
			sql.append("?");
			if(n<m-1){
				sql.append(",");
			}
			
		}
		sql.append(")");
		insertSql = sql.toString();
	}
	
	public void shutDownHandler(){
		try {
			//往队列插入一个关闭的信号，队列处理监听到关闭信号会退出监听
			WrapItem<T> wrapItem = new WrapItem<T>();
			wrapItem.setShutdownSignature(true);
			queue.put(wrapItem);
		} catch (InterruptedException e) {
			logger.error("shut down cause error",e);
		}
	}
	
	private boolean initDBMetaData(){
		boolean flag = false;
		try{
			DatabaseMetaData metaData = jdbcTemplate.getDataSource().getConnection().getMetaData();
			ResultSet rs = metaData.getColumns(null, "%", getAliasTable(clazz.getSimpleName()), "%");
			while(rs.next()){
				metaMap.put(rs.getString("COLUMN_NAME"), new DBColumnMetaData(rs.getString("COLUMN_NAME"), rs.getInt("DATA_TYPE"), rs.getObject("COLUMN_DEF")));
			}
			flag = true;
		}catch(Exception e){
			logger.error("init db metadata wrong", e);
			flag = false;
		}
		return flag;
	}
	
	private String getAliasTable(String poName){
		if(aliasMap!= null && aliasMap.containsKey("TABLE")){
			return aliasMap.get("TABLE");
		}else{
			return CamelCaseUtils.toUnderScoreCase(poName);
		}
	}
	
	private String getAliasField(String fieldName){
		if(aliasMap!= null && aliasMap.containsKey(fieldName)){
			return aliasMap.get(fieldName);
		}else{
			return CamelCaseUtils.toUnderScoreCase(fieldName);
		}
	}
	
	public void aliasTable(String tableName){
		if(aliasMap == null){
			aliasMap = new HashMap<String, String>();
		}
		aliasMap.put("TABLE", tableName);
	}
	
	public void aliasField(String fieldName,String columnName){
		if(aliasMap == null){
			aliasMap = new HashMap<String, String>();
		}
		aliasMap.put(fieldName, columnName);
	}
	
	public void addExcludeField(String fieldName){
		excludeField.add(fieldName);
	}
}