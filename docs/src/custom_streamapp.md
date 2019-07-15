# Custom StreamApp Guideline

Ohara streamApp is an unparalleled wrap of [kafka streams](https://kafka.apache.org/documentation/streams) which gives
you a straightforward thought to design your streaming flow.
It offers a simple way to implement and define actions to process data between topics.
You only have to write your logic in [start()](#start-method) method and compile your code to a jar file.
After jar file is compiled successfully, you can **deploy** your jar file to Ohara Stream, run it, and monitor your streamApp by
 [logs](#logs) and [metrics](#metrics). 

The following sections will describe how to write a streamApp application in Ohara Stream.

----------

## Quick Links

- [Overview](#ohara-streamapp-overview)
- [StreamApp Entry](#streamapp-entry)
- [StreamApp Java API](#streamapp-java-api)
- [Setting Definitions](#setting-definitions)
- [Metrics](#metrics)
- [Logs](#logs)

----------

## Ohara StreamApp Overview

Ohara streamApp is a wrap of [kafka streams](https://kafka.apache.org/documentation/streams) and provided an entry of interface
class [StreamApp](#streamapp-entry) to define user custom streaming code. A normal streamApp application will use [Row](custom_connector.html#data-model)
as data type to interactive topics in Ohara Stream.

Before writing your streamApp, you should download the ohara dependencies first. Ohara Stream includes many powerful tools
for developer but not all tools are requisite in designing streamApp. The required dependencies are shown below.
  
```groovy
repositories {
     maven {
         url "https://dl.bintray.com/oharastream/ohara"
     }
 }
implementation "com.island.ohara:ohara-streams:0.6.1-SNAPSHOT"
implementation "com.island.ohara:ohara-common:0.6.1-SNAPSHOT"
implementation "com.island.ohara:ohara-kafka:0.6.1-SNAPSHOT"
```

> The [releases](https://github.com/oharastream/ohara/releases) page shows the available version of ohara

----------

## StreamApp Entry

We will automatically find your custom class which should be extended by **om.island.ohara.streams.StreamApp**.

In Ohara Stream environment, the required parameters are defined in Ohara Stream UI. You only need to initial the
```OStream``` as following :
 ```text
OStream<Row> ostream = OStream.builder().toOharaEnvStream();
```

A base implementation for a custom streamApp only need to include [start()](#start-method) method, but you could include other methods
which are described below for your convenience.

The following example is a simple streamApp application which can run in Ohara Stream. Note that this example
simply starts the streamApp application without doing any transformation, i.e., the source topic won't write data to the 
target topic.
```java
public class SimpleApplicationForOharaEnv extends StreamApp {

  @Override
  public void start() {
    OStream<Row> ostream = OStream.builder().cleanStart().toOharaEnvStream();
    ostream.start();
  }
}
```

> The methods we provide here belong to Ohara StreamApp, which have many powerful and friendly features. 
Native Kafka Streams API does not have these methods.

### init() method
After we find the user custom class, the first method will be called by StreamApp is **init()**. This is an optional method that
can be used for user to initialize some external data source connections or input parameters.

### start() method
This method will be called after [init()](#init-method). Normally, you could only define start() method for most cases in
Ohara Stream. We encourage user to use [source connector](custom_connector.html#source-connector) for importing external
data source to Ohara Stream and use topic data as custom streamApp data source in start() method.

The only object you should remember in this method is **OStream** (a.k.a. ohara streamApp). You could use this object to
construct your application and use all the powerful APIs in StreamApp.

For example:
```text
ostream
  .map(row -> Row.of(row.cell("name"), row.cell("age")))
  .filter(row -> row.cell("name").value() != null)
  .map(row -> Row.of(Cell.of("name", row.cell("name").value().toString().toUpperCase())))
  .start();
```
The above code does the following transformations:
1. pick cell of the header: `name`, `age` from each <i>row</i>
2. filter out that if `name` is null
3. convert the cell of `name` to <b>upperCase</b>

From now on, you can use the [StreamApp Java API](#streamapp-java-api) to design your own application, happy coding!

----------

## StreamApp Java API

In StreamApp, we provide three different classes for developers:
- OStream: define the functions for operating streaming data (each row record one-by-one)
- OGroupedStream: define the functions for operating <b>grouped</b> streaming data
- OTable: define the functions for operating table data (changelog for same key of row record)

The above classes will be auto converted when you use the correspond functions; You should not worried about the usage 
of which class is right to use. All the starting point of development is just **OStream**.

Below we list the available functions in each classes (See more information in <i>javadoc</i>):

### OStream

- constructTable(String topicName)
  ```text
  Create a OTable with specified topicName from current OStream.
  ```
- filter(Predicate predicate)
  ```text
  Create a new OStream that filter by the given predicate.
  ```
- through(String topicName, int partitions)
  ```text
  Transfer this OStream to specify topic and use the required partition number.
  ```
- leftJoin(String joinTopicName, Conditions conditions, ValueJoiner joiner)
  ```text
  Join this OStream with required joinTopicName and conditions.
  ```
- map(ValueMapper mapper)
  ```text
  Transform the value of each record to a new value of the output record.
  ```
- groupByKey(List<String> keys)
  ```text
  Group the records by key to a OGroupedStream.
  ```
- foreach(ForeachAction action)
  ```text
  Perform an action on each record of OStream.
  ```
- start()
  ```text
  Run this streamApp application.
  ```
- stop()
  ```text
  Stop this streamApp application.
  ```
- describe()
  ```text
  Describe the topology of this streamApp.
  ```
- getPoneglyph()
  ```text
  Get the Ohara format Poneglyph from topology.
  ```

### OGroupedStream

- count()
  ```text
  Count the number of records in this OGroupedStream and return the count value.
  ```
- reduce(final Reducer reducer)
  ```text
  Combine the values of each record in this OGroupedStream by the grouped key.
  ```

### OTable

- toOStream()
  ```text
  Convert this OTable to OStream
  ```

----------

## StreamApp Examples

Below we provide some examples that demonstrate how to develop your own streamApp applications.
More description of each example could be found in <i>javadoc</i>.

* [WordCount](https://github.com/oharastream/ohara/blob/master/ohara-streams/src/test/java/com/island/ohara/streams/examples/WordCountExample.java): count the words in "word" column 
* [PageViewRegion](https://github.com/oharastream/ohara/blob/master/ohara-streams/src/test/java/com/island/ohara/streams/examples/PageViewRegionExample.java): count the views by each region
* [Sum](https://github.com/oharastream/ohara/blob/master/ohara-streams/src/test/java/com/island/ohara/streams/examples/SumExample.java): sum odd numbers in "number" column

----------

## Setting Definitions

Will be implemented in the near future. Also see: [issue #962](https://github.com/oharastream/ohara/issues/962)

----------

## Metrics

When a streamApp application is running, Ohara Stream automatically collects some metrics data from the streamApp in the background.
The metrics data here means [official metrics](#official-metrics) which contains [Counters](custom_connector.html#counter)
for now (other type of metrics will be introduced in the future).
The metrics data could be fetched by [StreamApp APIs](rest_interface.html#streamapp). Developers will be able to implement
their own custom metrics in the foreseeable future.

Ohara Stream leverages JMX to offer the metrics data to streamApp. It means that all metrics you have created are stored as Java beans and 
accessible through JMX service. The streamApp will expose a port via [StreamApp APIs](rest_interface.html#streamapp)
for other JMX client tool used, such as JMC, but we still encourage you to use [StreamApp APIs](rest_interface.html#streamapp)
as it offers a more readable format of metrics.

----------

### Official Metrics

There are two type of official metrics for streamApp:
- consumed topic records (counter)
- produced topic records (counter)

A normal streamApp will connect to two topics, one is the source topic that streamApp will consume from, and the other is
the target topic that streamApp will produce to. We use prefix words (**TOPIC_IN**, **TOPIC_OUT**) in the response
data ([StreamApp APIs](rest_interface.html#streamapp)) in order to improve readabilities of those types.
You don't need to worry about the implementation of these official metrics, but you can still read the [source code](https://github.com/oharastream/ohara/blob/master/ohara-streams/src/main/java/com/island/ohara/streams/metric/MetricFactory.java)
to see how Ohara Stream creates official metrics.

----------

## Logs

Will be implemented in the near future. Also see:
https://github.com/oharastream/ohara/issues/962