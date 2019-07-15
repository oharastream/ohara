# Ohara REST Interface

ohara provides a bunch of REST APIs of managing data, applications and cluster for ohara users.
Both request and response must have application/json content type, hence you should set content type to application/json in your request.
    
    Content-Type: application/json
    
and add content type of the response via the HTTP Accept header:

    Accept: application/json
    
----------

## Quick links to APIs
- [Version](#version)
- [Topic](#topic)
- [FTP Information](#ftp-connection-information)
- [HDFS Information](#hdfs-connection-information)
- [JDBC Information](#jdbc-connection-information)
- [Connector](#connector)
- [Pipeline](#pipeline)
- [Node](#node)
- [Zookeeper](#zookeeper)
- [Broker](#broker)
- [Worker](#worker)
- [Validation](#validation)
- [Container](#container)
- [StreamApp](#streamapp)
- [Jars](#jars)
- [Logs](#logs)
- [Query](#query)
- [Info](#info)

----------
## object id
Most storable objects in ohara is assigned with a unique id. Most access to objects require the id to indicate which
object you want to request. You don't need to worry about how to generate the unique id but you need to **remember**
the id in order to operate the object. Or you can request **list** to see all objects and then fetch the id to to other
operation like delete and update.

----------
## Statuses & Errors

ohara leverages akka-http to support standards-compliant HTTP statuses. your clients should check the
HTTP status before parsing response entities. The error message in response body are format to json content.

```json
{
  "code": "java.lang.IllegalArgumentException",
  "message": "Unsupported restful api:vasdasd. Or the request is invalid to the vasdasd",
  "stack": "java.lang.IllegalArgumentException: Unsupported restful api:vasdasd. Or the request is invalid to the vasdasd at"
}
```
    
1. code (**string**) — the type of error. It is normally a type of java exception
1. message (**string**) — a brief description of error
1. stack (**string**) — error stack captured by server
----------
## Manage clusters

You are tired to host a bunch of clusters when you just want to build a pure streaming application. So do we!
Ohara aims to take over the heavy management and simplify your life. Ohara leverage the docker technology to run all process
in containers. If you are able to use k8s, ohara is good at deploying all containers via k8s. If you are too afraid to touch k8s,
Ohara is doable to be based on ssh connection to control all containers.

Ohara automatically configure all clusters for you. Of course, you have the freedom to overwrite any settings.
see section [zookeeper](#zookeeper), [broker](#broker) and [worker](#worker) to see more details.

In order to provide a great experience in exercising containers, ohara pre-builds a lot of docker images with custom
scripts. Of course, Ohara APIs allow you to choose other image instead of ohara official images. However, it works only if
the images you pick up are compatible to ohara command. see [here](docker.md) for more details.
Also, all official images are hosted by [docker hub](https://cloud.docker.com/u/oharastream/repository/list)

----------
## Version

We all love to see the version of software, right? Ohara provide a API to display the details of version. It includes
following information.

1. version (**string**) — version of configurator
1. user(**string**) — builder of configurator
1. revision(**string**) — latest commit of configurator
1. date(**string**) — build date of configurator

----------
### get the version of ohara

*GET /v0/info*

**Example Response**

```json
{
  "versionInfo": {
    "version": "0.3-SNAPSHOT",
    "user": "Chia-Ping Tsai",
    "revision": "9af9578041f069a9a452c7fda5f7ed7217c0deea",
    "date": "2019-03-21 17:55:06"
  }
}
```

----------

## Topic

Ohara topic is based on kafka topic. It means the creation of topic on ohara will invoke a creation of kafka also.
Also, the delete to ohara topic also invoke a delete request to kafka. The common properties in topic are shown below.

1. name (**string**) — topic name
1. brokerClusterName (**string**) — the broker cluster hosting this topic
1. numberOfReplications (**int**) — the number of replications for this topic
1. numberOfPartitions (**int**) — the number of partitions for this topic
1. lastModified (**long**) — the last time to update this topic

> The name must be unique in a broker cluster.

----------

### create a topic

*POST /v0/topics*

1. name (**string**) — topic name
1. brokerClusterName (optional **string**) — the broker cluster hosting this topic
(**If you don't specify the broker cluster in request, ohara will try to find a broker cluster for you.
And it works only if there is only a broker cluster exists in ohara**)
1. numberOfReplications (optional **int**) — the number of replications for this topic
(**it is illegal to input the number of replications which is larger than the number of broker nodes**)
1. numberOfPartitions (optional **int**)— the number of partitions for this topic

**Example Request**

```json
{
  "name": "topic0",
  "numberOfReplications": 1,
  "numberOfPartitions": 1
}
```

> the name you pass to ohara is used to build topic on kafka, and it is restricted by kafka ([a-zA-Z0-9\\._\\-])

**Example Response**

```json
{
  "name": "topic0",
  "brokerClusterName": "preCreatedBkCluster",
  "lastModified": 1553498552595,
  "numberOfReplications": 1,
  "numberOfPartitions": 1,
  "metrics": {
    "meters": []
  }
}
```

> The topic, which is just created, does not have any metrics. 

----------

### update a topic

*PUT /v0/topics/${name}*

1. numberOfPartitions (**int**) — the number of partitions for this topic
(**it is illegal to decrease the number**)

**Example Request**

```json
{
  "numberOfPartitions": 3
}
```

> You wil get an exception if you try to change the unmodifiable attributes!!!

**Example Response**

```json
{
  "name": "topic0",
  "brokerClusterName": "preCreatedBkCluster",
  "lastModified": 1553498552595,
  "numberOfReplications": 1,
  "numberOfPartitions": 3,
  "metrics": {
   "meters": []
  }
}
```

----------

### list all topics

*GET /v0/topics*

**Example Response**

```json
[
  {
    "name": "topic0",
    "brokerClusterName": "preCreatedBkCluster",
    "lastModified": 1553498552595,
    "numberOfReplications": 1,
    "numberOfPartitions": 1,
    "metrics": {
     "meters": []
    }
  },
  {
    "name": "wk00",
    "brokerClusterName": "preCreatedBkCluster",
    "lastModified": 1553498375573,
    "numberOfReplications": 1,
    "numberOfPartitions": 1,
    "metrics": {
     "meters": []
    }
  }
]
```

----------

### delete a topic

*DELETE /v0/topics/${name}*

**Example Response**

```
204 NoContent
```

> It is ok to delete an jar from an nonexistent topic, and the response is 204 NoContent.

----------

### get a topic

*GET /v0/topics/${name}*

**Example Response**

```json
{
  "name": "topic0",
  "brokerClusterName": "preCreatedBkCluster",
  "lastModified": 1553498552595,
  "numberOfReplications": 1,
  "numberOfPartitions": 1,
  "metrics": {
   "meters": []
  }
}
```
----------
## FTP Connection Information

You can store the ftp information in ohara if the data is used frequently. Currently, all data are stored by text. The
storable information is shown below.

1. name (**string**) — name of this ftp information
1. hostname (**string**) — ftp server hostname
1. port (**int**) — ftp server port
1. user (**string**) — account of ftp server
1. password (**string**) — password of ftp server

The following information are tagged by ohara.

1. lastModified (**long**) — the last time to update this ftp information
  
----------

### store a ftp information

*POST /v0/ftp*

1. name (**string**) — name of this ftp information
1. hostname (**string**) — ftp server hostname
1. port (**int**) — ftp server port
1. user (**string**) — account of ftp server
1. password (**string**) — password of ftp server

> the string value can't be empty or null. the port should be small than 65535 and larger than zero.

**Example Request**

```json
{
  "name": "ftp0",
  "hostname": "node00",
  "port": 22,
  "user": "abc",
  "password": "pwd"
}
```

**Example Response**

```json
{
  "name": "ftp0",
  "hostname": "node00",
  "port": 22,
  "user": "abc",
  "password": "pwd",
  "lastModified": 1553498552595
}
```

----------

### update a ftp information

*PUT /v0/ftp/${name}*

1. name (**string**) — name of this ftp information
1. hostname (**option(string)**) — ftp server hostname
1. port (**option(int)**) — ftp server port
1. user (**option(string)**) — account of ftp server
1. password (**option(string)**) — password of ftp server

> the string value can't be empty or null. the port should be small than 65535 and larger than zero.

**Example Request**

```json
{
  "name": "ftp0",
  "hostname": "node00",
  "port": 22,
  "user": "abc",
  "password": "pwd"
}
```

> Noted, this APIs will create an new ftp object if the input name is not associated to an existent object.

**Example Response**

```json
{
  "name": "ftp0",
  "hostname": "node00",
  "port": 22,
  "user": "abc",
  "password": "pwd",
  "lastModified": 1553498552595
}
```

----------

### list all ftp information stored in ohara

*GET /v0/ftp*

**Example Response**

```json
[
  {
    "name": "ftp0",
    "hostname": "node00",
    "port": 22,
    "user": "abc",
    "password": "pwd",
    "lastModified": 1553498552595
  }
]
```

----------

### delete a ftp information

*DELETE /v0/ftp/${name}*

**Example Response**

```
204 NoContent
```

> It is ok to delete an jar from an nonexistent ftp information, and the response is 204 NoContent.

----------
### get a ftp information

*GET /v0/ftp/${name}*

**Example Response**

```json
{
  "name": "ftp0",
  "hostname": "node00",
  "port": 22,
  "user": "abc",
  "password": "pwd",
  "lastModified": 1553498552595
}
```

----------

## HDFS Connection Information

Ohara supports to store the simple hdfs information which is running on single namenode without security configuration.

1. name (**string**) — name of this hdfs information.
1. uri (**string**) — hdfs connection information. The form looks like "hdfs://namenode:9999/"

The following information are tagged by ohara.

1. lastModified (**long**) — the last time to update this hdfs information
----------
### store a hdfs information

*POST /v0/hdfs*

1. name (**string**) — name of this hdfs information.
1. uri (**string**) — hdfs connection information. The form looks like "hdfs://namenode:9999/"

**Example Request**

```json
{
  "name": "hdfs0",
  "uri": "hdfs://namenode:9999"
}
```

**Example Response**

```json
{
  "name": "hdfs0",
  "uri": "hdfs://namenode:9999",
  "lastModified": 1553498552595
}
```
----------
### update a hdfs information

*PUT /v0/hdfs/${id}*

1. name (**string**) — name of this hdfs information.
1. uri (**option(string)**) — hdfs connection information. The form looks like "hdfs://namenode:9999/"

**Example Request**

```json
{
  "name": "hdfs0",
  "uri": "hdfs://namenode:9999"
}
```

> Noted, this API creates an new object if input name does not exist.

**Example Response**

```json
{
  "name": "hdfs0",
  "uri": "hdfs://namenode:9999",
  "lastModified": 1553498552595
}
```
----------
### list all hdfs information stored in ohara

*GET /v0/hdfs*

**Example Response**

```json
[
  {
    "name": "hdfs0",
    "uri": "hdfs://namenode:9999",
    "lastModified": 1553498552595
  }
]
```
----------
### delete a hdfs information

*DELETE /v0/hdfs/${id}*

**Example Response**

```
204 NoContent
```

> It is ok to delete an jar from an nonexistent hdfs information, and the response is 204 NoContent.

----------
### get a hdfs information

*GET /v0/hdfs/${id}*

**Example Response**

```json
{
  "name": "hdfs0",
  "uri": "hdfs://namenode:9999",
  "lastModified": 1553498552595
}
```
----------
## JDBC Connection Information

Database is a common data source in our world. Ohara also supports to link database to be a part of streaming, so there
are also APIs which help us to store related information used to connect database. Given that we are in java world,
the jdbc is only supported now. The storable information is shown below.

1. name (**string**) — name of this jdbc information.
1. url (**string**) — jdbc connection information. format: jdbc:${database}://${serverName}\$instanceName:$portNumber
1. user (**string**) — the account which has permission to access database
1. password (**string**) — password of account. It is stored as text in ohara

The following information are tagged by ohara.

1. lastModified (**long**) — the last time to update this jdbc information

----------

### store a jdbc information

*POST /v0/jdbc*

1. name (**string**) — name of this jdbc information.
1. url (**string**) — jdbc connection information. format: jdbc:${database}://${serverName}\$instanceName:$portNumber
1. user (**string**) — the account which has permission to access database
1. password (**string**) — password of account. It is stored as text in ohara

**Example Request**

```json
{
  "name": "jdbc_name",
  "url": "jdbc:mysql",
  "user": "user",
  "password": "aaa"
}
```

**Example Response**

```json
{
  "name": "jdbc_name",
  "url": "jdbc:mysql",
  "lastModified": 1540967970407,
  "user": "user",
  "password": "aaa"
}
```

----------

### update a jdbc information

*PUT /v0/jdbc/${name}*

1. name (**string**) — name of this jdbc information.
1. url (**option(string)**) — jdbc connection information. format: jdbc:${database}://${serverName}\$instanceName:$portNumber
1. user (**option(string)**) — the account which has permission to access database
1. password (**option(string)**) — password of account. It is stored as text in ohara

**Example Request**

```json
{
  "name": "jdbc_name",
  "url": "jdbc:mysql",
  "user": "user",
  "password": "aaa"
}
```

> Noted, an new object will be created if the input name is not associated to an existent object.

**Example Response**

```json
{
  "name": "jdbc_name",
  "url": "jdbc:mysql",
  "lastModified": 1540967970407,
  "user": "user",
  "password": "aaa"
}
```
----------
### list all jdbc information stored in ohara

*GET /v0/jdbc*

**Example Response**

```json
[
  {
    "name": "jdbc_name",
    "url": "jdbc:mysql",
    "lastModified": 1540967970407,
    "user": "user",
    "password": "aaa"
  }
]
```

----------

### delete a jdbc information

*DELETE /v0/jdbc/${name}*

**Example Response**

```
204 NoContent
```

> It is ok to delete an jar from an nonexistent jdbc information, and the response is 204 NoContent.

----------
### get a jdbc information

*GET /v0/jdbc/${name}*

**Example Response**

```json
{
  "name": "jdbc_name",
  "url": "jdbc:mysql",
  "lastModified": 1540967970407,
  "user": "user",
  "password": "aaa"
}
```

----------

## Connector

Connector is core of application in ohara [pipeline](#pipeline). Connector has two type - source and sink. Source connector
pulls data from another system and then push to topic. By contrast, Sink connector pulls data from topic and then push to
another system. In order to use connector in [pipeline](#pipeline), you have to set up a connector settings in ohara and then add it
to [pipeline](#pipeline). Of course, the connector settings must belong to a existent connector in target worker cluster. By default,
worker cluster hosts only the official connectors. If you have more custom requirement for connector, please follow
[custom connector guideline](custom_connector.md) to write your connector. 

Apart from custom settings, common settings are required by all connectors. The common settings are shown below.
1. connector.name (**string**) — the name of this connector
1. connector.class (**class**) — class name of connector implementation
1. topics(**array(string)**) — the source topics or target topics for this connector
1. columns (**array(object)**) — the schema of data for this connector
  - columns[i].name (**string**) — origin name of column
  - columns[i].newName (**string**) — new name of column
  - columns[i].dataType (**string**) — the type used to convert data
  - columns[i].order (**int**) — the order of this column
1. numberOfTasks (**int**) — the number of tasks
1. workerClusterName (**string**) — target worker cluster

The following information are updated by ohara.
1. name (**string**) — connector's name
1. lastModified (**long**) — the last time to update this connector
1. state (**optional(string)**) — the state of a started connector. If the connector is not started, you won't see this field
1. error (**optional(string)**) — the error message from a failed connector. If the connector is fine or un-started, you won't get this field.
1. [metrics](custom_connector.html#metrics) (**object**) — the metrics from a running connector
  - meters (**array(object)**) — the metrics in meter type
    - meters[i].value (**double**) — the number stored in meter
    - meters[i].unit (**string**) — unit for value
    - meters[i].document (**string**) — document of this meter
    
The settings from request, BTW, is a individual item in response. Hence, you will observe the following response
after you store the settings with connector.class.

```json
{
  "settings": {
    "connector.class": "abc"
  }
}

```
----------
### create the settings of connector

*POST /v0/connectors*

It is ok to lack some common settings when creating settings for a connector. However, it is illegal to start a connector
with incomplete settings. For example, storing the settings consisting of only **connector.name** is ok. But stating
a connector with above incomplete settings will introduce a error.

**Example Request**

```json
{
  "name": "jdbc_name",
  "connector.class": "com.island.ohara.connector.ftp.FtpSource"
}
```

**Example Response**

```json
{
  "lastModified": 1540967970407,
  "name": "jdbc_name",
  "settings": {
    "connector.name": "jdbc_name",
    "connector.class": "com.island.ohara.connector.ftp.FtpSource"
  },
  "metrics": {
    "meters": []
  }
}
```
----------
### update the settings of connector

*PUT /v0/connectors/${name}*

**Example Request**

```json
{
  "name": "jdbc_name",
  "connector.class": "com.island.ohara.connector.ftp.FtpSource"
}
```

**Example Response**

```json
{
  "lastModified": 1540967970407,
  "name": "jdbc_name",
  "settings": {
    "connector.name": "jdbc_name",
    "connector.class": "com.island.ohara.connector.ftp.FtpSource"
  },
  "metrics": {
    "meters": []
  }
}
```
----------
### list information of all connectors

*GET /v0/connectors*

**Example Response**

```json
[
  {
    "lastModified": 1540967970407,
    "name": "jdbc_name",
    "settings": {
      "connector.name": "jdbc_name",
      "connector.class": "com.island.ohara.connector.ftp.FtpSource"
    },
    "metrics": {
      "meters": []
    }
  }
]
```
----------
### delete a connector

*DELETE /v0/connectors/${id}*

Deleting the settings used by a running connector is not allowed. You should [stop](#stop-a-connector) connector before deleting it.

**Example Response**

```
204 NoContent
```

> It is ok to delete an jar from an nonexistent connector or a running connector, and the response is 204 NoContent.

----------
### get information of connector

*GET /v0/connectors/${id}*

**Example Response**

```json
{
  "lastModified": 1540967970407,
  "name": "jdbc_name",
  "settings": {
    "connector.name": "jdbc_name",
    "connector.class": "com.island.ohara.connector.ftp.FtpSource"
  },
  "metrics": {
    "meters": []
  }
}
```
----------
### start a connector

*PUT /v0/connectors/${name}/start*

Ohara will send a start request to specific worker cluster to start the connector with stored settings, and then make
a response to called. The connector is executed async so the connector may be still in starting after you retrieve
the response. You can send [GET request](#get-information-of-connector) to see the state of connector.
This request is idempotent so it is safe to retry this command repeatedly.

**Example Response**

```json
{
  "lastModified": 1540967970407,
  "name": "jdbc_name",
  "settings": {
    "connector.name": "jdbc_name",
    "connector.class": "com.island.ohara.connector.ftp.FtpSource"
  },
  "state": "RUNNING",
  "metrics": {
    "meters": [
      {
        "value": 1234,
        "unit": "rows",
        "document": "number of processed rows"
      }
    ]
  }
}
```
### stop a connector

*PUT /v0/connectors/${name}/stop*

Ohara will send a stop request to specific worker cluster to stop the connector. The stopped connector will be removed from
worker cluster. The settings of connector is still kept by ohara so you can start the connector with same settings again
in the future. If you want to delete the connector totally, you should stop the connector and then [delete](#delete-a-connector) it.
This request is idempotent so it is safe to send this request repeatedly.

**Example Response**

```json
{
  "lastModified": 1540967970407,
  "name": "jdbc_name",
  "settings": {
    "connector.name": "jdbc_name",
    "connector.class": "com.island.ohara.connector.ftp.FtpSource"
  },
  "metrics": {
    "meters": []
  }
}
```
----------
### pause a connector

*PUT /v0/connectors/${name}/pause*

Pausing a connector is to disable connector to pull/push data from/to source/sink. The connector is still alive in kafka.
This request is idempotent so it is safe to send this request repeatedly.

**Example Response**

```json
{
  "lastModified": 1540967970407,
  "name": "jdbc_name",
  "settings": {
    "connector.name": "jdbc_name",
    "connector.class": "com.island.ohara.connector.ftp.FtpSource"
  },
  "state": "PAUSED",
  "metrics": {
    "meters": [
      {
        "value": 1234,
        "unit": "rows",
        "document": "number of processed rows"
      }
    ]
  }
}
```
----------
### resume a connector

*PUT /v0/connectors/${name}/resume*

Resuming a connector is to enable connector to pull/push data from/to source/sink.
This request is idempotent so it is safe to retry this command repeatedly.

**Example Response**

```json
{
  "lastModified": 1540967970407,
  "name": "jdbc_name",
  "settings": {
    "connector.name": "jdbc_name",
    "connector.class": "com.island.ohara.connector.ftp.FtpSource"
  },
  "state": "RUNNING",
  "metrics": {
    "meters": [
      {
        "value": 1234,
        "unit": "rows",
        "document": "number of processed rows"
      }
    ]
  }
}
```
----------
## Pipeline

Pipeline APIs are born of ohara-manager which needs a way to store the relationship of components in streaming.
The relationship in pipeline is made up of multi **flows**. Each **flow** describe a **from** and multi **to**s.
For example, you have a [topic](#topic) as source and a [connector](#connector) as consumer, so you can describe the relationship
via following flow.
```json
{
  "flows": [
    {
      "from": "topic's id",
      "to": ["connector's id"]
    }
  ]
}
```

Each object described in pipeline must be related to a existent object. If you delete a object which is already used by
a pipeline, it will be deleted from pipeline's flow.

All objects in pipeline MUST work on the same cluster hierarchy. For example, you have following clusters.
1. a ([worker cluster](#worker)) based on
1. b ([broker cluster](#broker)) based on
1. c ([zookeeper cluster](#zookeeper))

When you add a [topic](#topic) to pipeline which is located at cluster-a, the [topic](#topic) must be located at broker-b. Otherwise, you will get
a error saying the [topic](#topic) can't be belong to you. A [connect](#connector) which is not hosted by same worker cluster
can't be related to pipeline also.

The properties used in generating pipeline are shown below.
1. name (**string**) — pipeline's name
1. flows (**array(object)**) — the relationship between objects
  - flows[i].from (**string**) — the endpoint of source
  - flows[i].to (**array(string)**) — the endpoint of sink
1. workerClusterName (**string**) — target worker cluster
  
Following information are written by ohara.
 1. lastModified (**long**) — the last time to update this pipeline
 1. objects(**array(object)**) — the abstract of all objects mentioned by pipeline
   - objects[i].[id](#object-id) (**string**) — object's id
   - objects[i].name (**string**) — object's name
   - objects[i].kind (**string**) — the type of this object. for instance, [topic](#topic), [connector](#connector), and [streamapp](#streamapp) 
   - objects[i].className (**string**) — object's implementation. Normally, it shows the full name of a java class
   - objects[i].state (**optional(string)**) — the state of object. If the object can't have state (eg, [topic](#topic)), you won't see this field
   - objects[i].error (**optional(string)**) — the error message of this object
   - objects[i].lastModified (**long**) — the last time to update this object
   - [metrics](custom_connector.html#metrics) (**object**) — the metrics from this object. Not all objects in pipeline have metrics!
     - meters (**array(object)**) — the metrics in meter type
       - meters[i].value (**double**) — the number stored in meter
       - meters[i].unit (**string**) — unit for value
       - meters[i].document (**string**) — document of this meter
----------
### create a pipeline

*POST /v0/pipelines*

The following example creates a pipeline with a [topic](#topic) and [connector](#connector). The [topic](#topic) is created on
[broker cluster](#broker) but the [connector](#connector) isn't. Hence, the response from server shows that it fails
to find the status of the [connector](#connector). That is to say, it is ok to add un-running [connector](#connector) to pipeline.

**Example Request 1**

```json
{
  "name": "pipeline0",
  "flows": [
    {
      "from": "be48b7d8-08a8-40a4-8f17-aaa",
      "to": ["81cb80a9-34a5-4e45-881a-cb87d4fbb5bd"]
    }
  ]
}
```

**Example Response 1**

```json
{
  "name": "pipeline0",
  "lastModified": 1554950999668,
  "workerClusterName": "wk00",
  "flows": [
    {
      "from": "be48b7d8-08a8-40a4-8f17-9c1d1fe655b6",
      "to": [
        "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd"
      ]
    }
  ],
  "objects": [
    {
      "name": "topic0",
      "lastModified": 1554950034608,
      "id": "be48b7d8-08a8-40a4-8f17-9c1d1fe655b6",
      "metrics": {
        "meters": []
      },
      "kind": "topic"
    },
    {
      "name": "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd",
      "lastModified": 1554950058696,
      "id": "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd",
      "error": "Failed to get status and type of connector:81cb80a9-34a5-4e45-881a-cb87d4fbb5bd.This may be temporary since our worker cluster is too busy to sync status of connector. abc doesn't exist",
      "metrics": {
        "meters": []
      },
      "kind": "connector"
    }
  ]
}
```

Don't worry about creating a pipeline with incomplete flows. It is ok to add a flow with only **from**. The following
example creates a pipeline with only a object and leave empty in **to** field.

**Example Request 1**

```json
{
  "name": "pipeline1",
  "flows": [
    {
      "from": "be48b7d8-08a8-40a4-8f17-9c1d1fe655b6",
      "to": []
    }
  ]
}
```

**Example Response 1**

```json
{
  "name": "pipeline1",
  "lastModified": 1554952500972,
  "workerClusterName": "wk00",
  "flows": [
    {
      "from": "be48b7d8-08a8-40a4-8f17-9c1d1fe655b6",
      "to": []
    }
  ],
  "objects": [
    {
      "name": "topic0",
      "lastModified": 1554950034608,
      "id": "be48b7d8-08a8-40a4-8f17-9c1d1fe655b6",
      "metrics": {
        "meters": []
      },
      "kind": "topic"
    }
  ]
}
```
----------
### update a pipeline

*PUT /v0/pipelines/$name*

**Example Request**

```json
{
  "name": "pipeline0",
  "flows": [
    {
      "from": "be48b7d8-08a8-40a4-8f17-aaa",
      "to": ["81cb80a9-34a5-4e45-881a-cb87d4fbb5bd"]
    }
  ]
}
```

> Noted, this API creates an new pipeline for you if the input name does not exist!

**Example Response**

```json
{
  "name": "pipeline0",
  "lastModified": 1554950999668,
  "workerClusterName": "wk00",
  "flows": [
    {
      "from": "be48b7d8-08a8-40a4-8f17-9c1d1fe655b6",
      "to": [
        "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd"
      ]
    }
  ],
  "objects": [
    {
      "name": "topic0",
      "lastModified": 1554950034608,
      "id": "be48b7d8-08a8-40a4-8f17-9c1d1fe655b6",
      "metrics": {
        "meters": []
      },
      "kind": "topic"
    },
    {
      "name": "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd",
      "lastModified": 1554950058696,
      "id": "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd",
      "error": "Failed to get status and type of connector:81cb80a9-34a5-4e45-881a-cb87d4fbb5bd.This may be temporary since our worker cluster is too busy to sync status of connector. abc doesn't exist",
      "metrics": {
        "meters": []
      },
      "kind": "connector"
    }
  ]
}
```
----------
### list all pipelines

*GET /v0/pipelines*

Listing all pipelines is a expensive operation as it invokes a iteration to all objects stored in pipeline. The loop will
do a lot of checks and fetch status, metrics and log from backend clusters. If you have the id of pipeline, please
use [GET](#get-a-pipeline) to fetch details of **single** pipeline.

**Example Response**

```json
[
  {
    "name": "pipeline0",
    "lastModified": 1554950999668,
    "workerClusterName": "wk00",
    "flows": [
      {
        "from": "be48b7d8-08a8-40a4-8f17-9c1d1fe655b6",
        "to": [
          "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd"
        ]
      }
    ],
    "objects": [
      {
        "name": "topic0",
        "lastModified": 1554950034608,
        "id": "be48b7d8-08a8-40a4-8f17-9c1d1fe655b6",
        "metrics": {
          "meters": []
        },
        "kind": "topic"
      },
      {
        "name": "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd",
        "lastModified": 1554950058696,
        "id": "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd",
        "error": "Failed to get status and type of connector:81cb80a9-34a5-4e45-881a-cb87d4fbb5bd.This may be temporary since our worker cluster is too busy to sync status of connector. abc doesn't exist",
        "metrics": {
          "meters": []
        },
        "kind": "connector"
      }
    ]
  }
]
```
----------
### delete a pipeline

*DELETE /v0/pipelines/$name*

Deleting a pipeline does not delete the objects related to the pipeline.

**Example Response**

```
204 NoContent
```

> It is ok to delete an an nonexistent pipeline, and the response is 204 NoContent. However, it is illegal to remove
  a pipeline having any running objects

----------
### get a pipeline

*GET /v0/pipelines/$name*

**Example Response**

```json
{
  "name": "pipeline0",
  "lastModified": 1554950999668,
  "workerClusterName": "wk00",
  "flows": [
    {
      "from": "be48b7d8-08a8-40a4-8f17-9c1d1fe655b6",
      "to": [
        "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd"
      ]
    }
  ],
  "objects": [
    {
      "name": "topic0",
      "lastModified": 1554950034608,
      "id": "be48b7d8-08a8-40a4-8f17-9c1d1fe655b6",
      "metrics": {
        "meters": []
      },
      "kind": "topic"
    },
    {
      "name": "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd",
      "lastModified": 1554950058696,
      "id": "81cb80a9-34a5-4e45-881a-cb87d4fbb5bd",
      "error": "Failed to get status and type of connector:81cb80a9-34a5-4e45-881a-cb87d4fbb5bd.This may be temporary since our worker cluster is too busy to sync status of connector. abc doesn't exist",
      "metrics": {
        "meters": []
      },
      "kind": "connector"
    }
  ]
}
```
----------
## Node

Node is the basic unit of running service. It can be either physical machine or vm. In section [Zookeeper](#zookeeper),
[Broker](#broker) and [Worker](#worker), you will see many requests demanding you to fill the node name to build the
services. Currently, ohara requires the node added to ohara should pre-install following services.
1. docker (18.09+)
1. ssh server
1. k8s (only if you want to k8s to host containers)
1. official ohara images
  - [oharastream/zookeeper](https://cloud.docker.com/u/oharastream/repository/docker/oharastream/zookeeper)
  - [oharastream/broker](https://cloud.docker.com/u/oharastream/repository/docker/oharastream/broker)
  - [oharastream/connect-worker](https://cloud.docker.com/u/oharastream/repository/docker/oharastream/connect-worker)
  - [oharastream/streamapp](https://cloud.docker.com/u/oharastream/repository/docker/oharastream/streamapp)

The version (tag) depends on which ohara you used. It would be better to use the same version to ohara. For example,
the version of ohara configurator you are running is 0.4, then the official images you should download is oharastream/xx:0.4.

The properties used in describing a node are shown below.
1. name (**string**) — hostname of node
1. port (**int**) — ssh port of node
1. user (**string**) — ssh account
1. password (**string**) — ssh password

> ohara use above information to login node to manage the containers. Please make sure the account has permission to
operate docker (and k8s service) without sudo.

The following information are tagged by ohara.
1. [id](#object-id) (**string**) — node id
1. lastModified (**long**) — the last time to update this node
  
----------
### store a node

*POST /v0/nodes*

1. name (**string**) — hostname of node
1. port (**int**) — ssh port of node
1. user (**string**) — ssh account
1. password (**string**) — ssh password

**Example Request**

```json
{
  "name": "node00",
  "port": 22,
  "user": "abc",
  "password": "pwd"
}
```

**Example Response**

```json
{
  "name": "node00",
  "port": 22,
  "user": "abc",
  "password": "pwd",
  "lastModified": 1553498552595,
  "id": "715e09c9-b4ee-41cc-8d05-cb544904ac38"
}
```
----------
### update a node

*PUT /v0/nodes/${id}*

1. name (**string**) — hostname of node
1. port (**int**) — ssh port of node
1. user (**string**) — ssh account
1. password (**string**) — ssh password

**Example Request**

```json
{
  "name": "node00",
  "port": 22,
  "user": "abc",
  "password": "pwd"
}
```

> An new node will be created if your input name does not exist

**Example Response**

```json
{
  "name": "node00",
  "port": 22,
  "user": "abc",
  "password": "pwd",
  "lastModified": 1553498552595,
  "id": "715e09c9-b4ee-41cc-8d05-cb544904ac38"
}
```
----------
### list all nodes stored in ohara

*GET /v0/nodes*

**Example Response**

```json
[
  {
    "name": "node00",
    "port": 22,
    "user": "abc",
    "password": "pwd",
    "lastModified": 1553498552595,
    "id": "715e09c9-b4ee-41cc-8d05-cb544904ac38"
  }
]
```
----------
### delete a node

*DELETE /v0/nodes/${id}*



**Example Response**

```
204 NoContent
```

> It is ok to delete an an nonexistent pipeline, and the response is 204 NoContent. However, it is disallowed to remove
  a node which is running service. If you do want to delete the node from ohara, please stop all services from the node.
  
----------
### get a node

*GET /v0/nodes/${id}*

**Example Response**

```json
{
  "name": "node00",
  "port": 22,
  "user": "abc",
  "password": "pwd",
  "lastModified": 1553498552595,
  "id": "715e09c9-b4ee-41cc-8d05-cb544904ac38"
}
```
----------
## Zookeeper

[Zookeeper](https://zookeeper.apache.org) service is the base of all other services. It is also the fist service you should
set up. At the beginning, you can deploy zookeeper cluster in single node. However, it may be unstable since single node
can't guarantee the data durability when node crash. In production you should set up zookeeper cluster on 3 nodes at least.

Zookeeper service has many configs which make you spend a lot of time to read and set. Ohara provides default values to
all configs but open a room to enable you to overwrite somethings you do care.
1. name (**string**) — cluster name
1. imageName (**string**) — docker image
1. clientPort (**int**) — broker client port.
1. electionPort (**int**) — used to select the zk node leader
1. peerPort (**int**) — port used by internal communication
1. nodeNames (**array(string)**) — the nodes running the zookeeper process
1. deadNodes (**array(string)**) — the nodes that have failed containers of zookeeper
  
----------
### create a zookeeper cluster

*POST /v0/zookeepers*

1. name (**string**) — cluster name
1. imageName (**string**) — docker image
1. clientPort (**int**) — broker client port.
1. electionPort (**int**) — used to select the zk node leader
1. peerPort (**int**) — port used by internal communication
1. nodeNames (**array(string)**) — the nodes running the zookeeper process

**Example Request**

```json
{
  "name": "zk00",
  "imageName": "oharastream/zookeeper:0.6.1-SNAPSHOT",
  "clientPort": 12345,
  "peerPort": 12346,
  "electionPort": 12347,
  "nodeNames": [
    "node00"
  ]
}
```

**Example Response**

```json
{
  "name": "zk00",
  "electionPort": 12347,
  "imageName": "oharastream/zookeeper:0.6.1-SNAPSHOT",
  "clientPort": 12345,
  "peerPort": 12346,
  "nodeNames": [
    "node00"
  ],
  "deadNodes": []
}
```

As mentioned before, ohara provides default to most settings. You can just input name and nodeNames to run a zookeeper cluster.

**Example Request**

```json
{
  "name": "zk00",
  "nodeNames": [
    "node00"
  ]
}
```

> All ports have default value so you can ignore them when creating zookeeper cluster. However, the port conflict detect
does not allow you to reuse port on different purpose (a dangerous behavior, right?).

**Example Response**

```json
{
  "name": "zk00",
  "electionPort": 3888,
  "imageName": "oharastream/zookeeper:0.6.1-SNAPSHOT",
  "clientPort": 2181,
  "peerPort": 2888,
  "nodeNames": [
    "node00"
  ],
  "deadNodes": []
}
```
----------
### list all zookeeper clusters

*GET /v0/zookeepers*

**Example Response**

```json
[
  {
    "name": "zk00",
    "electionPort": 12347,
    "imageName": "oharastream/zookeeper:0.6.1-SNAPSHOT",
    "clientPort": 12345,
    "peerPort": 12346,
    "nodeNames": [
      "node00"
    ],
    "deadNodes": []
  }
]
```
----------
### delete a zookeeper cluster

*DELETE /v0/zookeepers/$name*

It is disallowed to remove a zookeeper cluster used by a running [broker cluster](#broker).

**Query Parameters**
1. force (**boolean**) — true if you don't want to wait the graceful shutdown (it can save your time but may damage your data). Other values invoke graceful delete. 

**Example Response**

```
204 NoContent
```

> It is ok to delete an nonexistent zookeeper cluster, and the response is 204 NoContent.

----------
### get a zookeeper cluster

*GET /v0/zookeepers/$name*

**Example Response**

```json
{
  "name": "zk00",
  "electionPort": 12347,
  "imageName": "oharastream/zookeeper:0.6.1-SNAPSHOT",
  "clientPort": 12345,
  "peerPort": 12346,
  "nodeNames": [
    "node00"
  ],
  "deadNodes": []
}
```

### delete a node from a running zookeeper cluster

Unfortunately, it is a litter dangerous to remove a node from a running zookeeper cluster so we don't support it yet. 

----------

### add a node to a running zookeeper cluster

Unfortunately, it is a litter hard to add a node to a running zookeeper cluster so we don't support it yet. 

----------

## Broker

[Broker](https://kafka.apache.org/intro) is core of data transmission in ohara. The topic, which is a part our data lake,
is hosted by broker cluster. The number of brokers impacts the performance of transferring data and data durability.
But it is ok to setup broker cluster in single node when testing. As with [Zookeeper](#zookeeper), broker has many configs also.
Ohara still provide default to most configs and then enable user to overwrite them.

Broker is based on [Zookeeper](#zookeeper), hence you have to create zookeeper cluster first. Noted that a zookeeper
cluster can be used by only a broker cluster. It will fail if you try to multi broker cluster on same zookeeper cluster. 

The properties which can be set by user are shown below.
1. name (**string**) — cluster name
1. imageName (**string**) — docker image
1. clientPort (**int**) — broker client port
1. exporterPort (**int**) — port used by internal communication
1. jmxPort (**int**) — port used by jmx service
1. zookeeperClusterName (**String**) — name of zookeeper cluster used to store metadata of broker cluster
1. nodeNames (**array(string)**) — the nodes running the broker process
1. deadNodes (**array(string)**) — the nodes that have failed containers of broker

----------
### create a broker cluster

*POST /v0/brokers*

1. name (**string**) — cluster name
1. imageName (**string**) — docker image
1. clientPort (**int**) — broker client port.
1. exporterPort (**int**) — port used by internal communication
1. jmxPort (**int**) — port used by jmx service
1. zookeeperClusterName (**String**) — name of zookeeper cluster used to store metadata of broker cluster
1. nodeNames (**array(string)**) — the nodes running the broker process

**Example Request**

```json
{
  "name": "bk00",
  "imageName": "oharastream/broker:0.6.1-SNAPSHOT",
  "zookeeperClusterName": "zk00",
  "clientPort": 12345,
  "exporterPort": 12346,
  "jmxPort": 12347,
  "nodeNames": [
    "node00"
  ]
}
```

**Example Response**

```json
{
  "name": "bk00",
  "zookeeperClusterName": "zk00",
  "imageName": "oharastream/broker:0.6.1-SNAPSHOT",
  "exporterPort": 12346,
  "clientPort": 12345,
  "jmxPort": 12347,
  "nodeNames": [
    "node00"
  ],
  "deadNodes": []
}
```

As mentioned before, ohara provides default to most settings. You can just input name and nodeNames to run a broker cluster.

**Example Request**

```json
{
  "name": "bk00",
  "nodeNames": [
    "node00"
  ]
}
```
> As you don't input the zookeeper cluster name, Ohara will try to pick up a zookeeper cluster for you. If the number
of zookeeper cluster host by ohara is only one, ohara do deploy broker cluster on the zookeeper cluster. Otherwise,
ohara will say that it can't match a zookeeper cluster for you.  All ports have default value so you can ignore
them when creating zookeeper cluster. However, the port conflict detect does not allow you to reuse port on
different purpose (a dangerous behavior, right?).

**Example Response**

```json
{
  "name": "bk00",
  "zookeeperClusterName": "zk00",
  "imageName": "oharastream/broker:0.6.1-SNAPSHOT",
  "exporterPort": 7071,
  "clientPort": 9092,
  "jmxPort": 9093,
  "nodeNames": [
    "node00"
  ],
  "deadNodes": []
}
```
----------
### list all broker clusters

*GET /v0/brokers*

**Example Response**

```json
[
  {
    "name": "bk00",
    "zookeeperClusterName": "zk00",
    "imageName": "oharastream/broker:0.6.1-SNAPSHOT",
    "exporterPort": 7071,
    "clientPort": 9092,
    "jmxPort": 9093,
    "nodeNames": [
      "node00"
    ],
    "deadNodes": []
  }
]
```
----------
### delete a broker cluster

*DELETE /v0/brokers/$name*

It is disallowed to remove a broker cluster used by a running [worker cluster](#worker).

**Query Parameters**
1. force (**boolean**) — true if you don't want to wait the graceful shutdown (it can save your time but may damage your data). Other values invoke graceful delete. 

**Example Response**

```
204 NoContent
```

> It is ok to delete an nonexistent broker cluster, and the response is 204 NoContent.

----------

### get a broker cluster

*GET /v0/brokers/$name*

**Example Response**

```json
{
  "name": "bk00",
  "zookeeperClusterName": "zk00",
  "imageName": "oharastream/broker:0.6.1-SNAPSHOT",
  "exporterPort": 7071,
  "clientPort": 9092,
  "jmxPort": 9093,
  "nodeNames": [
    "node00"
  ],
  "deadNodes": []
}
```
----------
### add a new node to a running broker cluster

*PUT /v0/brokers/$name/$nodeName*

If you want to extend a running broker cluster, you can add a node to share the heavy loading of a running broker cluster.
However, the balance is not triggered at once.

**Example Response**

```json
{
  "name": "bk00",
  "zookeeperClusterName": "zk00",
  "imageName": "oharastream/broker:0.6.1-SNAPSHOT",
  "exporterPort": 7071,
  "clientPort": 9092,
  "jmxPort": 9093,
  "nodeNames": [
    "node01",
    "node00"
  ],
  "deadNodes": []
}
```
----------
### remove a node from a running broker cluster

*DELETE /v0/brokers/$name/$nodeName*

If your budget is limited, you can decrease the number of nodes running broker cluster. BUT, removing a node from a
running broker cluster invoke a lot of data move. The loading may burn out the remaining nodes.

**Example Response**

```
204 NoContent
```

> It is ok to delete an nonexistent broker node, and the response is 204 NoContent.

----------
## Worker

[Worker](https://kafka.apache.org/intro) is core of running connectors for ohara. It provides a simple but powerful system
to distribute and execute connectors on different nodes. The performance of connectors depends on the scale of worker cluster.
For example, you can assign the number of task when creating connector. If there is only 3 nodes within your worker cluster and
you specify 6 tasks for your connector, the tasks of you connectors still be deployed on 3 nodes. That is to say, the connector
can't get more resources to execute.

Worker is based on [Broker](#broker), hence you have to create broker cluster first. Noted that a broker
cluster can be used by multi worker clusters. BTW, worker cluster will pre-allocate a lot of topics on broker cluster, and
the pre-created topics CAN'T be reused by different worker clusters.

The properties which can be set by user are shown below.
1. name (**string**) — cluster name
1. imageName (**string**) — docker image
1. brokerClusterName (**string**) — broker cluster used to host topics for this worker cluster
1. clientPort (**int**) — worker client port
1. jmxPort (**int**) — worker jmx port
1. groupId (**string**) — the id of worker stored in broker cluster
1. configTopicName (**string**) — a internal topic used to store connector configuration
1. configTopicReplications (**int**) — number of replications for config topic
1. offsetTopicName (**string**) — a internal topic used to store connector offset
1. offsetTopicPartitions (**int**) — number of partitions for offset topic
1. offsetTopicReplications (**int**) — number of replications for offset topic
1. statusTopicName (**string**) — a internal topic used to store connector status
1. statusTopicPartitions (**int**) — number of partitions for status topic
1. statusTopicReplications (**int**) — number of replications for status topic
1. jars (**array(object)**) — the "primary key" of jars that will be loaded by worker cluster. You can require worker cluster to
                              load the jars stored in ohara if you want to run custom connectors on the worker cluster.
                              see [Jars APIs](#jars) for uploading jars to ohara.  Noted: the response will replace this
                              by [JarInfo](#jars).
1. nodeNames (**array(string)**) — the nodes running the worker process
1. deadNodes (**array(string)**) — the nodes that have failed containers of worker

> The groupId, configTopicName, offsetTopicName and statusTopicName must be unique in broker cluster. Don't reuse them
in same broker cluster. Dispatching above unique resources to two worker cluster will pollute the data. Of course,
ohara do a quick failure for this dumb case. However, it is not a quick failure when you are using raw kafka rather than
ohara. Please double check what you configure!

After building the worker cluster, ohara starts to fetch the details of available connectors from the worker cluster.
The details is the setting definitions of connector. It shows how to assign the settings to a connector correctly.
The details of connector's setting definitions can be retrieved via [GET](#get-a-worker-cluster) or [LIST](#list-all-workers-clusters),
and the JSON representation is shown below.
```json
{
  "connectors": [
    {
      "className": "xxx",
      "definitions": [
        {
          "reference": "NONE",
          "displayName": "connector.class",
          "internal": false,
          "documentation": "the class name of connector",
          "valueType": "CLASS",
          "tableKeys": [],
          "orderInGroup": 0,
          "key": "connector.class",
          "required": true,
          "defaultValue": null,
          "group": "core",
          "editable": true
        }
      ]
    }
  ]
}
```
1. connectors (**array(string)**) — the available connectors of  worker cluster
  - connectors[i].className (**string**) — the class name of available connector
  - connectors[i].definitions (**array(object)**) — the settings used by this connector
    - connectors[i].definitions[j].displayName (**string**) — the readable name of this setting
    - connectors[i].definitions[j].group (**string**) — the group of this setting (all core setting are in core group)
    - connectors[i].definitions[j].orderInGroup (**int**) — the order in group
    - connectors[i].definitions[j].editable (**boolean**) — true if this setting is modifiable 
    - connectors[i].definitions[j].key (**string**) — the key of configuration
    - connectors[i].definitions[j].[valueType](#setting-type) (**string**) — the type of value
    - connectors[i].definitions[j].defaultValue (**string**) — the default value
    - connectors[i].definitions[j].documentation (**string**) — the explanation of this definition
    - connectors[i].definitions[j].[reference](#setting-reference) (**string**) — works for ohara manager. It represents the reference of value.
    - connectors[i].definitions[j].required(**boolean**) — true if this setting has no default value and you have to assign a value. Otherwise, you can’t start connector.
    - connectors[i].definitions[j].internal (**string**) — true if this setting is assigned by system automatically.
    - connectors[i].definitions[j].tableKeys (**array(string)**) — the column name when the type is TABLE

Apart from official settings (topics, columns, etc), a connector also have custom settings. Those setting definition can
be found through [GET](#get-a-worker-cluster) or [LIST](#list-all-workers-clusters). And for another, the worker cluster needs
to take some time to load available connectors. If you don't see the setting definitions, please retry it later. 

----------
### Setting Type

The type of value includes two processes to input value when you are trying to run a connector. For example, starting
a connector will fail when you input a string to a setting having **int** type. The acceptable types are shown below.
1. Boolean — the value must be castable to **java.lang.Boolean**
1. String — the value must be castable to **java.lang.String**
1. Short — the value must be castable to **java.lang.Short**
1. Int — the value must be castable to **java.lang.Integer**
1. Long — the value must be castable to **java.lang.Long**
1. Double — the value must be castable to **java.lang.Double**
1. Class — the value must be castable to **java.lang.String** and it must be equal to a class in worker's jvm
1. Password — the value must be castable to **java.lang.String**. the value is replaced by **hidden** in APIs
1. List — the value must be castable to **java.lang.String** and it is split according to JSON array
1. Table — the value must be castable to **java.lang.String** and it has the following JSON representation.
1. Duration — the value must be castable to **java.time.Duration** and it is based on the ISO-8601 duration format PnDTnHnMn.nS
```json
[
  {
    "order": 1,
    "c0": "v0",
    "c1": "v1",
    "c2": "v2"
  },
  {
    "order": 2,
    "c0": "t0",
    "c1": "t1",
    "c2": "t2"
  }
]
```
How to get the description of above **keys**? If the setting type is **table**, the setting must have **tableKeys**. It
is a array of string which shows the keys used in the table type. For instance, a setting having table type is shown below.
```json
{
  "reference": "NONE",
  "displayName": "columns",
  "internal": false,
  "documentation": "output schema",
  "valueType": "TABLE",
  "tableKeys": [
    "order",
    "dataType",
    "name",
    "newName"
  ],
  "orderInGroup": 6,
  "key": "columns",
  "required": false,
  "defaultValue": null,
  "group": "core",
  "editable": true
}
```
----------
### Setting Reference

This element is a specific purpose. It is used by ohara manager (UI) only. If you don't have interest in UI, you can just
ignore this element. However, we still list the available values here.
1. TOPIC
1. WORKER_CLUSTER

----------
### create a worker cluster

*POST /v0/workers*

1. name (**string**) — cluster name
1. imageName (**string**) — docker image
1. clientPort (**int**) — worker client port.
1. jmxPort (**int**) — worker jmx port.
1. brokerClusterName (**string**) — broker cluster used to host topics for this worker cluster
1. jars (**array(object)**) — the "primary key" object list of jar that will be loaded by worker cluster
  - jars[i].group (**string**) — the group name of jar
  - jars[i].name (**string**) — the name of jar
1. groupId (**string**) — the id of worker stored in broker cluster
1. configTopicName (**string**) — a internal topic used to store connector configuration
1. configTopicReplications (**int**) — number of replications for config topic
1. offsetTopicName (**string**) — a internal topic used to store connector offset
1. offsetTopicReplications (**int**) — number of replications for offset topic
1. offsetTopicPartitions (**int**) — number of partitions for offset topic
1. statusTopicName (**string**) — a internal topic used to store connector status
1. statusTopicReplications (**int**) — number of replications for status topic
1. statusTopicPartitions (**int**) — number of partitions for status topic
1. nodeNames (**array(string)**) — the nodes running the worker process

**Example Request**

```json
{
  "name": "wk00",
  "imageName": "oharastream/connect-worker:0.6.1-SNAPSHOT",
  "clientPort": 12345,
  "jmxPort": 12346,
  "brokerClusterName": "preCreatedBkCluster",
  "groupId": "abcdefg",
  "configTopicName": "configTopic",
  "configTopicReplications": 1,
  "offsetTopicName": "offsetTopic",
  "offsetTopicReplications": 1,
  "offsetTopicPartitions": 1,
  "statusTopicName": "statusTopic",
  "statusTopicReplications": 1,
  "statusTopicPartitions": 1,
  "jars": [
    {
      "group": "abc",
      "name": "myjar"
    }
  ],
  "nodeNames": [
    "node00"
  ]
}
```

**Example Response**

```json
{
  "statusTopicName": "statusTopic",
  "name": "wk00",
  "offsetTopicPartitions": 1,
  "brokerClusterName": "preCreatedBkCluster",
  "connectors": [],
  "sinks": [],
  "offsetTopicName": "offsetTopic",
  "imageName": "oharastream/connect-worker:0.6.1-SNAPSHOT",
  "groupId": "abcdefg",
  "jarInfos": [],
  "statusTopicReplications": 1,
  "configTopicPartitions": 1,
  "offsetTopicReplications": 1,
  "configTopicReplications": 1,
  "statusTopicPartitions": 1,
  "configTopicName": "configTopic",
  "jmxPort": 12346,
  "clientPort": 12345,
  "nodeNames": [
    "node00"
  ],
  "deadNodes": []
}
```

As mentioned before, ohara provides default to most settings. You can just input name, nodeNames and jars to run a worker cluster.

**Example Request**

```json
{
  "name": "wk00",
  "jars": [
      {
        "group": "abc",
        "name": "myjar"
      }
  ],
  "nodeNames": [
    "node00"
  ]
}
```
> As you don't input the broker cluster name, Ohara will try to pick up a broker cluster for you. If the number
of broker cluster host by ohara is only one, ohara do deploy worker cluster on the broker cluster. Otherwise,
ohara will say that it can't match a broker cluster for you.  All ports have default value so you can ignore
them when creating worker cluster. However, the port conflict detect does not allow you to reuse port on
different purpose (a dangerous behavior, right?).

**Example Response**

```json
{
  "statusTopicName": "status-89eaef1e9d",
  "name": "wk00",
  "offsetTopicPartitions": 1,
  "brokerClusterName": "preCreatedBkCluster",
  "connectors": [],
  "offsetTopicName": "offset-956c528fa5",
  "imageName": "oharastream/connect-worker:0.6.1-SNAPSHOT",
  "groupId": "dcafb19d0e",
  "jarInfos": [],
  "statusTopicReplications": 1,
  "configTopicPartitions": 1,
  "offsetTopicReplications": 1,
  "configTopicReplications": 1,
  "statusTopicPartitions": 1,
  "configTopicName": "setting-67c528ca7d",
  "jmxPort": 8084,
  "clientPort": 8083,
  "nodeNames": [
    "node00"
  ],
  "deadNodes": []
}
```
----------
### list all workers clusters

*GET /v0/workers*

**Example Response**

```json
[
  {
    "statusTopicName": "status-89eaef1e9d",
    "name": "wk00",
    "offsetTopicPartitions": 1,
    "brokerClusterName": "preCreatedBkCluster",
    "connectors": [],
    "offsetTopicName": "offset-956c528fa5",
    "imageName": "oharastream/connect-worker:0.6.1-SNAPSHOT",
    "groupId": "dcafb19d0e",
    "jarInfos": [],
    "statusTopicReplications": 1,
    "configTopicPartitions": 1,
    "offsetTopicReplications": 1,
    "configTopicReplications": 1,
    "statusTopicPartitions": 1,
    "configTopicName": "setting-67c528ca7d",
    "jmxPort": 8084,
    "clientPort": 8083,
    "nodeNames": [
      "node00"
    ],
    "deadNodes": []
  }
]
```
----------
### delete a worker cluster

*DELETE /v0/workers/$name*

**Query Parameters**
1. force (**boolean**) — true if you don't want to wait the graceful shutdown (it can save your time but may damage your data). Other values invoke graceful delete. 

**Example Response**

```
204 NoContent
```

> It is ok to delete an nonexistent worker cluster, and the response is 204 NoContent.

----------
### get a worker cluster

*GET /v0/workers/$name*

**Example Response**

```json
{
  "statusTopicName": "status-d7f7a35aa4",
  "name": "wk00",
  "offsetTopicPartitions": 1,
  "brokerClusterName": "preCreatedBkCluster",
  "connectors": [
    {
      "className": "com.island.ohara.connector.perf.PerfSource",
      "definitions": [
        {
          "reference": "NONE",
          "displayName": "connector.class",
          "internal": false,
          "documentation": "the class name of connector",
          "valueType": "CLASS",
          "tableKeys": [],
          "orderInGroup": 0,
          "key": "connector.class",
          "required": true,
          "defaultValue": null,
          "group": "core",
          "editable": true
        },
        {
          "reference": "NONE",
          "displayName": "tasks.max",
          "internal": false,
          "documentation": "the number of tasks invoked by connector",
          "valueType": "INT",
          "tableKeys": [],
          "orderInGroup": 3,
          "key": "tasks.max",
          "required": true,
          "defaultValue": null,
          "group": "core",
          "editable": true
        },
        {
          "reference": "NONE",
          "displayName": "key.converter",
          "internal": true,
          "documentation": "key converter",
          "valueType": "CLASS",
          "tableKeys": [],
          "orderInGroup": 4,
          "key": "key.converter",
          "required": false,
          "defaultValue": "org.apache.kafka.connect.converters.ByteArrayConverter",
          "group": "core",
          "editable": true
        },
        {
          "reference": "NONE",
          "displayName": "value.converter",
          "internal": true,
          "documentation": "value converter",
          "valueType": "STRING",
          "tableKeys": [],
          "orderInGroup": 5,
          "key": "value.converter",
          "required": false,
          "defaultValue": "org.apache.kafka.connect.converters.ByteArrayConverter",
          "group": "core",
          "editable": true
        },
        {
          "reference": "NONE",
          "displayName": "kind",
          "internal": false,
          "documentation": "kind of connector",
          "valueType": "STRING",
          "tableKeys": [],
          "orderInGroup": 11,
          "key": "kind",
          "required": false,
          "defaultValue": "source",
          "group": "core",
          "editable": false
        },
        {
          "reference": "NONE",
          "displayName": "connector.name",
          "internal": false,
          "documentation": "the name of connector",
          "valueType": "STRING",
          "tableKeys": [],
          "orderInGroup": 1,
          "key": "connector.name",
          "required": false,
          "defaultValue": null,
          "group": "core",
          "editable": true
        },
        {
          "reference": "NONE",
          "displayName": "columns",
          "internal": false,
          "documentation": "output schema",
          "valueType": "TABLE",
          "tableKeys": [
            "order",
            "dataType",
            "name",
            "newName"
          ],
          "orderInGroup": 6,
          "key": "columns",
          "required": false,
          "defaultValue": null,
          "group": "core",
          "editable": true
        },
        {
          "reference": "WORKER_CLUSTER",
          "displayName": "workerClusterName",
          "internal": false,
          "documentation": "the cluster name of running this connector.If there is only one worker cluster, you can skip this setting since configurator will pick up a worker cluster for you",
          "valueType": "STRING",
          "tableKeys": [],
          "orderInGroup": 7,
          "key": "workerClusterName",
          "required": false,
          "defaultValue": null,
          "group": "core",
          "editable": true
        },
        {
          "reference": "TOPIC",
          "displayName": "topics",
          "internal": false,
          "documentation": "the topics used by connector",
          "valueType": "LIST",
          "tableKeys": [],
          "orderInGroup": 2,
          "key": "topics",
          "required": true,
          "defaultValue": null,
          "group": "core",
          "editable": true
        },
        {
          "reference": "NONE",
          "displayName": "version",
          "internal": false,
          "documentation": "version of connector",
          "valueType": "STRING",
          "tableKeys": [],
          "orderInGroup": 8,
          "key": "version",
          "required": false,
          "defaultValue": "0.6.1-SNAPSHOT",
          "group": "core",
          "editable": false
        },
        {
          "reference": "NONE",
          "displayName": "revision",
          "internal": false,
          "documentation": "revision of connector",
          "valueType": "STRING",
          "tableKeys": [],
          "orderInGroup": 9,
          "key": "revision",
          "required": false,
          "defaultValue": "8faa89f18370c891422dae1993def55795f7ef2e",
          "group": "core",
          "editable": false
        },
        {
          "reference": "NONE",
          "displayName": "author",
          "internal": false,
          "documentation": "author of connector",
          "valueType": "STRING",
          "tableKeys": [],
          "orderInGroup": 10,
          "key": "author",
          "required": false,
          "defaultValue": "root",
          "group": "core",
          "editable": false
        }
      ]
    }
  ],
  "offsetTopicName": "offset-2c564b55cf",
  "imageName": "oharastream/connect-worker:0.6.1-SNAPSHOT",
  "groupId": "a5b623d114",
  "jarInfos": [],
  "statusTopicReplications": 1,
  "configTopicPartitions": 1,
  "offsetTopicReplications": 1,
  "configTopicReplications": 1,
  "statusTopicPartitions": 1,
  "configTopicName": "setting-68be0e46f7",
  "jmxPort": 8084,
  "clientPort": 8083,
  "nodeNames": [
    "node00"
  ],
  "deadNodes": []
}
```
----------
### add a new node to a running worker cluster

*PUT /v0/workers/$name/$nodeName*

If you want to extend a running worker cluster, you can add a node to share the heavy loading of a running worker cluster.
However, the balance is not triggered at once. By the way, moving a task to another idle node needs to **stop** task
first. Don't worry about the temporary lower throughput when balancer is running.

**Example Response**

```json
{
  "statusTopicName": "status-89eaef1e9d",
  "name": "wk00",
  "offsetTopicPartitions": 1,
  "brokerClusterName": "preCreatedBkCluster",
  "connectors": [],
  "offsetTopicName": "offset-956c528fa5",
  "imageName": "oharastream/connect-worker:0.6.1-SNAPSHOT",
  "groupId": "dcafb19d0e",
  "jarInfos": [],
  "statusTopicReplications": 1,
  "configTopicPartitions": 1,
  "offsetTopicReplications": 1,
  "configTopicReplications": 1,
  "statusTopicPartitions": 1,
  "configTopicName": "setting-67c528ca7d",
  "jmxPort": 8084,
  "clientPort": 8083,
  "nodeNames": [
    "node01",
    "node00"
  ],
  "deadNodes": []
}
```
----------
### remove a node from a running worker cluster

*DELETE /v0/workers/$name/$nodeName*

If your budget is limited, you can decrease the number of nodes running worker cluster. BUT, removing a node from a
running worker cluster invoke a lot of task move, and it will decrease the throughput of your connector.

**Example Response**

```
204 NoContent
```

> It is ok to delete an nonexistent worker node, and the response is 204 NoContent.

----------
## Validation

Notwithstanding we have read a lot of document and guideline, there is a chance to input incorrect request or settings
when operating ohara. Hence, ohara provides a serial APIs used to validate request/settings before you do use them
to start service. Noted that not all request/settings are validated by Ohara configurator. If the request/settings is
used by other system (for example, kafka), ohara automatically bypass the validation request to target system and then
wrap the result to JSON representation.

----------
### Validate the FTP connection

*PUT /v0/validate/ftp*

The parameters of request are shown below.
1. hostname (**string**) — ftp server hostname
1. port (**int**) — ftp server port
1. user (**string**) — account of ftp server
1. password (**string**) — password of ftp server
1. workerClusterName (**string**) — the target cluster used to validate this connection

**Example Request**

```json
{
  "hostname": "node00",
  "port": 22,
  "user": "user",
  "password": "pwd"
}
```

> Ohara picks up the single worker cluster directly when you ignore the element of worker cluster.

Since FTP connection is used by ftp connector only, ohara configurator involves several connectors to test the connection properties.
Ohara configurator collects report from each connectors and then generate a JSON response shown below.
1. hostname (**string**) — the node which execute this validation
1. message (**string**) — the description about this validation
1. pass (**boolean**) — true is pass

**Example Request**

```json
[
  {
    "hostname": "node00",
    "message": "succeed to connector to ftp server",
    "pass": true
  }
]
```
----------
### Validate the JDBC connection

*PUT /v0/validate/rdb*

The parameters of request are shown below.
1. url (**string**) — jdbc url
1. user (**string**) — account of db server
1. password (**string**) — password of db server
1. workerClusterName (**string**) — the target cluster used to validate this connection

**Example Response**

```json
{
  "url": "jdbc://",
  "user": "user",
  "password": "pwd",
  "tableNames": [
    "table0", "table1"
  ]
}
```

> Ohara picks up the single worker cluster directly when you ignore the element of worker cluster.

Since JDBC connection is used by jdbc connector only, ohara configurator involves several connectors to test the connection properties.
Ohara configurator collects report from each connectors and then generate a JSON response shown below.
1. hostname (**string**) — the node which execute this validation
1. message (**string**) — the description about this validation
1. pass (**boolean**) — true is pass
1. tableNames (**array(String)**) — the table readable to passed user/password

**Example Response**

```json
[
  {
    "hostname": "node00",
    "message": "succeed to connector to db server",
    "pass": true
  }
]
```
----------
### Validate the HDFS connection

*PUT /v0/validate/hdfs*

The parameters of request are shown below.
1. uri (**string**) — hdfs url
1. workerClusterName (**string**) — the target cluster used to validate this connection

**Example Request**

```json
{
  "uri": "file://"
}
```

> Ohara picks up the single worker cluster directly when you ignore the element of worker cluster.

Since HDFS connection is used by hdfs connector only, ohara configurator involves several connectors to test the connection properties.
Ohara configurator collects report from each connectors and then generate a JSON response shown below.
1. hostname (**string**) — the node which execute this validation
1. message (**string**) — the description about this validation
1. pass (**boolean**) — true is pass

**Example Response**

```json
[
  {
    "hostname": "node00",
    "message": "succeed to connector to hdfs server",
    "pass": true
  }
]
```
----------
### Validate the node connection

*PUT /v0/validate/node*

The parameters of request are shown below.
1. hostname (**string**) — hostname of node
1. port (**int**) — ssh port of node
1. user (**string**) — ssh account
1. password (**string**) — ssh password

**Example Request**

```json
{
  "hostname": "node00",
  "port": 22,
  "user": "abc",
  "password": "pwd"
}
```

Since Node connection is used by ohara configurator only, ohara configurator validates the connection by itself.
The format of report is same to other reports but the **hostname** is fill with **node's hostname** rather than node which
execute the validation.
1. hostname (**string**) — node's hostname
1. message (**string**) — the description about this validation
1. pass (**boolean**) — true is pass

**Example Response**

```json
[
  {
    "hostname": "node00",
    "message": "succeed to connector to ssh server",
    "pass": true
  }
]
```
----------
### Validate the connector settings

*PUT /v0/validate/connector*

Before starting a connector, you can send the settings to test whether all settings are available for specific connector.
Ohara is not in charge of settings validation. Connector MUST define its setting via [setting definitions](custom_connector.html#setting-definitions).
Ohara configurator only repackage the request to kafka format and then collect the validation result from kafka.

**Example Request**

The request format is same as [connector request](#create-the-settings-of-connector)

**Example Response**

If target connector has defined the settings correctly, kafka is doable to validate each setting of request. Ohara configurator
collect the result and then generate the following report.

```json
{
  "errorCount": 0,
  "settings": [
    {
      "definition": {
        "reference": "NONE",
        "displayName": "connector.class",
        "internal": false,
        "documentation": "the class name of connector",
        "valueType": "CLASS",
        "tableKeys": [],
        "orderInGroup": 0,
        "key": "connector.class",
        "required": true,
        "defaultValue": null,
        "group": "core",
        "editable": true
      },
      "setting": {
        "key": "connector.class",
        "value": "com.island.ohara.connector.perf",
        "errors": []
      }
    }
  ]
}
```

The above example only show a part of report. The element **definition** is equal to [connector's setting definition](#worker).
The definition is what connector must define. If you don't write any definitions for you connector, the validation will do nothing
for you. The element **setting** is what you request to validate.
1. key (**string**) — the property key. It is equal to key in **definition**
1. value (**string**) — the value you request to validate
1. errors (**array(string)**) — error message when the input value is illegal to connector 
----------
## Container

Each processes managed by ohara is based on docker container. In most cases, user don't need to know the details of containers
since the management of containers is on ohara's shoulder. However, ohara understand that we all have curious brain so ohara
supports to display the container's details of a running cluster. Noted that the context may be changed between different
release of ohara. And the distinct implementations of container manager possibly provide different context of containers.

----------
### retrieve the container details of a running cluster

*GET /v0/containers/$clusterName*

**Example Response**

The **cluster name** may be mapped to different services (of course, it would be better to avoid using same name on different services),
hence, the returned JSON is in array type. The details of elements are shown below.
1. clusterName (**string**) — cluster name
1. clusterType (**string**) —  cluster type
1. containers (**array(object)**) — the container in this cluster 
  - environments (**object**) — the environment variables of container
  - name (**string**) — the name of container
  - hostname (**string**) — hostname of container
  - size (**string**) — the disk size used by this container
  - state (**optional(string)**) — the state of container
  - portMappings (**array(object)**) —  the exported ports of this container
    - portMappings[i].hostIp (**string**) — the network interface of container host
    - portMappings[i].portPairs (**object**) — the container port and host port
      - portMappings[i].portPairs[j].hostPort (**int**) — host port
      - portMappings[i].portPairs[j].containerPort (**int**) — container port
  - nodeName (**string**) — the node which host this container
  - imageName (**string**) — the image used to create this container
  - id (**string**) — container id
  - created (**string**) — create time
```json
[
  {
    "clusterName": "zk00",
    "clusterType": "zookeeper",
    "containers": [
      {
        "environments": {
          "PATH": "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/home/zookeeper/default/bin",
          "ZK_ID": "0",
          "ZK_ELECTION_PORT": "3888",
          "JAVA_HOME": "/usr/lib/jvm/jre",
          "ZK_CLIENT_PORT": "2181",
          "ZK_SERVERS": "node00",
          "ZK_PEER_PORT": "2888",
          "ZOOKEEPER_HOME": "/home/zookeeper/default"
        },
        "name": "occl-zk00-zk-2aa11cc",
        "hostname": "node00",
        "size": "32.9kB (virtual 595MB)",
        "state": "RUNNING",
        "portMappings": [
          {
            "hostIp": "0.0.0.0",
            "portPairs": [
              {
                "hostPort": 2181,
                "containerPort": 2181
              },
              {
                "hostPort": 2888,
                "containerPort": 2888
              },
              {
                "hostPort": 3888,
                "containerPort": 3888
              }
            ]
          }
        ],
        "nodeName": "node00",
        "imageName": "oharastream/zookeeper:0.6.1-SNAPSHOT",
        "id": "22169c48646c",
        "kind": "SSH",
        "created": "2019-04-12 03:30:56 -0400 EDT"
      }
    ]
  }
]
```
----------
## StreamApp

Ohara StreamApp is a unparalleled wrap of kafka streaming. It leverages and enhances [kafka streams](https://kafka.apache.org/documentation/streams/) to make developer
easily design and implement the streaming application. More details of developing streaming application is in [custom stream guideline](custom_streamapp.md).

Assume that you have completed a streaming application via ohara Java APIs, and you have generated a jar including your streaming code.
By Ohara Restful APIs, you are enable to control, deploy, and monitor your streaming application.
As with cluster APIs, ohara leverages docker container to host streaming application. Of course, you can apply your favor  
container management tool including simple (based on ssh) and k8s when you are starting ohara.

Before stating to use restful APIs, please ensure that all nodes have downloaded the [StreamApp image](https://cloud.docker.com/u/oharastream/repository/docker/oharastream/streamapp).
The jar you uploaded to run streaming application will be included in the image and then executes as a docker container.
The [StreamApp image](https://cloud.docker.com/u/oharastream/repository/docker/oharastream/streamapp) is kept in each node so don't worry about the network. We all hate re-download everything when running services.

The following information of StreamApp are updated by ohara.
1. name (**string**) — custom name of this streamApp
1. imageName (**string**) — image name of this streamApp
1. instances ( **int**) — numbers of streamApp container
1. nodeNames (**array(string)**) — node list of streamApp running container
1. deadNodes (**array(string)**) — dead node list of the exited containers from this cluster
1. jar (**object**) — uploaded jar key
1. from (**array(string)**) — topics of streamApp consume with
1. to (**array(string)**) — topics of streamApp produce to
1. state (**option(string)**) — only started/failed streamApp has state
1. jmxPort (**int**) — the expose jmx port
1. [metrics](custom_connector.html#metrics) (**object**) — the metrics from this streamApp.
    - meters (**array(object)**) — the metrics in meter type
      - meters[i].value (**double**) — the number stored in meter
      - meters[i].unit (**string**) — unit for value
      - meters[i].document (**string**) — document of this meter
1. exactlyOnce (**boolean**) — enable exactly once
1. error (**option(string)**) — the error message from a failed streamApp. If the streamApp is fine or un-started, you won't get this field.
1. lastModified (**long**) — last modified this jar time
----------
### start a StreamApp

*PUT /v0/stream/${name}/start*

**Example Response**

```json
{
  "name": "myApp",
  "imageName": "oharastream/streamapp:0.6.1-SNAPSHOT",
  "instances": 1,
  "nodeNames": ["node1"],
  "deadNodes": [],
  "jar": {
    "name": "streamapp",
    "group": "wk01"
  },
  "from": [
    "topicA"
  ],
  "to": [
    "topicB"
  ],
  "state": "RUNNING",
  "jmxPort": 5678,
  "exactlyOnce": "false",
  "metrics": [],
  "lastModified": 1542102595892
}
```
----------
### stop a StreamApp

This action will graceful stop and remove all docker containers belong to this streamApp.
Note: successful stop streamApp will have no status.

*PUT /v0/stream/${name}/stop*

**Example Response**

```json
{
  "name": "myApp",
  "imageName": "oharastream/streamapp:0.6.1-SNAPSHOT",
  "instances": 1,
  "nodeNames": ["node1"],
  "deadNodes": [],
  "jar": {
    "name": "streamapp",
    "group": "wk01"
  },
  "from": [
    "topicA"
  ],
  "to": [
    "topicB"
  ],
  "jmxPort": 5678,
  "exactlyOnce": "false",
  "metrics": [],
  "lastModified": 1542102595892
}
```
----------
### create properties of specific streamApp

Create the properties of a streamApp.

*POST /v0/stream/property*

**Example Request**

1. name (**string**) — new streamApp name. This is the object unique name.
1. imageName (**option(string)**) — image name of streamApp used to ; default is official streamapp image of current version
1. jar (**object**) — the used jar object
    - jar.group (**string**) — the group name of this jar
    - jar.name (**string**) — the name without extension of this jar
1. from (**option(array(string))**) — new source topics ; default is empty
1. to (**option(array(string))**) — new target topics ; default is empty
1. jmxPort (**option(int)**) — expose port for jmx ; default is random port
1. instances (**option(int)**) — number of running streamApp ; default is 1
1. nodeNames (**option(array(string))**) — node name list of streamApp used to ; default is empty

```json
{
  "name": "myApp",
  "imageName": "oharastream/streamapp:0.6.1-SNAPSHOT",
  "jar": {
    "group": "wk01",
    "name": "stream-app"
  },
  "from": [
    "topic1"
  ],
  "to": [
    "topic2"
  ],
  "jmxPort": 5678,
  "instances": 3,
  "nodeNames": []
}
```

**Example Response**

1. name (**string**) — custom name of this streamApp
1. imageName (**string**) — image name of this streamApp
1. instances ( **int**) — numbers of streamApp container
1. nodeNames (**array(string)**) — node list of streamApp running container
1. deadNodes (**array(string)**) — dead node list of the exited containers from this cluster
1. jar (**object**) — uploaded jar key
1. from (**array(string)**) — topics of streamApp consume with
1. to (**array(string)**) — topics of streamApp produce to
1. state (**option(string)**) — only started/failed streamApp has state
1. jmxPort (**int**) — the expose jmx port
1. [metrics](custom_connector.html#metrics) (**object**) — the metrics from this streamApp.
    - meters (**array(object)**) — the metrics in meter type
      - meters[i].value (**double**) — the number stored in meter
      - meters[i].unit (**string**) — unit for value
      - meters[i].document (**string**) — document of this meter
1. exactlyOnce (**boolean**) — enable exactly once
1. error (**option(string)**) — the error message from a failed streamApp. If the streamApp is fine or un-started, you won't get this field.
1. lastModified (**long**) — last modified this jar time

```json
{
  "name": "myApp",
  "imageName": "oharastream/streamapp:0.6.1-SNAPSHOT",
  "instances": 3,
  "nodeNames": [],
  "deadNodes": [],
  "jar": {
    "name": "stream-app",
    "group": "wk01"
  },
  "from": [
    "topic1"
  ],
  "to": [
    "topic2"
  ],
  "jmxPort": 5678,
  "exactlyOnce": "false",
  "metrics": [],
  "lastModified": 1542102595892
}
```

> The streamApp, which is just created, does not have any metrics. 

----------
### get information from a specific streamApp cluster

*GET /v0/stream/property/${name}*

**Example Response**

1. name (**string**) — custom name of this streamApp
1. imageName (**string**) — image name of this streamApp
1. instances ( **int**) — numbers of streamApp container
1. nodeNames (**array(string)**) — node list of streamApp running container
1. deadNodes (**array(string)**) — dead node list of the exited containers from this cluster
1. jar (**object**) — uploaded jar key
1. from (**array(string)**) — topics of streamApp consume with
1. to (**array(string)**) — topics of streamApp produce to
1. state (**option(string)**) — only started/failed streamApp has state
1. jmxPort (**int**) — the expose jmx port
1. [metrics](custom_connector.html#metrics) (**object**) — the metrics from this streamApp.
    - meters (**array(object)**) — the metrics in meter type
      - meters[i].value (**double**) — the number stored in meter
      - meters[i].unit (**string**) — unit for value
      - meters[i].document (**string**) — document of this meter
1. exactlyOnce (**boolean**) — enable exactly once
1. error (**option(string)**) — the error message from a failed streamApp. If the streamApp is fine or un-started, you won't get this field.
1. lastModified (**long**) — last modified this jar time

```json
{
  "name": "myApp",
  "imageName": "oharastream/streamapp:0.6.1-SNAPSHOT",
  "instances": 3,
  "nodeNames": [],
  "deadNodes": [],
  "jar": {
    "name": "stream-app",
    "group": "wk01"
  },
  "from": [
    "topic1"
  ],
  "to": [
    "topic2"
  ],
  "jmxPort": 5678,
  "exactlyOnce": "false",
  "metrics": [],
  "lastModified": 1542102595892
}
```
----------
### update properties of specific streamApp

Update the properties of a non-started streamApp. 

*PUT /v0/stream/property/${name}*

**Example Request**

1. imageName (**option(string)**) — new streamApp image name
1. from (**option(array(string))**) — new source topics
1. to (**option(array(string))**) — new target topics
1. jar (**option(object)**) — new uploaded jar key
1. jmxPort (**option(int)**) — new jmx port
1. instances (**option(int)**) — new number of running streamApp
1. nodeNames (**option(array(string))**) — new node name list of streamApp used to (this field has higher priority than instances)

```json
{
  "imageName": "myimage",
  "from": [
    "newTopic1"
  ],
  "to": [
    "newTopic2"
  ],
  "jar": {
    "group": "newGroup",
    "name": "newJar"
  },
  "jmxPort": 8888,
  "instances": 3,
  "nodeNames": ["node1", "node2"]
}
```

**Example Response**

1. name (**string**) — custom name of this streamApp
1. imageName (**string**) — image name of this streamApp
1. instances ( **int**) — numbers of streamApp container
1. nodeNames (**array(string)**) — node list of streamApp running container
1. deadNodes (**array(string)**) — dead node list of the exited containers from this cluster
1. jar (**object**) — uploaded jar key
1. from (**array(string)**) — topics of streamApp consume with
1. to (**array(string)**) — topics of streamApp produce to
1. state (**option(string)**) — only started/failed streamApp has state
1. jmxPort (**int**) — the expose jmx port
1. [metrics](custom_connector.html#metrics) (**object**) — the metrics from this streamApp.
    - meters (**array(object)**) — the metrics in meter type
      - meters[i].value (**double**) — the number stored in meter
      - meters[i].unit (**string**) — unit for value
      - meters[i].document (**string**) — document of this meter
1. exactlyOnce (**boolean**) — enable exactly once
1. error (**option(string)**) — the error message from a failed streamApp. If the streamApp is fine or un-started, you won't get this field.
1. lastModified (**long**) — last modified this jar time

```json
{
  "name": "myApp",
  "imageName": "myimage",
  "instances": 2,
  "nodeNames": ["node1", "node2"],
  "deadNodes": [],
  "jar": {
    "name": "stream-app",
    "group": "wk01"
  },
  "from": [
    "newTopic1"
  ],
  "to": [
    "newTopic2"
  ],
  "jmxPort": 8888,
  "exactlyOnce": "false",
  "metrics": [],
  "lastModified": 1542102595892
}
```
----------
### delete properties of specific streamApp

Delete the properties of a non-started streamApp.
This api only remove the streamApp component which is stored in pipeline. 

*DELETE /v0/stream/property/${name}*

**Example Response**

```
204 NoContent
```

> It is ok to delete an nonexistent properties, and the response is 204 NoContent.

----------
### get topology tree graph from specific streamApp

*GET /v0/stream/view/${name}*

**Example Response**

1. jarInfo (**string**) — the upload jar information
1. name (**string**) — the streamApp name
1. poneglyph (**object**) — the streamApp topology tree graph
  - steles (**array(object)**) — the topology collection
    - steles[i].kind (**string**) — this component kind (SOURCE, PROCESSOR, or SINK)
    - steles[i].key (**string**) — this component kind with order
    - steles[i].name (**string**) — depend on kind, the name is 
        - SOURCE — source topic name
        - PROCESSOR — the function name
        - SINK — target topic name
    - steles[i].from (**string**) — the prior component key (could be empty if this is the first component)
    - steles[i].to (**string**) — the posterior component key (could be empty if this is the final component)

```json
{
  "id": "d312871a-4a05-488d-aae0-c8b27c5312c2",
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
```
----------
## Jars

Ohara encourages user to write custom application if the official applications can satisfy requirements for your use case.
Jar APIs is a useful entry of putting your jar on ohara and then start related services with it. For example, [Worker APIs](#create-a-worker-cluster)
accept a **jars** element which can carry the jar name pointing to a existent jar in ohara. The worker cluster will load all
connectors of the input jar, and then you are able to use the connectors on the worker cluster.

The properties stored by ohara are shown below.
1. name (**string**) — the file name without extension
1. group (**string**) — the group name (we use this field to separate different workspaces)
1. size (**long**) — file size
1. url (**option(string)**) — url to download this jar from Ohara Configurator. Noted not all jars are downloadable to user.
1. lastModified (**long**) — the time of uploading this file

----------

### upload a jar to ohara

Upload a jar file to ohara with field name : "jar" and group name : "group"
the text field "group" could be empty and we will generate a random string.

*POST /v0/jars*

**Example Request**

```http
Content-Type: multipart/form-data
jar="aa.jar"
group="wk01"
```

You have to specify the file name since it is a part of metadata stored by ohara.

**Example Response**
```json
{
  "name": "aa.jar",
  "group": "wk01",
  "size": 1779,
  "url": "http://localhost:12345/v0/downloadJars/aa.jar",
  "lastModified": 1561012496975
}
```

----------

### list all jars

Get all jars from specific group of query parameter.
If no query parameter, wll return all jars.

*GET /v0/jars?group=wk01*

**Example Response**
```json
[
  {
    "name": "aa.jar",
    "group": "wk01",
    "size": 1779,
    "url": "http://localhost:12345/v0/downloadJars/aa.jar",
    "lastModified": 1561012496975
  }
]
```
----------

### delete a jar

Delete a jar with specific name and group.
Note: the query parameter must exists.

*DELETE /v0/jars/$name?group=wk01*

**Example Response**

```
204 NoContent
```

> It is ok to delete an nonexistent jar, and the response is 204 NoContent. If you delete a jar is used by
  other services, you also break the scalability of service as you can't run the jar on any new nodes
  
----------

### get a jar

Get a jar with specific name and group.
Note: the query parameter must exists.

*GET /v0/jars/$name?group=wk01*

**Example Response**
```json
{
    "name": "aa.jar",
    "group": "wk01",
    "size": 1779,
    "url": "http://localhost:12345/v0/downloadJars/aa.jar",
    "lastModified": 1561012496975
  }
```

----------

## Logs

This world is beautiful but not safe. Even though ohara shoulders the blame for simplifying your life, there is a
slim chance that something don't work well in ohara. The Logs APIs, which are engineers' best friend, open a door
to observe the logs of running cluster.

It collect output from all containers' of a cluster and then format them to JSON representation which has following elements.
1. name (**string**) — cluster name
1. logs (**array(object)**) — log of each container
  - logs[i].name — container's name
  - logs[i].value — total output of a container
 
----------
### get the log of a running cluster

*GET /v0/logs/$clusterType/$clusterName*

- clusterType (**string**)
  - zookeepers
  - brokers
  - workers

**Example Response**

```json
{
  "name": "precreatezkcluster",
  "logs": [
    {
      "name": "node00",
      "value": "2019-04-15 02:13:33,168 [myid:] - INFO [main:QuorumPeerConfig@136"
    }
  ]
}
```

----------

## Query

Query APIs is a collection of helper methods required by Ohara Manager so you should assume this APIs are **private**
and we do not guarantee compatibility to this APIs. Normally, Ohara Configurator can't run the query for you since
most queries demand specific dependencies in runtime, and we don't allow you to touch the classpath of Ohara Configurator.
Hence, Ohara Configurator pass the queries to official specific **connectors** to execute the queries on a [worker cluster](#worker).
It implies that you should set up a [worker cluster](#worker) before submitting query request to Ohara Configurator.

----------

### Query Database

*POST /v0/query/rdb*

This API returns the table details of a relational database. This API invokes a running connector on worker cluster to
fetch database information and return to Ohara Configurator. You should deploy suitable jdbc driver on worker cluster
before using this API. Otherwise, you will get a exception returned by Ohara Configurator. The query consists of following fields.

1. url (**string**) — jdbc url
1. user (**string**) — user who can access target database
1. password (**string**) — password which can access target database
1. workerClusterName (**string**) — used to execute connectors to fetch table information
1. catalogPattern (**option(string)**) — filter returned tables according to catalog
1. schemaPattern (**option(string)**) — filter returned tables according to schema
1. tableName (**option(string)**) — filter returned tables according to name

**Example Request**

```json
{
  "url": "jdbc:sqlserver://",
  "user": "abc",
  "password": "abc",
  "workerClusterName": "wk00"
}
```

**Example Response**

1. name (**string**) — database name
1. tables (**array(object)**)
  - tables[i].catalogPattern (**option(object)**) — table's catalog pattern
  - tables[i].schemaPattern (**option(object)**) — table's schema pattern
  - tables[i].name (**option(object)**) — table's name
  - tables[i].columns (**array(object)**) — table's columns
    - tables[i].columns[j].name (**string**) — column's columns
    - tables[i].columns[j].dataType (**string**) — column's data type
    - tables[i].columns[j].pk (**boolean**) — true if this column is pk. otherwise false
  
```json
{
  "name": "sqlserver",
  "tables": [
    {
      "name": "t0",
      "columns": [
        {
          "name": "c0",
          "dataType": "integer",
          "pk": true
        }
      ]
    }
  ]
}
```

----------

## Info

Info API returns the information of Ohara Configurator you are executing. It consists of following fields.
1. versionInfo (**object**) — version details of Ohara Configurator
    - version (**string**) — the release version of Ohara Configurator
    - revision (**string**) — commit hash of Ohara Configurator. You can trace the hash code via [Github](https://github.com/oharastream/ohara/commits/master)
    - user (**string**) — the release manager of Ohara Configurator.
    - date (**string**) — the date of releasing Ohara Configurator.
1. mode (**string**) — the mode of this configurator. There are three modes now. First, k8s mode is for the production.
                     Second, ssh is useful to simple env. Third, fake mode is used to test APIs.
    
**Example Response**
    
 ```json
{
  "versionInfo": {
    "version": "0.6.1-SNAPSHOT",
    "user": "chia",
    "revision": "b86742ca03a0ca02cc3578f8686e38e5cf2fb461",
    "date": "2019-05-13 09:59:38"
  },
  "mode": "ssh"
}
```

----------
