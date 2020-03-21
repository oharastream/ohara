..
.. Copyright 2019 is-land
..
.. Licensed under the Apache License, Version 2.0 (the "License");
.. you may not use this file except in compliance with the License.
.. You may obtain a copy of the License at
..
..     http://www.apache.org/licenses/LICENSE-2.0
..
.. Unless required by applicable law or agreed to in writing, software
.. distributed under the License is distributed on an "AS IS" BASIS,
.. WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
.. See the License for the specific language governing permissions and
.. limitations under the License.
..

.. _stream:

Custom Stream Guideline
==========================

Ohara stream is an unparalleled wrap of `kafka
streams <https://kafka.apache.org/documentation/streams>`__ which gives
you a straightforward thought to design your streaming flow. It offers a
simple way to implement and define actions to process data between
topics. You only have to write your logic in :ref:`start() <stream-start-method>`
method and compile your code to a jar file. After jar file is compiled
successfully, you can **deploy** your jar file to Ohara, run it,
and monitor your stream by :ref:`logs <stream-logs>` and
:ref:`metrics <stream-metrics>`.

The following sections will describe how to write a stream
application in Ohara.

---------------------------

Ohara Stream Overview
------------------------

Ohara stream is a wrap of `kafka
streams <https://kafka.apache.org/documentation/streams>`__ and provided
an entry of interface class :ref:`Stream <stream-entry>` to define
user custom streaming code. A normal stream application will use
:ref:`Row <connector-datamodel>` as data type to interactive
topics in Ohara.


Before writing your stream, you should download the ohara
dependencies first. Ohara includes many powerful tools for
developer but not all tools are requisite in designing stream. The
required dependencies are shown below.

.. code-block:: groovy

   repositories {
        maven {
            url "https://dl.bintray.com/oharastream/ohara"
        }
    }
   implementation "oharastream.ohara:ohara-stream:$|version|"
   implementation "oharastream.ohara:ohara-common:$|version|"
   implementation "oharastream.ohara:ohara-kafka:$|version|"

.. note::
   The `releases <https://github.com/oharastream/ohara/releases>`__ page
   shows the available version of ohara

---------------------------

.. _stream-entry:

Stream Entry
---------------

We will automatically find your custom class which should be extended by
**oharastream.ohara.stream.Stream**.

In Ohara environment, the required parameters are defined in
Ohara UI. You only need to initial the ``OStream`` as following:

.. code-block:: java

  OStream<Row> ostream = OStream.builder().toOharaEnvStream();

A base implementation for a custom stream only need to include
:ref:`start() <stream-start-method>` method, but you could include other methods
which are described below for your convenience.

The following example is a simple stream application which can run in
Ohara. Note that this example simply starts the stream application without doing any transformation but writing data,
i.e., the target topic will have same data as the source topic.

.. code-block:: java

   public class SimpleApplicationForOharaEnv extends Stream {

     @Override
     public void start(OStream<Row> ostream, StreamDefinitions streamSetting) {
       ostream.start();
     }
   }

.. note::
   The following methods we provided belongs to Ohara Stream, which has
   many powerful and friendly features. Native Kafka Streams API does
   not have these methods.

.. _stream-init-method:

init() method
~~~~~~~~~~~~~

After we find the user custom class, the first method will be called by
Stream is **init()**. This is an optional method that can be used for
user to initialize some external data source connections or input
parameters.

.. _stream-config-method:

config() method
~~~~~~~~~~~~~~~

In a stream application, you may want to configure your own parameters. We support a method here to help you define
a custom streamSetting list in stream. The details of streamSetting are list :ref:`here <stream-setting-definitions>`.

In the following example, we want to add a custom definition which is used to define "join topic":

.. code-block:: java

   @Override
   public StreamDefinitions config() {
    return StreamDefinitions
      // add a definition of "filter name" in "default" group
      .with(SettingDef.builder().key("filterName").group("default").build());
   }

After define the definition, you can use it in :ref:`start() method <stream-start-method>`

.. note::
   This method is optional. We will append all the definitions you provide in this method to the stream default
   definitions. That is, the absent config() method means you only need the default definitions.

.. _stream-start-method:

start(OStream<Row>, StreamDefinitions) method
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This method will be called after :ref:`init() <stream-init-method>`. Normally,
you could only define start() method for most cases in Ohara. We encourage
user to use **source connector** (see :ref:`connector-sourceconnector` section) for importing
external data source to Ohara and use topic data as custom
stream data source in start() method.

We provide two arguments in this method:

#. OStream - the entry class of ohara stream

   OStream (a.k.a. ohara stream) helps you to construct your application
   and use all the powerful APIs in Stream.

#. StreamDefinitions - the definitions of ohara stream

   from the definition you can use `StreamDefinitions.string()` to get the value from the :ref:`config method <stream-config-method>` .

   .. note::
      The return value is wrap in a Java object **Optional**, you need to decide whether the value is present
      or not.

For example:

.. code-block:: java

   @Override
   public void start(OStream<Row> ostream, StreamDefinitions streamSetting) {
    ostream
      .map(row -> Row.of(row.cell("name"), row.cell("age")))
      // use the previous defined definition in config()
      .filter(row -> row.cell(streamSetting.string("filterName").get()).value() != null)
      .map(row -> Row.of(Cell.of("name", row.cell("name").value().toString().toUpperCase())))
      .start();
   }

The above code does the following transformations:

#. pick cell of the header: ``name``, ``age`` from each row
#. filter out that if ``filterName`` is null

   - here we get the value from **filterName** of definitions. the value you should update by
     :ref:`Stream update api <rest-stream-update-information>`

   PUT /v0/streams/XXX

   .. code-block:: json

      {
       "filterName": "name"
      }

#. convert the cell of ``name`` to upperCase

