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

.. _rest-pipelines:

Pipeline
========

Pipeline APIs are born of Ohara manager which needs a way to store the
relationship of components in streaming. The relationship in pipeline is
made up of multi **endpoints**. Each **endpoint** describe a component. For example,
you have a :ref:`topic <rest-topics>` as source so you can describe the relationship via following endpoints.

  .. code-block:: json

     {
       "endpoints": [
         {
           "group": "default",
           "name": "topic0",
           "kind": "topic"
         }
       ]
     }

The objects grouped by pipeline should be existent. Otherwise, pipeline
will ignore them in generating object abstracts.

The objects grouped by pipeline don’t need to located on the same
cluster hierarchy. Grouping a topic, which is placed at broker_0, and a
topic, which is located at broker_1, is valid. However, the object based
on a dead cluster will get an abstract with error state.

The properties used in generating pipeline are shown below.

#. group (**string**) — pipeline’s group
#. name (**string**) — pipeline’s name
#. endpoints (**array(object)**) — the relationship between objects

   - endpoints[i].group (**String**) — the group of this endpoint
   - endpoints[i].name (**String**) — the name of this endpoint
   - endpoints[i].kind (**String**) — the kind of this endpoint

#. tags (**object**) — the extra description to this object

Following information are written by Ohara.

#. lastModified (**long**) — the last time to update this pipeline
#. objects (**array(object)**) — the abstract of all objects mentioned by pipeline

   - objects[i].name (**string**) — object’s name
   - objects[i].kind (**string**) — the type of this object. for instance, :ref:`topic <rest-topics>`,
     :ref:`connector <rest-connectors>`, and :ref:`stream <rest-stream>`
   - objects[i].className (**string**) — object’s implementation. Normally, it shows the full name of
     a java class
   - objects[i].state (**option(string)**) — the state of object. If the object can’t have state
     (eg, :ref:`topic <rest-topics>`), you won’t see this field
   - objects[i].error (**option(string)**) — the error message of this object
   - objects[i].lastModified (**long**) — the last time to update this object
   - :ref:`metrics <connector-metrics>` (**object**) — the metrics from this object.
     Not all objects in pipeline have metrics!
   - meters (**array(object)**) — the metrics in meter type
   - meters[i].value (**double**) — the number stored in meter
   - meters[i].unit (**string**) — unit for value
   - meters[i].document (**string**) — document of this meter
   - meters[i].queryTime (**long**) — the time of query metrics from remote machine
   - meters[i].startTime (**option(long)**) — the time of record generated in remote machine

#. jarKeys (**array(object)**) — the jars used by the objects in pipeline.

create a pipeline
-----------------

*POST /v0/pipelines*

The following example creates a pipeline with a :ref:`topic <rest-topics>` and
:ref:`connector <rest-connectors>`. The :ref:`topic <rest-topics>` is created on
:ref:`broker cluster <rest-brokers>` but the :ref:`connector <rest-connectors>` isn’t. Hence,
the response from server shows that it fails to find the status of the
:ref:`connector <rest-connectors>`. That is to say, it is ok to add un-running
:ref:`connector <rest-connectors>` to pipeline.

Allow setting the not exists object for the endpoint. The object resposne value is empty.

Example Request 1
  Running single topic example

  .. code-block:: json

     {
       "name": "pipeline0",
       "endpoints": [
         {
           "group": "default",
           "name": "topic0",
           "kind": "topic"
         }
       ]
     }

Example Response 1
  .. code-block:: json

    {
      "name": "pipeline0",
      "lastModified": 1578639344607,
      "endpoints": [
        {
          "group": "default",
          "name": "topic0",
          "kind": "topic"
        }
      ],
      "tags": {},
      "objects": [
        {
          "name": "topic0",
          "state": "RUNNING",
          "lastModified": 1578635914746,
          "tags": {},
          "metrics": {
            "meters": []
          },
          "kind": "topic",
          "group": "default"
        }
      ],
      "jarKeys": [],
      "group": "default"
    }

Example Request 2
  Running topic and perf connector example

  .. code-block:: json

    {
      "name": "pipeline1",
      "endpoints": [
        {
          "group": "default",
          "name": "topic0",
          "kind": "topic"
        },
        {
          "group": "default",
          "name": "perf",
          "kind": "connector"
        }
      ]
    }

