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

.. _rest-stream:

Stream
=========

Ohara Stream is a unparalleled wrap of kafka streaming. It leverages
and enhances `Kafka Streams`_ to make
developer easily design and implement the streaming application. More
details of developing streaming application is in :ref:`custom stream guideline <stream>`.

Assume that you have completed a streaming application via ohara Java
APIs, and you have generated a jar including your streaming code. By
Ohara Restful APIs, you are enable to control, deploy, and monitor
your streaming application. As with cluster APIs, ohara leverages
docker container to host streaming application. Of course, you can
apply your favor container management tool including simple (based on ssh)
and k8s when you are starting ohara.

Before stating to use restful APIs, please ensure that all nodes have
downloaded the `Stream image`_.
The jar you uploaded to run streaming application will be included in
the image and then executes as a docker container. The `Stream image`_
is kept in each node so don’t worry about the network. We all hate
re-download everything when running services.

  .. note::
    If you implement the Ohara stream application, you must pack to jar file
    and use the File API upload jar file then setting the jarKey to create the
    Stream API.

The following information of Stream are updated by ohara.

.. _rest-stream-stored-data:

stream stored data
~~~~~~~~~~~~~~~~~~~~~

The following are common settings to a stream app.

#. name (**string**) — cluster name
#. group (**string**) — cluster group
#. jarKey (**object**) — the used jar key
#. jmxPort (**int**) — expose port for jmx
#. className (**string**) — the class to be executed. This field is optional and Configurator will pick up a class from
                            the input jar. However, it throw exception if there are many available classes in the jar file.
#. from (**array(TopicKey)**) — source topic
#. to (**array(TopicKey)**) — target topic
#. nodeNames (**array(string)**) — the nodes running the zookeeper process
#. brokerClusterKey (**object**) — the broker cluster key used for stream running

   - brokerClusterKey.group (**option(string)**) — the group of broker cluster
   - brokerClusterKey.name (**string**) — the name of broker cluster

   .. note::
     the following forms are legal as well. 1) {"name": "n"} and 2) "n". Both forms are converted to
     {"group": "default", "name": "n"}

#. tags (**object**) — the user defined parameters
#. nodeNames (**array(string)**) — node list of stream running container
#. aliveNodes (**array(string)**) — the nodes that host the running containers of stream cluster
#. state (**option(string)**) — only started/failed stream has state (DEAD if all containers are not running, else RUNNING)
#. error (**option(string)**) — the error message from a failed stream.
   If the stream is fine or un-started, you won't get this field.
#. :ref:`metrics <connector-metrics>` (**object**) — the metrics from this stream.

   - meters (**array(object)**) — the metrics in meter type

     - meters[i].value (**double**) — the number stored in meter
     - meters[i].unit (**string**) — unit for value
     - meters[i].document (**string**) — document of this meter
     - meters[i].queryTime (**long**) — the time of query metrics from remote machine
     - meters[i].startTime (**option(long)**) — the time of record generated in remote machine

#. lastModified (**long**) — last modified this jar time

.. _rest-stream-create-properties:

create properties of specific stream
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Create the properties of a stream.

*POST /v0/streams*

#. name (**string**) — new stream name. This is the object unique name ; default is random string.
#. group (**string**) — group name for current stream ; default value is "default"
#. imageName (**string**) — image name of stream used to ; default is oharastream/stream:|version|
#. nodeNames (**array(string)**) — node name list of stream used to ; default is empty
#. tags (**object**) — a key-value map of user defined data ; default is empty
#. jarKey (**object**) — the used jar key

   - group (**string**) — the group name of this jar
   - name (**string**) — the name of this jar

#. brokerClusterKey (**option(object)**) — the broker cluster used for stream running ; default we will auto fill this
   parameter for you if you don't specify it and there only exists one broker cluster.
#. jmxPort (**int**) — expose port for jmx ; default is random port
#. from (**array(TopicKey)**) — source topic ; default is empty array

   .. note::
      Currently, stream uses a "single flow" for users to define their own data flow, we need a better
      structure to support multiple topics.

      We only support one topic for current version. We will throw exception in start api if you assign
      more than 1 topic. We will support multiple topics on issue :ohara-issue:`688`

#. to (**array(TopicKey)**) — target topic ; default is empty array

   .. note::
      Currently, stream uses a "single flow" for users to define their own data flow, we need a better
      structure to support multiple topics.

      We only support one topic for current version. We will throw exception in start api if you assign
      more than 1 topic. We will support multiple topics on issue :ohara-issue:`688`

Example Request
  .. code-block:: json

    {
      "name": "streamtest1",
      "brokerClusterKey": "bk",
      "jarKey": "ohara-it-stream.jar",
      "nodeNames": ["node00"],
      "from": ["topic0"],
      "to": ["topic1"]
    }

