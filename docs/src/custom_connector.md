# Custom Connector Guideline

Ohara custom connector is based on [kafka connector](https://docs.confluent.io/current/connect/managing/index.html).
It offers a platform that enables you to define some simple actions to connect topic to any other system.
You don't need to worry the application availability, data durability, or distribution anymore. All you have to do is to
write your custom connector, which can have only the pull()/push() method, and then compile your code to a jar file.
After uploading your jar to ohara, you are able to **deploy** your  connector on the
[worker cluster](rest_interface.html#create-a-worker-cluster). By leveraging ohara connector framework, apart from the availability,
scalability, and durability, you can also monitor your connector for [logs](rest_interface.html#logs) and [metrics](#metrics). 

The following sections start to explain how to write a good connector on ohara. You don't need to read it through if you
are familiar with [kafka connector](https://docs.confluent.io/current/connect/managing/index.html). However, ohara connector has some
improvements which are not in [kafka connector](https://docs.confluent.io/current/connect/managing/index.html) so it has
worth of taking a look at [metrics](#metrics) and [setting definitions](#setting-definitions)

----------

## Quick Links
- [Overview](#ohara-connector-overview)
- [Source Connector](#source-connector)
- [Source Task](#source-task)
- [Sink Connector](#sink-connector)
- [Sink Task](#sink-task)
- [Version](#version)
- [Setting Definitions](#setting-definitions)
- [Metrics](#metrics)

----------

## Ohara Connector Overview

Ohara connector is composed of [source connect](#source-connector) and [sink connector](#sink-connector). 
[source connect](#source-connector) is used to pull data **from external system to topic**. By contrast, [sink connector](#sink-connector)
is used to pull data from **topic to external system**. A complete connector consists of **SourceConnector**/
**SinkConnector** and **SourceTask**/**SinkTask**. Worker cluster picks up a node to host your source/sink
connector and then distributes the source/sink tasks across cluster. 

You must include ohara jars before starting to write your custom connector. Please include both of ohara-common
and ohara-kafka in your dependencies. The ohara-common contains many helper methods and common data used in whole ohara.
The ohara-kafka offers a lot of beautiful APIs to help you to access kafka and design custom connector.
  
```groovy
repositories {
     maven {
         url "https://dl.bintray.com/oharastream/ohara"
     }
 }
implementation "com.island.ohara:ohara-common:0.6.1-SNAPSHOT"
implementation "com.island.ohara:ohara-kafka:0.6.1-SNAPSHOT"
```

> The [releases](https://github.com/oharastream/ohara/releases) page shows the available version of ohara

----------

### Data Model

Ohara has defined a table structure data in code base. We call it **row**. A row is comprised of multiple **cells**. Each cell has
its **name**, **value** and **tags**. The value in cell is a generic type which accepts any serializable type of value.
Following is an example that shows you how to convert a csv data to ohara row.

```
c0,c1,c2
v0,v1,v2
```

The above data can be converted to ohara row by following code.
```java
import com.island.ohara.common.data.Row;
import com.island.ohara.common.data.Cell;
class ExampleOfRow {
    public static void main(String[] args) {
        Row row = Row.of(
                Cell.of("c0", "v0"),
                Cell.of("c1", "v1"),
                Cell.of("c2", "v2")
                );
    }
}
```

```
c0,c1,c2
v0,,v2
```

The above data can be converted to ohara row by following code.
```java
import com.island.ohara.common.data.Row;
import com.island.ohara.common.data.Cell;
class ExampleOfRow {
    public static void main(String[] args) {
        Row row = Row.of(
                Cell.of("c0", "v0"),
                Cell.of("c2", "v2")
                );
    }
}
```

Don't worry about the serialization. Ohara offers default serializations for following data types.
1. string
1. boolean
1. short
1. int
1. long
1. float
1. double
1. bytes
1. serializable object
1. row (a nested row is acceptable!)

> The default serializer is located at com.island.ohara.common.data.Serializer.

When you get the rows in connector, you should follow the **cell setting** to generate the output.
The **cell setting** in ohara is called **column**. It shows the metadata of a **cell**. The metadata consists of
1. origin column name (**string**) — you can match the cell by this name
1. new column name — the new name of output.
1. type (**DataType**) — the type of output value. Whatever the origin type of value, you should convert the value according this type.
                        Don't worry the casting error. It is up to the user who pass the wrong configuration.
    - string
    - boolean
    - short
    - int
    - long
    - float
    - double
    - bytes
    - serializable object
    - row
1. order (**int**) — the order of cells in output.

An example of converting data according to columns.
```java
import com.island.ohara.common.data.Cell;
import com.island.ohara.common.data.Column;
class ExampleOfConverting {
    public static Object hello(Column column, String rawValue) {
        switch (column.dataType) {
            case DataType.BOOLEAN:
                return Boolean.valueOf(rawValue);
            case DataType.STRING:
                return rawValue;
            case DataType.SHORT:
                return Short.valueOf(rawValue);
            case DataType.INT:
                return Integer.valueOf(rawValue);
            case DataType.FLOAT:
                return Float.valueOf(rawValue);
            case DataType.DOUBLE:
                return Double.valueOf(rawValue);
            default:
                throw new IllegalArgumentException("unsupported type:" + column.dataType);
        }
    }
}
```

The type is a complicated issue since there are countless types in this world. It is impossible to define a general solution
to handle all types so the final types of value is **byte array** or **serializable object**. If the type you want to pass is not
in official support, you should define it as **byte array** or **serializable object** and then process it in your connectors.

> Feel free to throw an exception when your connector encounter a unknown type. Don't swallow it and convert to a weird value, such as null or empty.
Throwing exception is better than generating corrupt data! 

----------

## Source Connector

Source connector is used to pull data from outside system and then push processed data to ohara topics. A basic implementation
for a source connector only includes four methods - _start, _stop, _taskClass, and _taskSetting

```java
public abstract class RowSourceConnector extends SourceConnector {

  /**
   * Returns the RowSourceTask implementation for this Connector.
   *
   * @return a RowSourceTask class
   */
  protected abstract Class<? extends RowSourceTask> _taskClass();

  /**
   * Return the settings for source task.
   *
   * @param maxTasks number of tasks for this connector
   * @return a seq from settings
   */
  protected abstract List<TaskSetting> _taskSetting(int maxTasks);

  /**
   * Start this Connector. This method will only be called on a clean Connector, i.e. it has either
   * just been instantiated and initialized or _stop() has been invoked.
   *
   * @param taskSetting configuration settings
   */
  protected abstract void _start(TaskSetting taskSetting);

  /** stop this connector */
  protected abstract void _stop();
}
```

> The methods having prefix "_" belong to ohara connector. Ohara connector is based on kafka connector. Ohara take
  control on all kafka APIs in order to supply more powerful and friendly APIs to ohara user. In order to distinguish the 
  APIs between ohara and kafka, we add prefix _ to all ohara methods and make them be abstract.  

----------

### _start(TaskSetting)
After instantizing a connector, the first method called by worker is **start()**. You should initialize your connector
in **start** method, since it has a input parameter **TaskSetting** carrying all settings, such as target topics,
connector name and user-defined configs, from user. If you (connector developer) are a good friend of your connector user,
you can get (and cast it to expected type) config, which is passed by connector user, from **TaskSetting**. For example,
a connector user calls [Connector API](rest_interface.html#create-the-settings-of-connector) to store a config k0-v0 
(both of them are string type) for your connector, and then you can get v0 via TaskSetting.stringValue("k0").

> Don't be afraid of throwing exception when you notice that input parameters are incorrect. Throwing an exception can fail
  a connector quickly and stop worker to distribute connector task across cluster. It saves the time and resources. 

We all hate wrong configs, right? When you design the connector, you can **define** the [settings](#setting-definitions) on your
own initiative. The [settings](#setting-definitions) enable worker to check the input configs before starting connector.
It can't eliminate incorrect configs completely, but it save your time of fighting against wrong configs (have a great time with your family) 

----------

### _stop()

This method is invoked by calling [STOP API](rest_interface.html#stop-a-connector). You can release the resources allocated by
connector, or send a email to shout at someone. It is ok to throw an exception when you fails to **stop** the connector.
Worker cluster will mark **failure** on the connector, and the world keeps running.

----------

### _taskClass()

This method returns the java class of [RowSourceTask](#source-task) implementation. It tells worker cluster which class
should be created to pull data from external system. Noted that connector and task may not be created on same node (jvm)
so you should NOT share any objects between them (for example, make them to access a global variable).

----------

### _taskSetting(int maxTasks)

Connector has to generate configs for each task. The value of **maxTasks** is configured by [Connector API](rest_interface.html#connector).
If you prefer to make all tasks do identical job, you can just clone the task config passe by [start](#_starttasksetting).
Or you can prepare different configs for each task. Noted that the number of configuration you return MUST be equal with
input value - maxTasks. Otherwise, you will get a exception when running your connector.

> It would be better to do the final check to input configs in Connector rather than Task. Producing a failure quickly
  save your time and resources.

----------

## Source Task

```java
public abstract class RowSourceTask extends SourceTask {

  /**
   * Start the Task. This should handle any configuration parsing and one-time setup from the task.
   *
   * @param config initial configuration
   */
  protected abstract void _start(TaskSetting config);

  /**
   * Signal this SourceTask to stop. In SourceTasks, this method only needs to signal to the task
   * that it should stop trying to poll for new data and interrupt any outstanding poll() requests.
   * It is not required that the task has fully stopped. Note that this method necessarily may be
   * invoked from a different thread than _poll() and _commit()
   */
  protected abstract void _stop();
  /**
   * Poll this SourceTask for new records. This method should block if no data is currently
   * available.
   *
   * @return a array from RowSourceRecord
   */
  protected abstract List<RowSourceRecord> _poll();
}  
```

RowSourceTask is the unit of executing **poll**. A connector can invokes multiple tasks if you set **tasks.max** be bigger
than 1 via [Connector APIs](rest_interface.html#connector). RowSourceTask has similar lifecycle to Source connector. Worker
cluster call **start** to initialize a task and call **stop** to terminate a task.

----------

### _pull()
You can ignore all methods except for **_poll**. Worker cluster call **_poll** regularly to get **RowSourceRecord**s and then
save them to topics. Worker cluster does not care for your implementation. All you have to do is to put your data in ***RowSourceRecord**.
RowSourceRecord is a complicated object having many elements. Some elements are significant. For example, **partition**
can impact the distribution of records. In order to be the best friend of programmer, ohara follows fluent pattern to allow
you to create record through builder, and you can only fill the required elements.
```java
public class ExampleOfRowSourceRecord {
    public static RowSourceRecord create(Row row, String topicName) {
        return RowSourceRecord.builder()
        .row(row)
        .topicName(topicName)
        .build();
    }
}
```

> You can read the java docs of RowSourceRecord.Builder to see which default values are set for other (optional) elements.

----------

### Partition and Offsets in Source

De-duplicating data is not a easy job. When you keep pulling data from external system to topics, you always need a place
to record which data have not processed. Connector offers two specific objects for you to record the **offset** and **partition**
of your data. You can define a **partition** and a **offset** for RowSourceRecord. The durability is on Worker's shoulder,
and you are always doable to get **partition** and **offset** back even if the connector fail or shutdown.
```java
public class ExampleOfRowSourceContext {
    public static Map<String, ?> getOffset(Map<String, ?> partition) {
        return RowSourceContext.offset(partition);
    }
}
```

Both of them are Map type with string key and primitive type. Using Map is a workaround to record the offsets for
different connectors. You can view them as a **flatten** json representation. For example, one of task is handling file_a,
and it has processed first line of file_a. Then the pair of **partition** and **offset** look like
```json
{
  "fileName": "file_a"
}
```
```json
{
  "offset": 1
}
```

We can convert above json to **partition** and **offset** and then put them in **RowSourceRecord**.

```java
public class ExampleOfPartitionAndOffset {
    public static RowSourceRecord addPartitionAndOffset(RowSourceRecord.Builder builder, String fileName, int offset) {
        Map<String, String> partition = Collections.singletonMap("fileName", fileName);
        Map<String, Integer> offset = Collections.singletonMap("offset", 1);
        return builder.sourcePartition(partition)
        .sourceOffset(offset)
        .build();
    }
}
```

A news of **partition** and **offset** is that they are not stored with data in RowSourceRecord. If you want to know the
commit of **partition** and **offset**, you can override the **_commit()**.
```java
public abstract class RowSourceTask extends SourceTask {
  /**
   * Commit the offsets, up to the offsets that have been returned by _poll(). This method should
   * block until the commit is complete.
   *
   * <p>SourceTasks are not required to implement this functionality; Kafka Connect will record
   * offsets automatically. This hook is provided for systems that also need to store offsets
   * internally in their own system.
   */
  protected void _commit() {
    // do nothing
  }
}
```

----------

### Handle Exception in _poll()
Throwing exception make connector in **failure** state, and inactivate connector until you restart it. Hence, you SHOULD
catch and handle the exception as best you can. However, swallowing all exception is also a weired behavior. You SHOULD
fails the connector when encountering unrecoverable exception.

----------

### Blocking Action Is Unwelcome In _poll()
Task is executed on a separate thread and there are many remaining processing for data after _poll(). Hence, you should 
NOT block _poll(). On the contrary, returning an empty list can yield the resource to remaining processing.

> Returning null results in same result. However, we all should hate null so please take away null from your code.

----------

### Data From _poll() Are Committed Async
You don't expect that the data you generated are commit at once, right? Committing data invokes a large latency since
we need to sync data to multiple nodes and result in many disk I/O. Worker has another thread sending your data in background.
If your connector needs to know the time of committing data, you can override the **_commitRecord(RowSourceRecord)**.

```java
public abstract class RowSourceTask extends SourceTask {
  /**
   * Commit an individual RowSourceRecord when the callback from the producer client is received, or
   * if a record is filtered by a transformation. SourceTasks are not required to implement this
   * functionality; Kafka Connect will record offsets automatically. This hook is provided for
   * systems that also need to store offsets internally in their own system.
   *
   * @param record RowSourceRecord that was successfully sent via the producer.
   */
  protected void _commitRecord(RowSourceRecord record) {
    // do nothing
  }
}
```

----------

## Sink Connector

```java
public abstract class RowSinkConnector extends SinkConnector {

  /**
   * Start this Connector. This method will only be called on a clean Connector, i.e. it has either
   * just been instantiated and initialized or _stop() has been invoked.
   *
   * @param config configuration settings
   */
  protected abstract void _start(TaskSetting config);

  /** stop this connector */
  protected abstract void _stop();

  /**
   * Returns the RowSinkTask implementation for this Connector.
   *
   * @return a RowSinkTask class
   */
  protected abstract Class<? extends RowSinkTask> _taskClass();

  /**
   * Return the settings for source task. NOTED: It is illegal to assign different topics to
   * RowSinkTask
   *
   * @param maxTasks number of tasks for this connector
   * @return the settings for each tasks
   */
  protected abstract List<TaskSetting> _taskSetting(int maxTasks);
}
```

Sink connector is similar to [source connector](#source-connector). It also have [_start(TaskSetting)](#_starttasksetting),
[_stop()](#_stop), [_taskClass()](#_taskclass), [_taskSetting(int maxTasks)](#_tasksettingint-maxtasks),
[partition and offsets](#partition-and-offsets-in-source). The main difference between sink connector and source connector is that
sink connector do pull data from topic and then push processed data to outside system. Hence, it does have
[_put](#_putlistrowsinkrecord-records) rather than [_pull](#_pull)

> Though sink connector and source connector have many identical methods, you should NOT make a connector mixed sink and source.
  Because Both connector are **abstract** class, you can't have a class extending both of them in java.

Sink connector also has to provide the task class to worker cluster. The sink task in ohara is called **RowSinkTask**.
It is also distributed across whole worker cluster when you running a sink connector. 

----------

## Sink Task

```java
public abstract class RowSinkTask extends SinkTask {

  /**
   * Start the Task. This should handle any configuration parsing and one-time setup from the task.
   *
   * @param config initial configuration
   */
  protected abstract void _start(TaskSetting config);

  /**
   * Perform any cleanup to stop this task. In SinkTasks, this method is invoked only once
   * outstanding calls to other methods have completed (e.g., _put() has returned) and a final
   * flush() and offset commit has completed. Implementations from this method should only need to
   * perform final cleanup operations, such as closing network connections to the sink system.
   */
  protected abstract void _stop();

  /**
   * Put the table record in the sink. Usually this should send the records to the sink
   * asynchronously and immediately return.
   *
   * @param records table record
   */
  protected abstract void _put(List<RowSinkRecord> records);
}  
```

RowSinkTask is similar to [RowSourceTask](#source-task) that both of them have _start and _stop phase. RowSinkTask is
executed by a separate thread on worker also.

----------

### _put(List<RowSinkRecord> records)

Worker invokes a separate thread to fetch data from topic and put the data to sink task. The input data is called **RowSinkRecord**
which carries not only row but also metadata.
1. topicName (**string**) — where the dat come from
1. Row (**row**) — input data
1. partition (**int**) — index of partition
1. offset (**long**) — offset in topic-partition;
1. timestamp (**long**) — data timestamp
1. TimestampType (**enum**) — the way of generating timestamp
    - NO_TIMESTAMP_TYPE — means timestamp is nothing for this data
    - CREATE_TIME — the timestamp is provided by user or the time of sending this data 
    - LOG_APPEND_TIME — the timestamp is broker's local time when the data is append

----------

### Partition and Offsets In Sink

Sink task has a component, which is called **RowSinkContext**, saving the offset and partitions for input data. Commonly,
it is not big news to you since kafka has responsibility to manage data offset in topic-partition to avoid losing data.
However, if you have something more than data lost, such as exactly once, you can manage the data offset manually and then
use RowSinkContext to change the offset of input data.


----------

### Handle Exception In _put(List<RowSinkRecord>)
Any thrown exception will make this connector failed and stopped. You should handle the recoverable error and throw
the exception which obstruct connector from running. 

```java
public interface RowSinkContext {
  /**
   * Reset the consumer offsets for the given topic partitions. SinkTasks should use this if they
   * manage offsets in the sink data store rather than using Kafka consumer offsets. For example, an
   * HDFS connector might record offsets in HDFS to provide exactly once delivery. When the SinkTask
   * is started or a rebalance occurs, the task would reload offsets from HDFS and use this method
   * to reset the consumer to those offsets.
   *
   * <p>SinkTasks that do not manage their own offsets do not need to use this method.
   *
   * @param offsets map from offsets for topic partitions
   */
  void offset(Map<TopicPartition, Long> offsets);

  /**
   * Reset the consumer offsets for the given topic partition. SinkTasks should use if they manage
   * offsets in the sink data store rather than using Kafka consumer offsets. For example, an HDFS
   * connector might record offsets in HDFS to provide exactly once delivery. When the topic
   * partition is recovered the task would reload offsets from HDFS and use this method to reset the
   * consumer to the offset.
   *
   * <p>SinkTasks that do not manage their own offsets do not need to use this method.
   *
   * @param partition the topic partition to reset offset.
   * @param offset the offset to reset to.
   */
  default void offset(TopicPartition partition, Long offset) {
    this.offset(Collections.singletonMap(partition, offset));
  }
}
```

> Noted that data offset is a order in topic-partition so the input of RowSinkContext.offset consists of topic name
  and partition. 

----------

### Handle Exception In _put(List<RowSinkRecord>)
see [handle exception in _poll()](#handle-exception-in-poll)

----------

### Commit Your Output Data When Kafka Commit Input Data

While feeding data into your sink task, kakfa also tries to commit previous data that make the data disappear from you.
The method **_preCommit** is a callback of committing data offset. If you want to manage the offsets, you can change what
to commit by kafka. Another use case is that you have some stuff which needs to be committed also, and you can trigger
the commit in this callback.

```java
public abstract class RowSinkTask extends SinkTask {
  /**
   * Pre-commit hook invoked prior to an offset commit.
   *
   * <p>The default implementation simply return the offsets and is thus able to assume all offsets
   * are safe to commit.
   *
   * @param offsets the current offset state as from the last call to _put, provided for convenience
   *     but could also be determined by tracking all offsets included in the RowSourceRecord's
   *     passed to _put.
   * @return an empty map if Connect-managed offset commit is not desired, otherwise a map from
   *     offsets by topic-partition that are safe to commit.
   */
  protected Map<TopicPartition, TopicOffset> _preCommit(Map<TopicPartition, TopicOffset> offsets) {
    return offsets;
  }
}  
```

> The offsets exceeding the latest consumed offset are discarded

----------

## Version

We all love to show how good we are. If you are a connector designer, ohara connector offers a way to show the verion,
revision and author for a connector.

```java
public abstract class RowSourceConnector extends SourceConnector {
  /**
   * Get the version from this connector.
   *
   * @return the version, formatted as a String
   */
  protected ConnectorVersion _version() {
    return ConnectorVersion.builder().build();
  }
}
```

By default, all information in ConnectorVersion are **unknown**. You can override one of them or all of them when writing 
connector. The version information of a connector is showed by [Worker API](rest_interface.html#worker).

> Don't return null, please!!!

You can build a ConnectorVersion with fluent pattern.
```java
public class ExampleOfConnectorVersion {
  public static ConnectorVersion build() {
    return ConnectorVersion.builder()
              .version("my_version")
              .revision("my_revision")
              .author("my_user")
              .build();
  }
}
```

All official connectors have identical ConnectorVersion consisting of ohara's version, sha of commit and name of release manager.
Feel free to fill what you want in ConnectorVersion. For example, it is ok to leave **unknown** in **Author** if you 
are the people that we can't talk. However, Please don't use illegal values like **null** or **empty string**.

> Version in ohara connector is different to kafka connector. The later only supports **version** and it's APIs show only
  **version**. Hence, you can't get revision, author or other [settings](#setting-definitions) through kafka APIs 

----------

## Setting Definitions

A powerful connector always has a complicated configuration. In order to be a best friend of connector users, ohara connector
has a method which can return the details of setting definitions, and ohara suggests that all connector developers ought to
implement the method so as to guide users through the complicated settings of your connectors.

> If you have no interest in settings or your connector is too simple to have any settings, you can just skip this section.

SettingDefinition is a class used to describe the details of **a** setting. It consists of following arguments.
1. displayName (**string**) — the readable name of this setting
1. group (**string**) — the group of this setting (all core setting are in core group)
1. orderInGroup (**int**) — the order in group
1. editable (**boolean**) — true if this setting is modifiable 
1. key (**string**) — the key of configuration
1. [valueType](rest_interface.html#setting-type) (**string**) — the type of value
1. defaultValue (**string**) — the default value
1. documentation (**string**) — the explanation of this definition
1. [reference](rest_interface.html#setting-reference) (**string**) — works for ohara manager. It represents the reference of value.
1. required(**boolean**) — true if this setting has no default value and you have to assign a value. Otherwise, you can’t start connector.
1. internal (**string**) — true if this setting is assigned by system automatically.
1. tableKeys (**array(string)**) — the column name when the type is TABLE

> You can call [Worker APIs](rest_interface.html#worker) to get all connectors' setting definitions

Although a SettingDefinition can include many elements, you can simply build a SettingDefinition with only what you need.
An extreme example is that you can create a SettingDefinition with only key.
```java
public class ExampleOfSettingDefinition {
  public static SettingDefinition create(String key) {
    return SettingDefinition.builder()
            .key(key)
            .build();
  }
}
```

Notwithstanding it is flexible to build a SettingDefinition, we encourage connector developers to create a description-rich
SettingDefinition. More description to your setting produces more **document** in calling [Worker APIs](rest_interface.html#worker).
We all hate write documentation so it would be better to make your code readable.

----------

### Reference, Internal and TableKeys Are NOT Public

Ohara offers a great UI, which is located at ohara-manager. The UI requires some **private** information to generate forms
for custom connectors. The such private information is specific-purpose and is meaningless to non-ohara developers.
Hence, all of them are declared as package-private and ohara does not encourage custom connector developers to stop at nothing
to use them.

----------

### Optional, Required And Default Value

We know a great connector must have countless settings and only The Chosen One can control it. In order to shorten the gap
between your connectors and human begin, ohara encourage custom connector developers to offer the default values to most of
settings as much as possible. Assigning a default value to a SettingDefinition is a piece of cake.
```java
public class ExampleOfAssigningDefaultValueToSettingDefinition {
  public static SettingDefinition create(String key, String defaultValue) {
    return SettingDefinition.builder()
            .key(key)
            .optional(defaultValue)
            .build();
  }
}
```

> the default value is declared as **string** type as it must be **readable** in Restful APIs.

After calling the **optional(String)** method, the response, which is created by [Worker APIs](rest_interface.html#worker),
will display the following information.
```json
{
  "required": false,
  "defaultValue": "ur_default_value"
}
```

> The default value will be added to [TaskSetting](#_starttasksetting) automatically if the specified key
  is not already associated with a value.

----------

### A Readonly Setting Definition

You can declare a **readonly** setting that not only exposes something of your connector to user but also remind user
the setting can't be changed at runtime. For instance, the information of [version](#version) is fixed after you have
completed your connector so it is not a **editable** setting. Hence, ohara define a setting for **version** with a
readonly label. By the way, you should assign a default value to a readonly setting since a readonly setting
without default value is really weird. There is a example of creating a readonly setting.

```java
public class ExampleOfCreatingReadonlySettingDefinition {
  public static SettingDefinition createReadonly(String key, String defaultValue) {
    return SettingDefinition.builder()
            .key(key)
            .optional(defaultValue)
            .readonly()
            .build();
  }
}
```

> The input value will be removed automatically if the associated setting is declared readonly.

----------

### Type.Table

Table type enable you to define a setting having table structure value. Apart from assigning Type.Table to your setting
definition, you also have to define which keys are in your table. The following example show a case that declares a table having two
columns called **c0** and **c1**. 

```java
public class ExampleOfCreatingSettingDefinitionWithTableKeys {
  public static SettingDefinition create(String key, String defaultValue) {
    return SettingDefinition.builder()
            .key(key)
            .tableKeys(Arrays.asList("c0", "c1"))
            .valueType(Type.TABLE)
            .build();
  }
}
```

The legal value for above setting definition is shown below.

```json
{
  "key": [
    {
      "c0": "v0",
      "c1": "v1"
    },
    {
      "c0": "v2",
      "c1": "v3"
    }
  ]
}
```

The above example implies there is a table having two columns called **c0** and **c1**. Also, you assign two values to
**c0** that first is **v0** and another is **v2**. Ohara offers a check for Type.Table that the input value **must**
match all keys in 

> If you ignore the table keys for Type.Table, the check to your input value is also ignored. By contrast, the table keys
  are useless for other types.

----------

### Type.Duration

The time-based amount of time is a common setting in our world. However, it is also hard to reach the consensus about
the **string representation** for a duration. For instance, the java.time.Duration prefers ISO-8601, such as PT10S.
The scala.concurrent.duration.Duration prefers simple format, such as 10 seconds. Ohara offers a official support to
Duration type so as to ease the pain of using string in connector. When you declare a setting with duration type,
ohara provides the default check which casts input value to java Duration and scala Duration. Also, your connector
can get the **Duration** from [TaskSetting](#_starttasksetting) easily without worrying about the conversion between
java and scala. Furthermore, connector users can input both java.Duration and scala.Duration when starting connector.

----------

### Type.JDBC_TABLE

JDBC_TABLE is a specific string type used to reminder Ohara Manager that this field requires a **magic** button to
show available tables of remote database via Query APIs. Except for the **magic** in UI, there is no other stuff for
this JDBC_TYPE since kafka can't verify the input arguments according to other arguments. It means we can't connect to
remote database to check the existence of input table.

It is ok to replace this field by Type.String if you don't use Ohara Manager. Nevertheless, we still encourage connector
developer to choose the **fitting** type for your setting if you demand your user to input a database table.

----------

### Checker

We all love quick failure, right? A quick failure can save our resource and time. Ohara offers many checks for your setting
according to the **expected** type. For example, a setting declared **Duration** type has a checker which validate
whether the input value is able to be cast to either java.time.Duration or scala.duration.Duration. However, you are
going to design a complicated connector which has specific limit for input value. Or you don't like the default
checkers supplied by ohara. Ohara allows and encourages connector developer to provide custom checker.
The Checker is a functional interface.

```java
@FunctionalInterface
interface Checker {
  /** @param value value of input */
  void check(Object value);
}
```

All you have to do is to check the input value. Feel free to throw exception when you hate input value or type.

----------

## Metrics

We are live in a world filled with number, and so do connectors. While a connector is running, ohara collects many counts
from the data flow for the connector in background. All of counters (and other records which will be introduced in the future)
are called **metrics**, and it can be fetched by [Connector APIs](rest_interface.html#connector).
Apart from official metrics, connector developers are also able to build custom metrics for custom connectors,
and all custom metrics are also showed by [Connector APIs](rest_interface.html#connector).

Ohara leverage JMX to offer the metrics APIs to connector. It means all metrics you created are stored as Java beans and 
is accessible through JMX service. That is why you have to define a port via [Worker APIs](rest_interface.html#worker)
for creating a worker cluster. Although you can see all java mbeans via the JMX client (such as JMC), ohara still encourage
you to apply [Connector APIs](rest_interface.html#connector) as it offers a more readable format of metrics.

----------

### Counter

Counter is a common use case for metrics that you can increment/decrement/add/ a number atomically. A counter consists of
following members.
1. group (**string**) — the group of this counter
1. name (**string**) — the name of this counter
1. unit (**string**) — the unit of value
1. document (**string**) — the document for this metrics
1. startTime (**long**) — the time to start this counter
1. value (**long**) — current value of count

A example of creating a counter is shown below.
```java
public class ExampleOfCreatingCounter {
  public static Counter sizeCounter(String group) {
    return Counter.builder()
        .group(group)
        .name("row.size")
        .unit("bytes")
        .document("size (in bytes) of rows")
        .startTime(CommonUtils.current())
        .value(0)
        .register();
  }
}
```

> Though **unit** and **document* are declared optional, making them have meaning description can help reader to understand
  the magic number from your counter.

> The counter created by connector always has the group same to id of connector, since ohara needs to find the counters
  for specific connector in [Connector APIs](rest_interface.html#connector)

----------

### Official Metrics

There are two official metrics for connector - row counter and bytes counter. The former is the number of processed rows,
and the later is the number of processed data. Both of them are updated when data are pull/push from/to your connector.
Normally, you don't need to care for them when designing connectors. However, you can read the source code in ConnectorUtils.java
to see how ohara create official counters.

----------

### Create Your Own Counters

In order to reduce your duplicate code, ohara offers the **CounterBuilder** to all connectors. CounterBuilder is a wrap
of Counter.Builder with some pre-defined variables, and hence the creation of CounterBuilder must be after initializing
the connector/task.
 ```java
 public class ExampleOfCreatingCustomBuilder {
   public static Counter custom(RowSinkTask task) {
     return task.counterBuilder()
         .unit("bytes")
         .document("size (in bytes) of rows")
         .startTime(CommonUtils.current())
         .value(0)
         .register();
   }
 }
 ```
 
> Ohara doesn't obstruct you from using Counter directly. However, using CounterBuilder make sure that your custom metrics
  are available in [Connector APIs](rest_interface.html#connector).