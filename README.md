
##liteBatch
liteBatch��һ���������������ܣ���ͨ�õ������ܡ�

* �ܹ�����ͨinsertһ����ѭ���в���PO
* �첽ִ�У�������
* ά��һ������Ķ��У��������õķ�ֵ֮�������ύ
* ���Ժ͸���ORM���ʹ��
* ���ݸ������ݿ�
* ��Ӧ���е�VO���Զ����ɽű�
* ���ܸ�Ч�����Ի��ϲ��Դ��4w+/��
* �Զ�������ֻ������͵�����
* ֧���Զ����ӳ��͹����ֶ�

##���¼�¼
1.0.1
* �Ż��˶��п��Ƶ�ʵ�ֻ���
* ������redis���е�ʵ��
* ѡ��ʹ��redis����ʱ�������������崻������ݲ��ᶪʧ��100%����������
* �����˶��н����ȼ�ػ��ƣ��������߳̿���

1.1.0
* ����ļ������������
* �Ż�����ṹ�����handler��queue������
* ʵ��Aliasϵ��ע�⣬���Զ�javabean�Զ������ӳ�䵽���ݿ��ֶλ��
* ���flush������ʵ���ֶ�ͬ�����ݵ���Ӧ����Ŀ�꣨DB��file��
* ���syn��ǣ���ʾ�Ƿ��ֶ�ͬ�����ݣ�Ĭ���첽���β���
* �޸�һЩbug

##����ʹ��
Ҳ���Բο�test���̵�testUnit

```java
		RowBatchListener<Person> rowBatchListener = RowBatchListenerBuilder.buildRedisRowBatchListener(jdbcTemplate, 5000, Person.class, "localhost",6379);
		try {
			Random random = new Random();
			Person person = null;
			for (int i = 0; i < 66000; i++) {
				person = new Person();
				person.setAge(random.nextInt(100));
				person.setAddress("XX��·1��");
				person.setCompany("���� ���ϿƼ����޹�˾");
				person.setName("����");
				person.setCreateTime(new Date());
				rowBatchListener.insertOneWithBatch(person);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		}
```

##ע��
��mysql���ݿ��£���Ҫע�����¼���

* ������һ����5.1.13�汾���ϣ�����
* ��jdbc����url��ü���rewriteBatchedStatements=true����