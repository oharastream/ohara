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

.. _rest:

Ohara REST Interface
====================


Ohara provides a bunch of REST APIs of managing data, applications and
cluster for ohara users. Both request and response must have
application/json content type, hence you should set content type to
application/json in your request.

  ::

     Content-Type: application/json

and add content type of the response via the HTTP Accept header:

  ::

     Accept: application/json


Statuses & Errors
  Ohara leverages `akka http`_ to support standards-compliant HTTP statuses.
  your clients should check the HTTP status before parsing response
  entities. The error message in response body are format to json content.

    .. code-block:: json

       {
         "code": "java.lang.IllegalArgumentException",
         "message": "Unsupported restful api:vasdasd. Or the request is invalid to the vasdasd",
         "stack": "java.lang.IllegalArgumentException: Unsupported restful api:vasdasd. Or the request is invalid to the vasdasd at"
       }

  #. code (**string**) — the type of error. It is normally a type of java
     exception
  #. message (**string**) — a brief description of error
  #. stack (**string**) — error stack captured by server


Manage clusters
  You are tired to host a bunch of clusters when you just want to build a
  pure streaming application. So do we! Ohara aims to take over the heavy
  management and simplify your life. Ohara leverage the docker technology
  to run all process in containers. If you are able to use k8s, Ohara is
  good at deploying all containers via k8s. If you are too afraid to touch
  k8s, Ohara is doable to be based on ssh connection to control all
  containers.

  Ohara automatically configure all clusters for you. Of course, you have
  the freedom to overwrite any settings. see section
  :ref:`zookeeper <rest-zookeepers>`, :ref:`broker <rest-brokers>` and
  :ref:`worker <rest-workers>` to see more details.

  In order to provide a great experience in exercising containers, ohara
  pre-builds a lot of docker images with custom scripts. Of course, Ohara
  APIs allow you to choose other image instead of ohara official images.
  However, it works only if the images you pick up are compatible to ohara
  command. see :ref:`here <docker>` for more details. Also, all official
  images are hosted by `docker hub`_


.. _akka http: https://github.com/akka/akka-http
.. _docker hub: https://cloud.docker.com/u/oharastream/repository/list



.. toctree::
   :maxdepth: 2
   :caption: REST APIs

   rest-topics
   rest-connectors
   rest-pipelines
   rest-nodes
   rest-zookeepers
   rest-brokers
   rest-workers
   rest-validation
   rest-containers
   rest-stream
   rest-files
   rest-logs
   rest-inspect
   rest-objects