Example Response 2
  .. code-block:: json

    {
      "name": "pipeline1",
      "lastModified": 1578649709850,
      "endpoints": [
        {
          "group": "default",
          "name": "topic0",
          "kind": "topic"
        },
        {
          "group": "default",
          "name": "perf",
          "kind": "connector"
        }
      ],
      "tags": {},
      "objects": [
        {
          "name": "topic0",
          "state": "RUNNING",
          "lastModified": 1578649564486,
          "tags": {},
          "metrics": {
            "meters": [
              {
                "name": "BytesOutPerSec",
                "document": "BytesOutPerSec",
                "unit": "bytes / SECONDS",
                "queryTime": 1578649704688,
                "value": 0.0
              }
            ]
          },
          "kind": "topic",
          "group": "default"
        },
        {
          "name": "perf",
          "state": "RUNNING",
          "lastModified": 1578649620960,
          "tags": {},
          "className": "oharastream.ohara.connector.perf.PerfSource",
          "metrics": {
            "meters": [
              {
                "name": "ignored.message.size",
                "startTime": 1578649656575,
                "document": "size of ignored messages",
                "unit": "bytes",
                "queryTime": 1578649707752,
                "value": 0.0
              }
            ]
          },
          "kind": "source",
          "group": "default"
        }
      ],
      "jarKeys": [],
      "group": "default"
    }


update a pipeline
-----------------

*PUT /v0/pipelines/$name*

Example Request
  .. code-block:: json

    {
      "endpoints": [
        {
          "group": "default",
          "name": "topic1",
          "kind": "topic"
        }
      ]
    }

  .. note::
    This API creates an new pipeline for you if the input name
    does not exist!

Example Response
  .. code-block:: json

    {
      "name": "pipeline0",
      "lastModified": 1578641282237,
      "endpoints": [
        {
          "group": "default",
          "name": "topic1",
          "kind": "topic"
        }
      ],
      "tags": {},
      "objects": [
        {
          "name": "topic1",
          "state": "RUNNING",
          "lastModified": 1578641231579,
          "tags": {},
          "metrics": {
            "meters": []
          },
          "kind": "topic",
          "group": "default"
        }
      ],
      "jarKeys": [],
      "group": "default"
    }


list all pipelines
------------------

*GET /v0/pipelines*

Listing all pipelines is a expensive operation as it invokes a iteration
to all objects stored in pipeline. The loop will do a lot of checks and
fetch status, metrics and log from backend clusters. If you have the
name of pipeline, please use :ref:`GET <rest-pipelines-get>` to fetch details
of **single** pipeline.

the accepted query keys are listed below.

#. group
#. name
#. jarKeys
#. lastModified
#. tags

Example Response
  .. code-block:: json

    [
      {
        "name": "pipeline0",
        "lastModified": 1578641282237,
        "endpoints": [
          {
            "group": "default",
            "name": "topic1",
            "kind": "topic"
          }
        ],
        "tags": {},
        "objects": [
          {
            "name": "topic1",
            "state": "RUNNING",
            "lastModified": 1578641231579,
            "tags": {},
            "metrics": {
              "meters": []
            },
            "kind": "topic",
              "group": "default"
            }
        ],
        "jarKeys": [],
        "group": "default"
      }
    ]

*GET /v0/pipelines?name=${pipelineName}*

Example Response
  .. code-block:: json

    [
      {
        "name": "pipeline0",
        "lastModified": 1578647223700,
        "endpoints": [
          {
            "group": "default",
            "name": "topic1",
            "kind": "topic"
          }
        ],
        "tags": {},
        "objects": [],
        "jarKeys": [],
        "group": "default"
      }
    ]

delete a pipeline
-----------------

*DELETE /v0/pipelines/$name*

Deleting a pipeline does not delete the objects related to the pipeline.

Example Response
  ::

     204 NoContent

  .. note::
     It is ok to delete an an nonexistent pipeline, and the response is
     204 NoContent. However, it is illegal to remove a pipeline having any
     running objects


.. _rest-pipelines-get:

get a pipeline
--------------

*GET /v0/pipelines/$name*

Example Response
  .. code-block:: json

    {
      "name": "pipeline0",
      "lastModified": 1578647223700,
      "endpoints": [
        {
          "group": "default",
          "name": "topic1",
          "kind": "topic"
        }
      ],
      "tags": {},
      "objects": [],
      "jarKeys": [],
      "group": "default"
    }

  .. note::
    The field "objects" displays only existent endpoints.

refresh a pipeline
------------------

*PUT /v0/pipelines/$name/refresh*

Requires Ohara Configurator to cleanup nonexistent objects of pipeline. Pipeline is a group of objects and it contains,
sometimes, some nonexistent objects. Those nonexistent objects won't hurt our services but it may be ugly and weird to
read. Hence, the (helper) API do a background cleanup for your pipeline. The cleanup rules are shown below.

#. the endpoint having nonexistent "from" is removed
#. the objects in "to" get removed

Example Response
  ::

    202 Accepted

  .. note::
    You should use :ref:`Get pipeline <rest-pipelines-get>` to fetch up-to-date status
