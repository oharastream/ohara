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

package oharastream.ohara.client.configurator.v0

import oharastream.ohara.client.configurator.v0.MetricsApi.Metrics
import oharastream.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import oharastream.ohara.common.rule.OharaTest
import oharastream.ohara.common.setting.SettingDef.Permission
import oharastream.ohara.common.setting.{ObjectKey, SettingDef, TopicKey}
import oharastream.ohara.common.util.{CommonUtils, VersionUtils}
import oharastream.ohara.stream.config.StreamDefUtils
import org.junit.Test
import org.scalatest.Matchers._
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TestStreamApi extends OharaTest {
  private[this] final val accessRequest =
    StreamApi.access.hostname(CommonUtils.randomString(5)).port(CommonUtils.availablePort()).request
  private[this] final val fakeJar = ObjectKey.of(CommonUtils.randomString(1), CommonUtils.randomString(1))

  private[this] final def result[T](f: Future[T]): T = Await.result(f, 10 seconds)

  private[this] def topicKey(): TopicKey             = topicKey(CommonUtils.randomString())
  private[this] def topicKey(name: String): TopicKey = TopicKey.of(GROUP_DEFAULT, name)

  @Test
  def checkVersion(): Unit = {
    StreamApi.IMAGE_NAME_DEFAULT shouldBe s"oharastream/stream:${VersionUtils.VERSION}"
  }

  @Test
  def testClone(): Unit = {
    val nodeNames = Set(CommonUtils.randomString())
    val streamClusterInfo = StreamClusterInfo(
      settings = StreamApi.access.request
        .jarKey(fakeJar)
        .nodeNames(Set(CommonUtils.randomString()))
        .fromTopicKey(topicKey(CommonUtils.randomString()))
        .toTopicKey(topicKey(CommonUtils.randomString()))
        .brokerClusterKey(ObjectKey.of("group", "n"))
        .creation
        .settings,
      aliveNodes = Set.empty,
      state = None,
      error = None,
      metrics = Metrics.EMPTY,
      lastModified = CommonUtils.current()
    )
    streamClusterInfo.newNodeNames(nodeNames).nodeNames shouldBe nodeNames
  }

  @Test
  def nameFieldCheck(): Unit = {
    an[NullPointerException] should be thrownBy accessRequest.name(null)
    an[IllegalArgumentException] should be thrownBy accessRequest.name("")
  }

  @Test
  def groupFieldCheck(): Unit = {
    an[NullPointerException] should be thrownBy accessRequest.name(null)
    an[IllegalArgumentException] should be thrownBy accessRequest.name("")
  }

  @Test
  def imageNameFieldCheck(): Unit = {
    // default value
    accessRequest
      .name(CommonUtils.randomString(5))
      .fromTopicKey(topicKey(CommonUtils.randomString()))
      .toTopicKey(topicKey(CommonUtils.randomString()))
      .jarKey(fakeJar)
      .nodeName(CommonUtils.randomString(10))
      .brokerClusterKey(ObjectKey.of("group", "n"))
      .creation
      .imageName shouldBe StreamApi.IMAGE_NAME_DEFAULT
  }

  @Test
  def topicFromFieldCheck(): Unit = {
    an[NullPointerException] should be thrownBy accessRequest.fromTopicKeys(null)

    // default from field will be empty
    accessRequest
      .name(CommonUtils.randomString(5))
      .jarKey(fakeJar)
      .fromTopicKey(topicKey(CommonUtils.randomString()))
      .toTopicKey(topicKey(CommonUtils.randomString()))
      .brokerClusterKey(ObjectKey.of("group", "n"))
      .nodeName("node")
      .creation
      .fromTopicKeys should not be Set.empty
  }

  @Test
  def topicToFieldCheck(): Unit = {
    an[NullPointerException] should be thrownBy accessRequest.toTopicKeys(null)

    // default to field will be empty
    accessRequest
      .name(CommonUtils.randomString(5))
      .fromTopicKey(topicKey(CommonUtils.randomString()))
      .toTopicKey(topicKey(CommonUtils.randomString()))
      .brokerClusterKey(ObjectKey.of("group", "n"))
      .jarKey(fakeJar)
      .nodeName("node")
      .creation
      .toTopicKeys should not be Set.empty
  }

  @Test
  def jmxPortFieldCheck(): Unit = {
    an[IllegalArgumentException] should be thrownBy accessRequest.jmxPort(0)
    an[IllegalArgumentException] should be thrownBy accessRequest.jmxPort(-1)

    // default value
    CommonUtils.requireConnectionPort(
      accessRequest
        .jarKey(fakeJar)
        .name(CommonUtils.randomString(5))
        .nodeName(CommonUtils.randomString(10))
        .fromTopicKey(topicKey(CommonUtils.randomString()))
        .toTopicKey(topicKey(CommonUtils.randomString()))
        .brokerClusterKey(ObjectKey.of("group", "n"))
        .creation
        .jmxPort
    )
  }

  @Test
  def nodeNamesFieldCheck(): Unit = {
    an[NullPointerException] should be thrownBy accessRequest.nodeNames(null)
    // empty node names is legal to stream
    accessRequest.nodeNames(Set.empty)

    // default value
    accessRequest
      .name(CommonUtils.randomString(5))
      .jarKey(fakeJar)
      .nodeName(CommonUtils.randomString(10))
      .fromTopicKey(topicKey(CommonUtils.randomString()))
      .toTopicKey(topicKey(CommonUtils.randomString()))
      .brokerClusterKey(ObjectKey.of("group", "n"))
      .creation
      .nodeNames should not be Set.empty
  }

  @Test
  def brokerClusterKeyFieldCheck(): Unit = {
    an[NullPointerException] should be thrownBy accessRequest.brokerClusterKey(null)
  }

  @Test
  def requireFieldOnPropertyCreation(): Unit = {
    // jarKey is required
    an[DeserializationException] should be thrownBy accessRequest
      .name(CommonUtils.randomString(5))
      .nodeName(CommonUtils.randomString(10))
      .creation
  }

  @Test
  def testMinimumCreation(): Unit = {
    val creationApi = accessRequest
      .jarKey(fakeJar)
      .fromTopicKey(topicKey(CommonUtils.randomString()))
      .toTopicKey(topicKey(CommonUtils.randomString()))
      .brokerClusterKey(ObjectKey.of("group", "n"))
      .nodeName(CommonUtils.randomString(10))
      .creation

    creationApi.name.nonEmpty shouldBe true
    creationApi.group shouldBe GROUP_DEFAULT
    creationApi.imageName shouldBe StreamApi.IMAGE_NAME_DEFAULT
    creationApi.jarKey shouldBe fakeJar
    creationApi.fromTopicKeys should not be Set.empty
    creationApi.toTopicKeys should not be Set.empty
    creationApi.jmxPort should not be 0
    creationApi.nodeNames should not be Set.empty
    creationApi.tags shouldBe Map.empty

    val creationJson = StreamApi.CREATION_JSON_FORMAT.read(s"""
                                                  |  {
                                                  |    "jarKey": ${fakeJar.toJson},
                                                  |    "from": [
                                                  |      {
                                                  |        "group": "g",
                                                  |        "name": "g"
                                                  |      }
                                                  |    ],
                                                  |    "to": [
                                                  |      {
                                                  |        "group": "n",
                                                  |        "name": "n"
                                                  |      }
                                                  |    ],
                                                  |    "nodeNames": ["nn"],
                                                  |    "jarKey": ${fakeJar.toJson},
                                                  |    "brokerClusterKey": {
                                                  |      "group": "g",
                                                  |      "name": "n"
                                                  |    }
                                                  |  }
     """.stripMargin.parseJson)
    creationJson.name.nonEmpty shouldBe true
    creationJson.group shouldBe GROUP_DEFAULT
    creationJson.imageName shouldBe StreamApi.IMAGE_NAME_DEFAULT
    creationJson.jmxPort should not be 0
    creationJson.nodeNames should not be Set.empty
    creationJson.tags shouldBe Map.empty

    creationApi.settings.keys.size shouldBe creationJson.settings.keys.size
  }

  @Test
  def testCreation(): Unit = {
    val name      = CommonUtils.randomString(10)
    val from      = topicKey()
    val to        = topicKey()
    val jmxPort   = CommonUtils.availablePort()
    val nodeNames = Set(CommonUtils.randomString())
    val creation = accessRequest
      .name(name)
      .jarKey(fakeJar)
      .fromTopicKey(from)
      .toTopicKey(to)
      .jmxPort(jmxPort)
      .nodeNames(nodeNames)
      .brokerClusterKey(ObjectKey.of("group", "n"))
      .creation

    creation.name shouldBe name
    creation.jarKey shouldBe fakeJar
    creation.fromTopicKeys shouldBe Set(from)
    creation.toTopicKeys shouldBe Set(to)
    creation.jmxPort shouldBe jmxPort
    creation.nodeNames shouldBe nodeNames
  }

  @Test
  def testExtraSettingInCreation(): Unit = {
    val name  = CommonUtils.randomString(10)
    val name2 = JsString(CommonUtils.randomString(10))
    val creation = accessRequest
      .name(name)
      .jarKey(fakeJar)
      .settings(Map("name" -> name2))
      .fromTopicKey(topicKey(CommonUtils.randomString()))
      .toTopicKey(topicKey(CommonUtils.randomString()))
      .brokerClusterKey(ObjectKey.of("group", "n"))
      .nodeName("node")
      .creation

    // settings() has higher priority than name()
    creation.name shouldBe name2.value
  }

  @Test
  def parseCreation(): Unit = {
    val from     = topicKey()
    val to       = topicKey()
    val nodeName = "n0"
    val creation = StreamApi.CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "from": [
      |      {
      |        "group": "${from.group()}",
      |        "name": "${from.name()}"
      |      }
      |    ],
      |    "to": [
      |      {
      |        "group": "${to.group()}",
      |        "name": "${to.name()}"
      |      }
      |    ],
      |    "nodeNames": ["$nodeName"],
      |    "jarKey": ${fakeJar.toJson},
      |    "brokerClusterKey": {
      |      "group": "g",
      |      "name": "n"
      |    }
      |  }
      |  """.stripMargin.parseJson)
    creation.name.length shouldBe SettingDef.STRING_LENGTH_LIMIT
    creation.group shouldBe GROUP_DEFAULT
    creation.imageName shouldBe StreamApi.IMAGE_NAME_DEFAULT
    creation.jarKey shouldBe fakeJar
    creation.fromTopicKeys shouldBe Set(from)
    creation.toTopicKeys shouldBe Set(to)
    creation.jmxPort should not be 0
    creation.nodeNames shouldBe Set(nodeName)

    val name      = CommonUtils.randomString(10)
    val creation2 = StreamApi.CREATION_JSON_FORMAT.read(s"""
       |  {
       |    "name": "$name",
       |    "from": [
       |      {
       |        "group": "${from.group()}",
       |        "name": "${from.name()}"
       |      }
       |    ],
       |    "to": [
       |      {
       |        "group": "${to.group()}",
       |        "name": "${to.name()}"
       |      }
       |    ],
       |    "nodeNames": ["$nodeName"],
       |    "jarKey": ${fakeJar.toJson},
       |    "brokerClusterKey": {
       |      "group": "g",
       |      "name": "n"
       |    }
       |  }
       |  """.stripMargin.parseJson)
    creation2.name shouldBe name
    creation2.imageName shouldBe StreamApi.IMAGE_NAME_DEFAULT
    creation2.jarKey shouldBe fakeJar
    creation.fromTopicKeys shouldBe Set(from)
    creation.toTopicKeys shouldBe Set(to)
    creation2.jmxPort should not be 0
    creation.nodeNames shouldBe Set(nodeName)
  }

  @Test
  def testDefaultName(): Unit = StreamApi.CREATION_JSON_FORMAT.read(s"""
       |  {
       |    "jarKey": ${fakeJar.toJson},
       |    "nodeNames": ["n"],
       |    "brokerClusterKey": {
       |      "group": "g",
       |      "name": "n"
       |    },
       |    "from": [
       |      {
       |        "group": "g",
       |        "name": "n"
       |      }
       |    ],
       |    "to": [
       |      {
       |        "group": "g",
       |        "name": "n"
       |      }
       |    ]
       |  }
     """.stripMargin.parseJson).name.nonEmpty shouldBe true

  @Test
  def parseNameField(): Unit =
    intercept[DeserializationException] {
      StreamApi.CREATION_JSON_FORMAT.read(s"""
           |  {
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "name": "",
           |    "nodeNames": ["n"],
           |    "brokerClusterKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "from": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ],
           |    "to": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("the value of \"name\" can't be empty string")

  @Test
  def parseGroupField(): Unit =
    intercept[DeserializationException] {
      StreamApi.CREATION_JSON_FORMAT.read(s"""
           |  {
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "group": "",
           |    "nodeNames": ["n"],
           |    "brokerClusterKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "from": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ],
           |    "to": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("the value of \"group\" can't be empty string")

  @Test
  def parseImageNameField(): Unit =
    StreamApi.CREATION_JSON_FORMAT
      .read(s"""
                                                  |  {
                                                  |    "jarKey": {
                                                  |      "group": "g",
                                                  |      "name": "n"
                                                  |    },
                                                  |    "imageName": "",
                                                  |    "nodeNames": ["n"],
                                                  |    "brokerClusterKey": {
                                                  |      "group": "g",
                                                  |      "name": "n"
                                                  |    },
                                                  |    "from": [],
                                                  |    "to": []
                                                  |  }
                                                  |  """.stripMargin.parseJson)
      .settings(IMAGE_NAME_KEY)
      .convertTo[String] shouldBe StreamApi.IMAGE_NAME_DEFAULT

  @Test
  def parseJarKeyField(): Unit = {
    val name = CommonUtils.randomString(10)

    // no jarKey is OK
    StreamApi.CREATION_JSON_FORMAT.read(s"""
      |  {
      |    "name": "$name",
      |    "jarKey": ${fakeJar.toJson},
      |    "nodeNames": ["n"],
      |    "brokerClusterKey": {
      |      "group": "g",
      |      "name": "n"
      |    },
      |    "from": [
      |      {
      |        "group": "g",
      |        "name": "n"
      |      }
      |    ],
      |    "to": [
      |      {
      |        "group": "g",
      |        "name": "n"
      |      }
      |    ]
      |  }
      |  """.stripMargin.parseJson)

    intercept[DeserializationException] {
      StreamApi.CREATION_JSON_FORMAT.read(s"""
           |  {
           |    "jarKey": "",
           |    "nodeNames": ["n"],
           |    "brokerClusterKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "from": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ],
           |    "to": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("empty string")
  }

  @Test
  def parseJmxPortField(): Unit = {
    // zero port
    intercept[DeserializationException] {
      StreamApi.CREATION_JSON_FORMAT.read(s"""
           |  {
           |    "name": "${CommonUtils.randomString(10)}",
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "jmxPort": 0,
           |    "nodeNames": ["n"],
           |    "brokerClusterKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "from": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ],
           |    "to": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("the number must be")

    // negative port
    intercept[DeserializationException] {
      StreamApi.CREATION_JSON_FORMAT.read(s"""
           |  {
           |    "name": "${CommonUtils.randomString(10)}",
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "jmxPort": -99,
           |    "nodeNames": ["n"],
           |    "brokerClusterKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "from": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ],
           |    "to": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("the number must be")

    // not connection port
    intercept[DeserializationException] {
      StreamApi.CREATION_JSON_FORMAT.read(s"""
           |  {
           |    "name": "${CommonUtils.randomString(10)}",
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "jmxPort": 999999,
           |    "nodeNames": ["n"],
           |    "brokerClusterKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "from": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ],
           |    "to": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("the number must be")
  }

  @Test
  def requireFieldOnPropertyUpdate(): Unit = {
    // name is required
    an[NoSuchElementException] should be thrownBy result(accessRequest.jarKey(fakeJar).update())

    // no jar is ok
    accessRequest.name(CommonUtils.randomString(5)).updating
  }

  @Test
  def testDefaultUpdate(): Unit = {
    val name = CommonUtils.randomString(10)
    val data = accessRequest.name(name).updating
    data.settings.contains(StreamDefUtils.IMAGE_NAME_DEFINITION.key()) shouldBe false
    data.settings.contains(StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key()) shouldBe false
    data.settings.contains(StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key()) shouldBe false
    data.settings.contains(StreamDefUtils.JMX_PORT_DEFINITION.key()) shouldBe false
    data.settings.contains(StreamDefUtils.NODE_NAMES_DEFINITION.key()) shouldBe false
  }

  @Test
  def parseImageNameFieldOnUpdate(): Unit =
    intercept[DeserializationException] {
      StreamApi.UPDATING_JSON_FORMAT.read(s"""
           |  {
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "imageName": "",
           |    "nodeNames": ["n"],
           |    "brokerClusterKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "from": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ],
           |    "to": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("the value of \"imageName\" can't be empty string")

  @Test
  def parseFromFieldOnCreation(): Unit =
    intercept[DeserializationException] {
      StreamApi.CREATION_JSON_FORMAT.read(s"""
           |  {
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "nodeNames": [
           |      "n"
           |    ],
           |    "brokerClusterKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "from": [""],
           |    "to": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("non-empty string")

  @Test
  def parseFromFieldOnUpdate(): Unit =
    intercept[DeserializationException] {
      StreamApi.UPDATING_JSON_FORMAT.read(s"""
           |  {
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "from": [""]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("the value of \"from\" can't be empty string")

  @Test
  def parseToFieldOnCreation(): Unit =
    intercept[DeserializationException] {
      StreamApi.CREATION_JSON_FORMAT.read(s"""
           |  {
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "to": [""],
           |    "nodeNames": [
           |      "n"
           |    ],
           |    "brokerClusterKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "from": [""]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("non-empty string")

  @Test
  def parseToFieldOnUpdate(): Unit =
    intercept[DeserializationException] {
      StreamApi.UPDATING_JSON_FORMAT.read(s"""
           |  {
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "to": [""]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("the value of \"to\" can't be empty string")

  @Test
  def parseNodeNamesFieldOnCreation(): Unit =
    intercept[DeserializationException] {
      StreamApi.CREATION_JSON_FORMAT.read(s"""
           |  {
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "nodeNames": "",
           |    "brokerClusterKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "from": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ],
           |    "to": [
           |      {
           |        "group": "g",
           |        "name": "n"
           |      }
           |    ]
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("the value of \"nodeNames\" can't be empty string")

  @Test
  def parseNodeNamesFieldOnUpdate(): Unit = {
    intercept[DeserializationException] {
      StreamApi.UPDATING_JSON_FORMAT.read(s"""
           |  {
           |    "jarKey": {
           |      "group": "g",
           |      "name": "n"
           |    },
           |    "nodeNames": ""
           |  }
           |  """.stripMargin.parseJson)
    }.getMessage should include("the value of \"nodeNames\" can't be empty string")

    // pass
    StreamApi.UPDATING_JSON_FORMAT.read(s"""
                                           |  {
                                           |    "jarKey": {
                                           |      "group": "g",
                                           |      "name": "n"
                                           |    },
                                           |    "nodeNames": []
                                           |  }
                                           |  """.stripMargin.parseJson).nodeNames shouldBe Some(Set.empty)
  }

  @Test
  def ignoreNameOnCreation(): Unit =
    accessRequest
      .fromTopicKey(topicKey(CommonUtils.randomString()))
      .toTopicKey(topicKey(CommonUtils.randomString()))
      .jarKey(fakeJar)
      .nodeName(CommonUtils.randomString(10))
      .brokerClusterKey(ObjectKey.of("group", "n"))
      .creation
      .name
      .length should not be 0

  @Test
  def groupShouldAppearInResponse(): Unit = {
    val name = CommonUtils.randomString(5)
    val res = StreamApi.STREAM_CLUSTER_INFO_FORMAT.write(
      StreamClusterInfo(
        settings = StreamApi.access.request
          .fromTopicKey(topicKey(CommonUtils.randomString()))
          .toTopicKey(topicKey(CommonUtils.randomString()))
          .brokerClusterKey(ObjectKey.of("group", "n"))
          .jarKey(fakeJar)
          .name(name)
          .nodeName(CommonUtils.randomString(10))
          .creation
          .settings,
        aliveNodes = Set.empty,
        state = None,
        error = None,
        metrics = Metrics.EMPTY,
        lastModified = CommonUtils.current()
      )
    )
    // serialize to json should see the object key (group, name) in "settings"
    res.asJsObject.fields(NAME_KEY).convertTo[String] shouldBe name
    res.asJsObject.fields(GROUP_KEY).convertTo[String] shouldBe GROUP_DEFAULT
  }

  @Test
  def testTagsOnUpdate(): Unit = accessRequest.updating.tags shouldBe None

  @Test
  def testOverwriteSettings(): Unit = {
    val fromTopicKey = topicKey(CommonUtils.randomString())
    val toTopicKey   = topicKey(CommonUtils.randomString())
    val r1 =
      accessRequest
        .fromTopicKey(fromTopicKey)
        .toTopicKey(toTopicKey)
        .jarKey(fakeJar)
        .brokerClusterKey(ObjectKey.of("group", "n"))
        .nodeName(CommonUtils.randomString(10))
        .creation

    val r2 = accessRequest
      .fromTopicKey(fromTopicKey)
      .toTopicKey(toTopicKey)
      .jarKey(fakeJar)
      .settings(Map("name" -> JsString("fake")))
      .creation

    r1.toTopicKeys shouldBe r2.toTopicKeys
    r1.fromTopicKeys shouldBe r2.fromTopicKeys
    // settings will overwrite default value
    r1.name should not be r2.name
  }

  @Test
  def testBrokerClusterKey(): Unit = {
    val bkName  = CommonUtils.randomString()
    val bkGroup = CommonUtils.randomString()
    val r1 = accessRequest
      .brokerClusterKey(ObjectKey.of(bkGroup, bkName))
      .jarKey(fakeJar)
      .fromTopicKey(topicKey(CommonUtils.randomString()))
      .toTopicKey(topicKey(CommonUtils.randomString()))
      .nodeName(CommonUtils.randomString(10))
      .creation
    r1.brokerClusterKey.name() shouldBe bkName
  }

  @Test
  def testDeadNodes(): Unit = {
    val cluster = StreamClusterInfo(
      settings = StreamApi.access.request
        .jarKey(fakeJar)
        .nodeNames(Set("n0", "n1"))
        .fromTopicKey(topicKey(CommonUtils.randomString()))
        .toTopicKey(topicKey(CommonUtils.randomString()))
        .brokerClusterKey(ObjectKey.of("group", "n"))
        .creation
        .settings,
      aliveNodes = Set("n0"),
      state = Some("running"),
      error = None,
      metrics = Metrics.EMPTY,
      lastModified = CommonUtils.current()
    )
    cluster.deadNodes shouldBe Set("n1")
    cluster.copy(state = None).deadNodes shouldBe Set.empty
  }

  @Test
  def nameDefinitionShouldBeNonUpdatable(): Unit =
    StreamApi.DEFINITIONS(NAME_KEY).permission() shouldBe Permission.CREATE_ONLY

  @Test
  def groupDefinitionShouldBeNonUpdatable(): Unit =
    StreamApi.DEFINITIONS(GROUP_KEY).permission() shouldBe Permission.CREATE_ONLY

  @Test
  def testMaxHeap(): Unit = StreamApi.CREATION_JSON_FORMAT.read(s"""
                                                                   |  {
                                                                   |    "brokerClusterKey": "bk",
                                                                   |    "nodeNames": ["node00"],
                                                                   |    "jarKey": "k",
                                                                   |    "xmx": 123
                                                                   |  }
      """.stripMargin.parseJson).maxHeap shouldBe 123

  @Test
  def testInitHeap(): Unit = StreamApi.CREATION_JSON_FORMAT.read(s"""
                                                                    |  {
                                                                    |    "brokerClusterKey": "bk",
                                                                    |    "nodeNames": ["node00"],
                                                                    |    "jarKey": "k",
                                                                    |    "xms": 123
                                                                    |  }
      """.stripMargin.parseJson).initHeap shouldBe 123

  @Test
  def testNegativeMaxHeap(): Unit =
    an[DeserializationException] should be thrownBy StreamApi.CREATION_JSON_FORMAT.read(s"""
                                                                                                                           |  {
                                                                                                                           |    "nodeNames": ["node00"],
                                                                                                                           |    "xmx": -123
                                                                                                                           |  }
      """.stripMargin.parseJson)

  @Test
  def testNegativeInitHeap(): Unit =
    an[DeserializationException] should be thrownBy StreamApi.CREATION_JSON_FORMAT.read(s"""
                                                                                                                            |  {
                                                                                                                            |    "nodeNames": ["node00"],
                                                                                                                            |    "xms": -123
                                                                                                                            |  }
      """.stripMargin.parseJson)

  @Test
  def settingsDisappearFromJson(): Unit = {
    val cluster = StreamClusterInfo(
      settings = StreamApi.access.request
        .jarKey(ObjectKey.of("a", "b"))
        .brokerClusterKey(ObjectKey.of("a", "b"))
        .nodeNames(Set("n0", "n1"))
        .creation
        .settings,
      aliveNodes = Set("n0"),
      state = Some("running"),
      error = None,
      metrics = Metrics.EMPTY,
      lastModified = CommonUtils.current()
    )
    StreamApi.STREAM_CLUSTER_INFO_FORMAT.write(cluster).asJsObject.fields.keySet should not contain ("settings")
  }

  @Test
  def testInfoJsonRepresentation(): Unit = {
    val cluster = StreamClusterInfo(
      settings = StreamApi.access.request
        .jarKey(ObjectKey.of("a", "b"))
        .brokerClusterKey(ObjectKey.of("a", "b"))
        .nodeNames(Set("n0", "n1"))
        .creation
        .settings,
      aliveNodes = Set("n0"),
      state = Some("running"),
      error = None,
      metrics = Metrics.EMPTY,
      lastModified = CommonUtils.current()
    )
    StreamApi.STREAM_CLUSTER_INFO_FORMAT.read(StreamApi.STREAM_CLUSTER_INFO_FORMAT.write(cluster)) shouldBe cluster
  }

  @Test
  def emptyNodeNamesIsLegal(): Unit = StreamApi.CREATION_JSON_FORMAT.read(s"""
                                                                             |  {
                                                                             |    "jarKey": ${fakeJar.toJson},
                                                                             |    "from": [
                                                                             |    ],
                                                                             |    "to": [
                                                                             |    ],
                                                                             |    "brokerClusterKey": {
                                                                             |      "group": "g",
                                                                             |      "name": "n"
                                                                             |    }
                                                                             |  }
     """.stripMargin.parseJson).nodeNames shouldBe Set.empty
}