Example Response
  Response format is as :ref:`stream stored format <rest-stream-stored-data>`.

  .. code-block:: json

    {
      "author": "root",
      "brokerClusterKey": {
        "group": "default",
        "name": "bk"
      },
      "name": "streamtest1",
      "xms": 1024,
      "routes": {},
      "lastModified": 1579145546218,
      "tags": {},
      "xmx": 1024,
      "imageName": "oharastream/stream:$|version|",
      "jarKey": {
        "group": "default",
        "name": "ohara-it-stream.jar"
      },
      "to": [
        {
          "group": "default",
          "name": "topic1"
        }
      ],
      "revision": "b303f3c2e52647ee5e79e55f9d74a5e51238a92c",
      "version": "$|version|",
      "aliveNodes": [],
      "className": "oharastream.ohara.it.stream.DumbStream",
      "from": [
        {
          "group": "default",
          "name": "topic0"
        }
      ],
      "metrics": {
        "meters": []
      },
      "jmxPort": 44914,
      "kind": "stream",
      "group": "default",
      "nodeNames": [
        "node00"
      ]
    }

  .. note::
     The stream, which is just created, does not have any metrics.


.. _rest-stream-get-information:

get information from a specific stream cluster
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

*GET /v0/streams/${name}?group=$group*

.. note::
   We will use the default value as the query parameter "?group=" if you don't specify it.

Example Response
  Response format is as :ref:`stream stored format <rest-stream-stored-data>`.

  .. code-block:: json

    {
      "author": "root",
      "brokerClusterKey": {
        "group": "default",
        "name": "bk"
      },
      "name": "streamtest1",
      "xms": 1024,
      "routes": {},
      "lastModified": 1579145546218,
      "tags": {},
      "xmx": 1024,
      "imageName": "oharastream/stream:$|version|",
      "jarKey": {
        "group": "default",
        "name": "ohara-it-stream.jar"
      },
      "to": [
        {
          "group": "default",
          "name": "topic1"
        }
      ],
      "revision": "b303f3c2e52647ee5e79e55f9d74a5e51238a92c",
      "version": "$|version|",
      "aliveNodes": [],
      "className": "oharastream.ohara.it.stream.DumbStream",
      "from": [
        {
          "group": "default",
          "name": "topic0"
        }
      ],
      "metrics": {
        "meters": []
      },
      "jmxPort": 44914,
      "kind": "stream",
      "group": "default",
      "nodeNames": [
        "node00"
      ]
    }

list information of stream cluster
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

*GET /v0/streams*

the accepted query keys are listed below.
#. author
#. group
#. name
#. lastModified
#. tags
#. state
#. aliveNodes
#. key
#. version

Example Response
  Response format is as :ref:`stream stored format <rest-stream-stored-data>`.

  .. code-block:: json

    [
      {
        "author": "root",
        "brokerClusterKey": {
          "group": "default",
          "name": "bk"
        },
        "name": "streamtest1",
        "xms": 1024,
        "routes": {},
        "lastModified": 1579145546218,
        "tags": {},
        "xmx": 1024,
        "imageName": "oharastream/stream:$|version|",
        "jarKey": {
          "group": "default",
          "name": "ohara-it-stream.jar"
        },
        "to": [
          {
            "group": "default",
            "name": "topic1"
          }
        ],
        "revision": "b303f3c2e52647ee5e79e55f9d74a5e51238a92c",
        "version": "$|version|",
        "aliveNodes": [],
        "className": "oharastream.ohara.it.stream.DumbStream",
        "from": [
          {
            "group": "default",
            "name": "topic0"
          }
        ],
        "metrics": {
          "meters": []
        },
        "jmxPort": 44914,
        "kind": "stream",
        "group": "default",
        "nodeNames": [
          "node00"
        ]
      }
    ]

.. _rest-stream-update-information:

update properties of specific stream
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Update the properties of a non-started stream.

*PUT /v0/streams/${name}?group=$group*

.. note::
   If the required stream (group, name) was not exists, we will try to use this request as
   :ref:`create stream <rest-stream-create-properties>`

#. imageName (**option(string)**) — image name of stream used to.
#. nodeNames (**option(array(string))**) — node name list of stream used to.
#. tags (**option(object)**) — a key-value map of user defined data.
#. jarKey (**option(option(object))**) — the used jar key

   - group (**option(string)**) — the group name of this jar
   - name (**option(string)**) — the name without extension of this jar

#. jmxPort (**option(int)**) — expose port for jmx.
#. from (**option(array(string))**) — source topic.

   .. note::
      Currently, stream uses a "single flow" for users to define their own data flow, we need a better
      structure to support multiple topics.

      we only support one topic for current version. We will throw exception in start api if you assign
      more than 1 topic. We will support multiple topics on issue :ohara-issue:`688`

