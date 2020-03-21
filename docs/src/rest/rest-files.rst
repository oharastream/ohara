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

.. _rest-files:

Files
=====

Ohara encourages user to write custom application if the official
applications can satisfy requirements for your use case. Jar APIs is a
useful entry of putting your jar on ohara and then start related
services with it. For example, :ref:`Worker APIs <rest-workers-create>`
accept a **sharedJarKeys** element which can
carry the jar name pointing to a existent jar in ohara. The worker
cluster will load all connectors of the input jar, and then you are able
to use the connectors on the worker cluster.

The File API upload jar file to use by the :ref:`Worker <rest-workers>` and :ref:`Stream <rest-stream>`.

  .. note::
    The file used by a worker or stream can't be either updated or deleted.

The properties stored by ohara are shown below.

#. name (**string**) — the file name without extension
#. group (**string**) — the group name (we use this field to separate different workspaces)
#. size (**long**) — file size
#. url (**option(string)**) — url to download this jar from Ohara Configurator. Noted not all jars are downloadable to user.
#. lastModified (**long**) — the time of uploading this file
#. tags (**object**) — the user defined parameters
#. bytes (**array(object)**) — read file content to bytes
#. classInfos (**array(object)**) — the information of available classes in this file

  - classInfos[i].className — the name of this class
  - classInfos[i].classType — the type of this class. for example, topic, source connector, sink connector or stream app
  - classInfos[i].settingDefinitions — the definitions of this class

  .. note::
    The field "classInfos" is empty if the file is NOT a valid jar.

upload a file to Ohara
----------------------

Upload a file to ohara with field name : “jar” and group name : “group”
the text field “group” could be empty and we will generate a random
string.

*POST /v0/files*

Example Request
  .. code-block:: text

     Content-Type: multipart/form-data
     file="ohara-it-stream.jar"
     group="default"
     tags={}

  .. note::
     You have to specify the file name since it is a part of metadata
     stored by ohara. Noted, the later uploaded file can overwrite the
     older one

Example Response
  .. code-block:: json

    {
      "name": "ohara-it-stream.jar",
      "size": 1896,
      "url": "http://localhost:12345/v0/downloadFiles/default/ohara-it-stream.jar",
      "lastModified": 1578967196525,
      "tags": {},
      "classInfos": [
        {
          "classType": "stream",
          "className": "oharastream.ohara.it.stream.DumbStream",
          "settingDefinitions": [
            {
              "blacklist": [],
              "reference": "BROKER_CLUSTER",
              "displayName": "Broker cluster key",
              "regex": null,
              "internal": false,
              "permission": "EDITABLE",
              "documentation": "the key of broker cluster used to transfer data for this stream",
              "necessary": "REQUIRED",
              "valueType": "OBJECT_KEY",
              "tableKeys": [],
              "orderInGroup": 0,
              "key": "brokerClusterKey",
              "defaultValue": null,
              "recommendedValues": [],
              "group": "core"
            }
          ]
        }
      ],
      "group": "default"
    }

list all jars
-------------

Get all jars from specific group of query parameter. If no query
parameter, wll return all jars.

*GET /v0/files?group=default*

Example Response
  .. code-block:: json

    [
      {
        "name": "ohara-it-stream.jar",
        "size": 1896,
        "url": "http://localhost:5000/v0/downloadFiles/default/ohara-it-stream.jar",
        "lastModified": 1578973197877,
        "tags": {},
        "classInfos": [
          {
            "classType": "stream",
            "className": "oharastream.ohara.it.stream.DumbStream",
            "settingDefinitions": [
              {
                "blacklist": [],
                "reference": "BROKER_CLUSTER",
                "displayName": "Broker cluster key",
                "regex": null,
                "internal": false,
                "permission": "EDITABLE",
                "documentation": "the key of broker cluster used to transfer data for this stream",
                "necessary": "REQUIRED",
                "valueType": "OBJECT_KEY",
                "tableKeys": [],
                "orderInGroup": 0,
                "key": "brokerClusterKey",
                "defaultValue": null,
                "recommendedValues": [],
                "group": "core"
              },
            ]
          }
        ],
        "group": "default"
      }
    ]

delete a file
-------------

Delete a file with specific name and group. Note: the query parameter
must exists.

*DELETE /v0/files/$name?group=default*

Example Response
  ::

     204 NoContent

  .. note::
     It is ok to delete an nonexistent jar, and the response is 204
     NoContent. If you delete a file is used by other services, you also
     break the scalability of service as you can’t run the jar on any new
     nodes


get a file
----------

Get a file with specific name and group. Note: the query parameter must
exists.

*GET /v0/files/$name?group=default*

Example Response
  .. code-block:: json

    {
      "name": "ohara-it-stream.jar",
      "size": 1896,
      "url": "http://localhost:5000/v0/downloadFiles/default/ohara-it-stream.jar",
      "lastModified": 1578973197877,
      "tags": {},
      "classInfos": [
        {
          "classType": "stream",
          "className": "oharastream.ohara.it.stream.DumbStream",
          "settingDefinitions": [
            {
              "blacklist": [],
              "reference": "BROKER_CLUSTER",
              "displayName": "Broker cluster key",
              "regex": null,
              "internal": false,
              "permission": "EDITABLE",
              "documentation": "the key of broker cluster used to transfer data for this stream",
              "necessary": "REQUIRED",
              "valueType": "OBJECT_KEY",
              "tableKeys": [],
              "orderInGroup": 0,
              "key": "brokerClusterKey",
              "defaultValue": null,
              "recommendedValues": [],
              "group": "core"
            }
          ]
        }
      ],
      "group": "default"
    }


update tags of file
-------------------

*PUT /v0/files/$name?group=default*

Example Response
  .. code-block:: json

     {
       "tags": {
         "a": "b"
       }
     }

  .. note::
     it returns error code if input group/name are not associated to an
     existent file.

Example Response
  .. code-block:: json

    {
      "name": "ohara-it-stream.jar",
      "size": 1896,
      "url": "http://localhost:5000/v0/downloadFiles/default/ohara-it-stream.jar",
      "lastModified": 1578974415307,
      "tags": {
        "a": "b"
      },
      "classInfos": [
        {
          "classType": "stream",
          "className": "oharastream.ohara.it.stream.DumbStream",
          "settingDefinitions": [
            {
              "blacklist": [],
              "reference": "BROKER_CLUSTER",
              "displayName": "Broker cluster key",
              "regex": null,
              "internal": false,
              "permission": "EDITABLE",
              "documentation": "the key of broker cluster used to transfer data for this stream",
              "necessary": "REQUIRED",
              "valueType": "OBJECT_KEY",
              "tableKeys": [],
              "orderInGroup": 0,
              "key": "brokerClusterKey",
              "defaultValue": null,
              "recommendedValues": [],
              "group": "core"
            }
          ]
        }
      ],
      "group": "default"
    }
