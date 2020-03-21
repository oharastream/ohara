# Test case: JDBC to Topic to Stream to HDFS

## K8S Mode
- [Create new workspace with three nodes](#create-new-workspace-with-three-nodes)
- [Create two topics into the workspace](#create-two-topics-in-the-workspace)
- [Create new pipeline](#create-new-pipeline)
- [Add jdbc source connector, topics and a hdfs sink connector](#add-jdbc-source-connector-topics-and-hdfs-sink-connector)
- [Link JDBC source -> Stream -> HDFS sink](#link-jdbc-source---stream---hdfs-sink)
- [Prepare the required table and data on the PostgreSQL server](#prepare-the-required-table-and-data-on-the-postgresql-server)
- [Start all components on the pipeline page](#start-all-components-on-the-pipeline-page)
- [Verify which test data was successfully dumped to HDFS](#verify-which-test-data-was-successfully-dumped-to-hdfs)

## Docker Mode
Will accomplish this section in [another issue](https://github.com/oharastream/ohara/issues/3696)

---

### Create new workspace with three nodes

1. Click _QUICK START_ button.
1. In the **About this workspace** step, enter "wk01" in the text field.
1. Click _NEXT_ button.
1. In the **Select nodes** step, click _Select nodes_ button.
1. Select three of available nodes from the Nodes list.
1. Click _SAVE_ button.
1. In the **Upload or select worker plugins(Optional)** step, Click _NEXT_.
1. In the **Create this workspace** step, make sure
   1. **Workspace Name** is "wk01".
   1. **Node Names** has three nodes which are decided by us.
   1. **Plugins** is empty.
1. Click _FINISH_ button.
1. Wait the creation finished, the url will be redirected to `http://{url}/wk01`

### Create two topics in the workspace

1. Click th down arrow of workspace "wk01".
1. Select _Topics_.
1. Click _ADD TOPIC_.
1. Enter "topic1", "1", "1" in order of form fields.
1. Click _ADD TOPIC_ again.
1. Enter "topic2", "1", "1" in order of form fields.
1. Click the left arrow to go back the homepage.

### Create new pipeline

1. Click the plus symbol besides the _Pipelines_ to add a pipeline.
1. Enter "pipeline1" in the text field.
1. Click _ADD_ button.

### Add jdbc source connector, topics and hdfs sink connector

1. Click _Source_ in the _Toolbox_.
1. Drag and drop the **JDBCSourceCOnnector** in the paper.
1. Enter "jdbc" in the text field.
1. Click _ADD_ button.
1. Click _Topic_ in the _Toolbox_.
1. Drag and drop the **topic1** and **topic2** in the paper.
1. Click _Sink_ in the _Toolbox_.
1. Drag and drop the **HDFSSink** in the paper.
1. Enter "hdfs" in the text field.
1. Click _ADD_ button.

### Add stream jar

1. Click _Stream_ in the _Toolbox_.
1. 1. Click **Add streams** plus symbol in the _Toolbox_.
1. Browse to the location of your jar file `ohara-it-stream.jar`, click the file, and click Open.
      > if You don't know how to build a stream app jar, see this [link](#how-to-get-ohara-it-streamjar) for how
1. Press F5 to fetch stream class in the _Toolbox_.

### Link JDBC source -> Stream -> HDFS sink

**Set up jdbc source connector:**

1. On the **pipeline1** page, click the **jdbc** component in the pipeline graph.
1. Select the **COMMON** tab, fill out the form with the following config:
   1. Enter "<jdbc_url>" (jdbc url for PostgreSQL server) in the **jdbc url** field.
   1. Enter "<database_username>" in the **user name** field.
   1. Enter "<database_password>" in the **password** field.
   1. Enter "<database_table>" in the **table name** field.
   1. Enter "<table_timestamp>" in the **timestamp column name** field.
   1. Change flush size from 1000 to 10 in the **JDBC flush Size** field.
1. Select the **CORE** tab, select the **t3** from the **Topics** dropdown.

**Set up hdfs sink connector:**

1. On the **pipeline1** page, click the **hdfs** component in the pipeline graph.
2. Select the **COMMON** tab and fill out the following config:
   1. Enter "/data" in the Output Folder field
   2. Change **Flush Size** value from "1000" to "5"
   3. Check the **File Need Header** checkbox
   4. Enter "hdfs://`<hdfs_host>`:`<hdfs_port>`" in the **HDSF URL** field
3. Select the **CORE** tab and choose **t2** from the **Topics** dropdown.

### Prepare the required table and data on the PostgreSQL server

**Check database has table and data:**

1. Open a terminal and log into the PostgreSQL server.

```
$ psql -h <PostgreSQL_server_ip> -W <database_name> -U <user_name>
```

2. check table is exist

```
postgres=# \dt
          List of relations
 Schema |    Name     | Type  | Owner
--------+-------------+-------+-------
 public | person_data | table | ohara
 public | test_data   | table | ohara
(2 rows)
```

3. check table info

```
postgres=# \d person_data
                        Table "public.person_data"
  Column   |            Type             | Collation | Nullable | Default
-----------+-----------------------------+-----------+----------+---------
 index     | integer                     |           | not null |
 name      | character varying           |           |          |
 age       | integer                     |           |          |
 id        | character varying           |           |          |
 timestamp | timestamp without time zone |           |          | now()

```

4. check table has data

```
postgres=# select * from person_data;
 index |  name   | age |     id     |      timestamp
-------+---------+-----+------------+---------------------
     1 | Sam     |  33 | H123378803 | 2019-03-08 18:52:00
     2 | Jay     |  25 | A159330943 | 2019-03-08 18:53:00
     3 | Leon    |  31 | J156498160 | 2019-03-08 19:52:00
     4 | Stanley |  40 | D113134484 | 2019-03-08 20:00:00
     5 | Jordan  |  21 | U141236791 | 2019-03-08 20:10:20
     6 | Kayden  |  20 | E290773637 | 2019-03-09 18:52:59
     7 | Dillon  |  28 | M225842758 | 2019-03-09 20:52:59
     8 | Ross    |  33 | F229128254 | 2019-03-09 20:15:59
     9 | Gunnar  |  50 | Q107872026 | 2019-03-09 21:00:59
    10 | Tyson   |  26 | N197744193 | 2019-03-09 21:05:59
..........
```

[How to create table and insert data?](#how-to-create-table-and-insert-data)

### Start all components on the pipeline page

1. On the **pipeline1** page.
1. Click the _PIPELINE_ Actions on the top tab.
1. Click _Start all components_.

### Verify which test data was successfully dumped to HDFS

1. Open a terminal and ssh to HDFS server.
2. List all CSV files in **/data/wk00-t2/partition0** folder:

```sh
$ hdfs dfs -ls /data/wk00-t2/partition0

# You should see something similar like this in your terminal:
/data/wk00-t2/partition0/part-000000000-000000005.csv
```

3. View the content of **part-000000000-000000005.csv**:

```sh
$ hdfs dfs -cat /data/wk00-t2/partition0/part-000000000-000000005.csv

# The result should be like the following:
ID,NAME,CREATE_AT
1,ohara1,2019-03-01 00:00:01
```

#### How to create table and insert data?

1. Create table **person_data**.

```
postgres=# create table person_data (
postgres=#   index INTEGER NOT NULL,
postgres=#   name character varying,
postgres=#   age INTEGER,
postgres=#   id character varying,
postgres=#   timestamp timestamp without time zone DEFAULT NOW()
postgres=# );
```

2. insert data into table **person_data**.

```
postgres=# insert into person_data (index,name,age,id)values(1,'Sam',33,'H123378803'),
	(2,'Jay',25,'A159330943'),
	(3,'Leon',31,'J156498160'),
	(4,'Stanley',40,'D113134484'),
	(5,'Jordan',21,'U141236791'),
	(6,'Kayden',20,'E290773637'),
	(7,'Dillon',28,'M225842758'),
	(8,'Ross',33,'F229128254'),
	(9,'Gunnar',50,'Q107872026'),
	(10,'Tyson',26,'N197744193');
```
#### How to get ohara-it-stream.jar?

Open a new terminal from your machine and go to Ohara's source folder.

```sh
cd ohara/
```

Then `cd` to Stream DumbStream source folder.

```sh
cd ohara-it/src/main/scala/oharastream/ohara/it/stream/
```

Use Vi to edit **DumbStream.scala**

```sh
vi DumbStream.scala
```

Enter "I" key from your keyboard to activate the "INSERT" mode

```
-- INSERT --
```

Overwrite DumbStream class from

```
class DumbStream extends Stream {
  override def start(ostream: OStream[Row], configs: StreamSetting): Unit = {
    // do nothing but only start stream and write exactly data to output topic
    ostream.start()
  }
}
```

To

```
import oharastream.ohara.common.data.Row
import oharastream.ohara.common.setting.SettingDef
import oharastream.ohara.stream.config.StreamSetting
import oharastream.ohara.stream.{OStream, Stream}

class DumbStream extends Stream {

  override def config(): StreamDefinitions = StreamDefinitions
    .`with`(
      SettingDef
        .builder()
        .key("filterValue")
        .displayName("filter value")
        .documentation("filter the row that contains this value")
        .build())

  override def start(ostream: OStream[Row], configs: StreamDefinitions): Unit = {

    ostream
      // we filter row which the cell values contain the pre-defined "filterValue". Note:
      // 1) configs.string("filterValue") will try to get the value from env and it should NOT be null
      // 2) we ignore case for the value (i.e., "aa" = "AA")
      .filter(
      r =>
        r.cells()
          .stream()
          .anyMatch(c => {
            c.value().toString.equalsIgnoreCase(configs.string("filterValue"))
          }))
      .start()
  }
}
```

Press "Esc" key to leave the insert mode and enter `:wq` to save and exit from this file:

```
:wq
```

Build stream jar

```sh
# Make sure you're at the project root `/ohara`, then build the jar with:
./gradlew clean :ohara-it:jar -PskipManager
```

Go to stream jar folder and list the jars that you have

```sh
cd ohara-it/build/libs/ && ls

# You should see something like this in your terminal:
ohara-it-sink.jar ohara-it-source.jar ohara-it-stream.jar
```