
##liteBatch
liteBatch��һ���������������ܣ���ͨ�õ����幤��

* �ܹ�����ͨinsertһ������PO����������̵߳���һ�������Զ��ύ��
* ���Ժ͸���ORM���ʹ��
* ��Ӧ���е�PO�������κ�����
* ���ܸ�Ч�����Ի��ϲ��Դ��4w+/��
* �Զ�����NULLֵ
* ֧���Զ����ӳ��͹���

##���ʹ��
Ҳ���Բο�test���̵�testUnit

```java
    RowBatchListener<Person> rowBatchListener = new RowBatchListener<>(jdbcTemplate, 5000, Person.class);//����һ��������
	try{
		Random random = new Random();
		Person person = null;
		for(int i=0;i<500500;i++){
			person = new Person();
			person.setAge(random.nextInt(100));
			person.setAddress("XX��·1��");
			person.setCompany("XX�Ƽ����޹�˾");
			person.setName("����");
			person.setCreateTime(new Date());
			rowBatchListener.insertOneWithBatch(person);//������ѭ���н��в���
		}
	}catch(Exception e){
		e.printStackTrace();
	}finally{
		rowBatchListener.closeListener();//һ��Ҫ�رռ�����������finally��ر�
	}
```

##ע��
��mysql���ݿ��£���Ҫע�����¼���

* ������һ����5.1.13�汾���ϣ�����
* ��jdbc����url��ü���rewriteBatchedStatements=true����