From now on, you can use the :ref:`Stream Java API <stream-java-api>` to design your own application, happy coding!

.. _stream-java-api:

Stream Java API
------------------

In Stream, we provide three different classes for developers:

- OStream: define the functions for operating streaming data (each row record one-by-one)
- OGroupedStream: define the functions for operating grouped streaming data
- OTable: define the functions for operating table data (changelog for same key of row record)

The above classes will be auto converted when you use the correspond
functions; You should not worried about the usage of which class is
right to use. All the starting point of development is just **OStream**.

Below we list the available functions in each classes (See more information in javadoc):


OStream
~~~~~~~

- constructTable(String topicName)

    Create a OTable with specified topicName from current OStream.

- filter(Predicate predicate)

    Create a new OStream that filter by the given predicate.

-  through(String topicName, int partitions)

    Transfer this OStream to specify topic and use the required partition number.

-  leftJoin(String joinTopicName, Conditions conditions, ValueJoiner joiner)

    Join this OStream with required joinTopicName and conditions.

-  map(ValueMapper mapper)

    Transform the value of each record to a new value of the output record.

-  groupByKey(List keys)

    Group the records by key to a OGroupedStream.

-  foreach(ForeachAction action)

    Perform an action on each record of OStream.

-  start()

    Run this stream application.

-  stop()

    Stop this stream application.

-  describe()

    Describe the topology of this stream.

-  getPoneglyph()

    Get the Ohara format Poneglyph from topology.


OGroupedStream
~~~~~~~~~~~~~~

-  count()

    Count the number of records in this OGroupedStream and return the count value.

-  reduce(final Reducer reducer)

    Combine the values of each record in this OGroupedStream by the grouped key.

OTable
~~~~~~

-  toOStream()

    Convert this OTable to OStream

---------------------------


Stream Examples
------------------

Below we provide some examples that demonstrate how to develop your own
stream applications. More description of each example could be found
in javadoc.

- :ohara-source:`WordCount <ohara-stream/src/test/java/oharastream/ohara/stream/examples/WordCountExample.java>`: count the words in “word” column
- :ohara-source:`PageViewRegion <ohara-stream/src/test/java/oharastream/ohara/stream/examples/PageViewRegionExample.java>`: count the views by each region
- :ohara-source:`Sum <ohara-stream/src/test/java/oharastream/ohara/stream/examples/SumExample.java>`: sum odd numbers in “number” column

---------------------------

.. _stream-setting-definitions:

Stream Definitions
---------------------

Stream stores a list of :ref:`SettingDef <setting-definition>`, which is StreamDefinitions, in the data store.
By default, we will keep the following definitions in the "core" group and generate the definition in stream API :

#. DefaultConfigs.BROKER_DEFINITION : The broker list
#. DefaultConfigs.IMAGE_NAME_DEFINITION : The image name
#. DefaultConfigs.NAME_DEFINITION : The stream application name
#. DefaultConfigs.GROUP_DEFINITION : The stream group name
#. DefaultConfigs.FROM_TOPICS_DEFINITION : The from topic
#. DefaultConfigs.TO_TOPICS_DEFINITION : The to topic
#. DefaultConfigs.JMX_PORT_DEFINITION : The exposed jmx port
#. DefaultConfigs.NODE_NAMES_DEFINITION : The node name list
#. DefaultConfigs.VERSION_DEFINITION : The version of stream
#. DefaultConfigs.REVISION_DEFINITION : The revision of stream
#. DefaultConfigs.AUTHOR_DEFINITION : The author of stream
#. DefaultConfigs.TAGS_DEFINITION : The tags of stream

Any other definition except above list will be treated as a custom definition. You can define

.. code-block:: java

   SettingDef.builder().key(joinTopic).group("default").build()

as a definition that is listed in "default" group, or

.. code-block:: java

   SettingDef.builder().key(otherKey).group("common").build()

as a definition that is listed in the "common" group.

.. note::

   Any group category will generate a new "tab" in Ohara manager.

The value of each definition will be kept in environment of stream running container, and you should set the value by
:ref:`stream api <rest-stream-update-information>`.

---------------------------

.. _stream-metrics:

Metrics
-------

When a stream application is running, Ohara automatically
collects some metrics data from the stream in the background. The
metrics data here means :ref:`official metrics <stream-official-metrics>` which
contains :ref:`Counters <connector-counter>` for now (other
type of metrics will be introduced in the future). The metrics data
could be fetched by :ref:`Stream APIs<rest-stream>`.
Developers will be able to implement their own custom metrics in the
foreseeable future.

Ohara leverages JMX to offer the metrics data to stream. It
means that all metrics you have created are stored as Java beans and
accessible through JMX service. The stream will expose a port via
:ref:`Stream APIs<rest-stream>` for other JMX client
tool used, such as JMC, but we still encourage you to use :ref:`Stream APIs<rest-stream>`
as it offers a more readable format of metrics.

.. _stream-official-metrics:

Official Metrics
~~~~~~~~~~~~~~~~

There are two type of official metrics for stream: - consumed topic
records (counter) - produced topic records (counter)

A normal stream will connect to two topics, one is the source topic
that stream will consume from, and the other is the target topic that
stream will produce to. We use prefix words (**TOPIC_IN**, **TOPIC_OUT**)
in the response data (:ref:`Stream APIs<rest-stream>`)
in order to improve readabilities of those types. You don’t need to worry about the
implementation of these official metrics, but you can still read the
:ohara-source:`source code <ohara-stream/src/main/java/oharastream/ohara/stream/metric/MetricFactory.java>`
to see how Ohara creates official metrics.

---------------------------

.. _stream-logs:

Logs
----

Will be implemented in the near future. Also see issue: :ohara-issue:`962`