#. to (**option(array(string))**) — target topic.

   .. note::
      Currently, stream uses a "single flow" for users to define their own data flow, we need a better
      structure to support multiple topics.

      we only support one topic for current version. We will throw exception in start api if you assign
      more than 1 topic. We will support multiple topics on issue :ohara-issue:`688`

Example Request
  .. code-block:: json

    {
      "from": ["topic2"],
      "to": ["topic3"],
      "jarKey": "ohara-it-stream.jar",
      "nodeNames": ["node01"]
    }

Example Response
  Response format is as :ref:`stream stored format <rest-stream-stored-data>`.

  .. code-block:: json

    {
      "author": "root",
      "brokerClusterKey": {
        "group": "default",
        "name": "bk"
      },
      "name": "streamtest1",
      "xms": 1024,
      "routes": {},
      "lastModified": 1579153777586,
      "tags": {},
      "xmx": 1024,
      "imageName": "oharastream/stream:$|version|",
      "jarKey": {
        "group": "default",
        "name": "ohara-it-stream.jar"
      },
      "to": [
        {
          "group": "default",
          "name": "topic3"
        }
      ],
      "revision": "b303f3c2e52647ee5e79e55f9d74a5e51238a92c",
      "version": "$|version|",
      "aliveNodes": [],
      "className": "oharastream.ohara.it.stream.DumbStream",
      "from": [
        {
          "group": "default",
          "name": "topic2"
        }
      ],
      "metrics": {
        "meters": []
      },
      "jmxPort": 44914,
      "kind": "stream",
      "group": "default",
      "nodeNames": [
        "node01"
      ]
    }

delete properties of specific stream
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Delete the properties of a non-started stream. This api only remove
the stream component which is stored in pipeline.

*DELETE /v0/streams/${name}?group=$group*

.. note::
   We will use the default value as the query parameter "?group=" if you don't specify it.

**Example Response**

  ::

     204 NoContent

  .. note::
     It is ok to delete an nonexistent properties, and the response is 204
     NoContent.


start a Stream
~~~~~~~~~~~~~~~~~

*PUT /v0/streams/${name}/start?group=$group*

.. note::
   We will use the default value as the query parameter "?group=" if you don't specify it.

Example Response
  ::

    202 Accepted

  .. note::
     You should use :ref:`get stream <rest-stream-get-information>` to fetch up-to-date status

.. _rest-stop-stream:

stop a Stream
~~~~~~~~~~~~~~~~

This action will graceful stop and remove all docker containers belong
to this stream. Note: successful stop stream will have no status.

*PUT /v0/streams/${name}/stop?group=$group[&force=true]*

Query Parameters
  #. force (**boolean**) — true if you don’t want to wait the graceful shutdown
     (it can save your time but may damage your data).

.. note::
   We will use the default value as the query parameter "?group=" if you don't specify it.

Example Response
  ::

    202 Accepted

  .. note::

     You should use :ref:`get stream <rest-stream-get-information>` to fetch up-to-date status

get topology tree graph from specific stream
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

[TODO] This is not implemented yet !

*GET /v0/streams/view/${name}*

Example Response
  #. jarInfo (**string**) — the upload jar information
  #. name (**string**) — the stream name
  #. poneglyph (**object**) — the stream topology tree graph

      - steles (**array(object)**) — the topology collection

         - steles[i].kind (**string**) — this component kind (SOURCE,
           PROCESSOR, or SINK)
         - steles[i].key (**string**) — this component kind with order
         - steles[i].name (**string**) — depend on kind, the name is

            - SOURCE — source topic name
            - PROCESSOR — the function name
            - SINK — target topic name

         - steles[i].from (**string**) — the prior component key (could be
           empty if this is the first component)
         - steles[i].to (**string**) — the posterior component key (could be
           empty if this is the final component)

  .. code-block:: json

     {
       "jarInfo": {
         "name": "stream-app",
         "group": "wk01",
         "size": 1234,
         "lastModified": 1542102595892
       },
       "name": "my-app",
       "poneglyph": {
         "steles": [
           {
             "kind": "SOURCE",
             "key" : "SOURCE-0",
             "name": "stream-in",
             "from": "",
             "to": "PROCESSOR-1"
           },
           {
             "kind": "PROCESSOR",
             "key" : "PROCESSOR-1",
             "name": "filter",
             "from": "SOURCE-0",
             "to": "PROCESSOR-2"
           },
           {
             "kind": "PROCESSOR",
             "key" : "PROCESSOR-2",
             "name": "mapvalues",
             "from": "PROCESSOR-1",
             "to": "SINK-3"
           },
           {
             "kind": "SINK",
             "key" : "SINK-3",
             "name": "stream-out",
             "from": "PROCESSOR-2",
             "to": ""
           }
         ]
       }
     }

.. _Kafka Streams: kafka streams <https://kafka.apache.org/documentation/streams
.. _Stream image: https://cloud.docker.com/u/oharastream/repository/docker/oharastream/stream