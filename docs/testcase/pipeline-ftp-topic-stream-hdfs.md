# Test case: FTP to Topic to Stream to HDFS

- [Create a new workspace with three nodes](#create-a-new-workspace-with-three-nodes)
- [Create two topic and upload a stream app jar into the workspace](#create-two-topic-and-upload-a-stream-app-jar-into-the-workspace)
- [Create a new pipeline, add some connectors, topics and a stream app](#create-a-new-pipeline--add-some-connectors--topics-and-a-stream-app)
- [FTP source -> Topic -> Stream -> Topic -> HDFS sink](#ftp-source---topic---stream---topic---hdfs-sink)
- [Prepare the required folders and test data on the FTP server](#prepare-the-required-folders-and-test-data-on-the-ftp-server)
- [Start all connectors and stream app](#start-all-connectors-and-stream-app)
- [Verify which test data was successfully dumped to HDFS](#verify-which-test-data-was-successfully-dumped-to-hdfs)

## Create a new workspace with three nodes

**Add three nodes:**

1. On the Nodes page, click the <kbd>NEW NODE</kbd> button.
2. Enter `<ohara_node_host>` in the **Node** field.
3. Enter `<ohara_node_port>` in the **Port** field.
4. Enter `<node_user>` in the **User** field.
5. Enter `<node_password>` in the **Password** field.
6. Click <kbd>TEST CONNECTION</kbd> to test your connection.
7. If the test passes, click <kbd>ADD</kbd>.
8. Repeat the above steps to add **three nodes**.

**Create a new workspace:**

1. On the Workspaces page, click the <kbd>NEW WORKSPACE</kbd> button.
2. Enter “wk00” in the **Name** field.
3. Select all available nodes from the Node List.
4. Click <kbd>ADD</kbd>.

## Create two topics and upload a stream app jar in the workspace

**Add two topics:**

1. On the **Workspaces** > **wk00** > **TOPICS** tab, click the <kbd>NEW TOPIC</kbd> button.
2. Enter “t1” in the **Topic name** field and enter default value in other fields, click <kbd>ADD</kbd>.
3. Enter “t2” in the **Topic name** field and enter default value in other fields, click <kbd>ADD</kbd>.

**Add a stream jar:**

1. On the **Workspaces** > **wk00** > **STREAM JARS** tab, click the <kbd>NEW JAR</kbd> button.
2. Browse to the location of your jar file `ohara-it-stream.jar`, click the file, and click <kbd>Open</kbd>.
   > if You don't know how to build a stream app jar, see this [link](#how-to-get-ohara-it-streamjar) for how

## Create a new pipeline, add some connectors, topics and a stream app

1. On the **Pipelines** list page, click the <kbd>NEW PIPELINE</kbd> button.
2. Enter “firstpipeline” in the **Pipeline name** field and select “wk00” from the Workspace name dropdown. Then, click <kbd>ADD</kbd>.
3. Click the **Add a source connector** icon and select **oharastream.ohara.connector.ftp.FTPSource** from the list, then click <kbd>ADD</kbd>.
4. Enter “ftpsource” in the **myconnector** field and click <kbd>ADD</kbd>.
5. Click the **Add a topic** icon and select **t1** from the dropdown and click <kbd>ADD</kbd>.
6. Click the **Add a stream app** icon and select **ohara-it-stream.jar** from the dropdown, then click <kbd>ADD</kbd>.
7. Enter “dumb” in the **mystream** field and click <kbd>ADD</kbd>.
8. Click the **Add a topic** icon and select **t2** from the dropdown, then click <kbd>ADD</kbd>.
9. Click the **Add a sink connector** icon and select **oharastream.ohara.connector.hdfs.sink.HDFSSink** from the list, then click <kbd>ADD</kbd>.
10. Enter “hdfssink” in the **myconnector** field and click <kbd>ADD</kbd>.

## FTP source -> Topic -> Stream -> Topic -> HDFS sink

**Set up ftpsource connector:**

1. On the **firstpipeline** page, click the **ftpsource** graph in the pipeline graph.
2. Select the **COMMON** tab and fill out the following config:
   1. Enter “/demo/input” in the **Input Folder** field.
   2. Enter “/demo/completed” in the **Completed Folder** field.
   3. Enter “/demo/error” in the **Error Folder** field.
   4. Enter `<ftp_server_ip>` in the **Hostname of Ftp Server** field.
   5. Enter `<ftp_server_port>` in the **Port of Ftp Server** field.
   6. Enter `ftp_username` in the **User of Ftp Server** field.
   7. Enter `ftp_password` in the **Password of Ftp Server** field.
3. Select the **CORE** tab and choose **t1** from the **Topics** dropdown.

**Set up **dumb** stream app:**

1. Click the **dumb** graph in the pipeline graph.
2. Enter “ohara1” to **filter value**
3. Click the **CORE** tab.
4. Select **t1** from the **From topic of data consuming from** dropdown.
5. Select **t2** from the **To topic of data produce to** dropdown.

**Set up hdfssink connector:**

1. Click the **hdfssink** graph in the pipeline graph.
2. Select the **COMMON** tab and fill out the following config:
   1. Enter “/data“ in the Output Folder field
   2. Change **Flush Size** value from “1000” to “5”
   3. Check the **File Need Header** checkbox
   4. Enter “hdfs://`<hdfs_host>`:`<hdfs_port>`” in the **HDSF URL** field
3. Select the **CORE** tab and choose **t2** from the **Topics** dropdown.

## Prepare the required folders and test data on the FTP server

1. Open a terminal, login to FTP server (or use a FTP client of your choice)

```
$ ftp `ftp_server_ip`
Name: `ftp_username`
Password: `ftp_password`
```

2. Create the following folders on your FTP server

```
ftp> mkdir demo
ftp> cd demo
ftp> mkdir input
ftp> mkdir completed
ftp> mkdir error
ftp> bye
```

3. Copy the test file `demo.csv` to **demo/input** folder. See this [link](#how-to-create-democsv) to create demo CSV files

## Start all connectors and stream app

1. On the **firstpipeline** page.
2. Click the **Start pipeline** icon.

## Verify which test data was successfully dumped to HDFS

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

## How to create demo.csv?

1. Create the txt copy the date save to demo.csv

```
ID,NAME,CREATE_AT
1,ohara1,2019-03-01 00:00:01
2,ohara2,2019-03-01 00:00:02
3,ohara3,2019-03-01 00:00:03
4,ohara4,2019-03-01 00:00:04
5,ohara5,2019-03-01 00:00:05
```

## How to get ohara-it-stream.jar?

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

```java
class DumbStream extends Stream {

  override def start(ostream: OStream[Row], configs: StreamDefinitions): Unit = {

    // do nothing but only start stream and write exactly data to output topic
    ostream.start()
  }
}
```

To

```java
/*
 * Copyright 2019 is-land
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package oharastream.ohara.it.stream
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
