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

package com.island.ohara.client.kafka

import java.util.Collections

import com.island.ohara.client.configurator.v0.ConnectorApi.State
import com.island.ohara.common.data.Serializer
import com.island.ohara.common.setting.{ConnectorKey, SettingDef, TopicKey}
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.kafka.Consumer
import com.island.ohara.kafka.connector.json.{ConnectorDefUtils, ConverterType, StringList}
import com.island.ohara.testing.With3Brokers3Workers
import org.junit.Test
import org.scalatest.Matchers

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
class TestWorkerClient extends With3Brokers3Workers with Matchers {

  private[this] val workerClient = WorkerClient(testUtil().workersConnProps())
  @Test
  def testExist(): Unit = {
    val topicKey = TopicKey.of(CommonUtils.randomString(10), CommonUtils.randomString(10))
    val connectorKey = ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5))
    result(workerClient.exist(connectorKey)) shouldBe false

    result(
      workerClient
        .connectorCreator()
        .topicKey(topicKey)
        .connectorClass(classOf[MyConnector])
        .connectorKey(connectorKey)
        .numberOfTasks(1)
        .create())

    try assertExist(workerClient, connectorKey)
    finally result(workerClient.delete(connectorKey))
  }

  @Test
  def testExistOnUnrunnableConnector(): Unit = {
    val topicKey = TopicKey.of(CommonUtils.randomString(10), CommonUtils.randomString(10))
    val connectorKey = ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5))
    result(workerClient.exist(connectorKey)) shouldBe false

    result(
      workerClient
        .connectorCreator()
        .topicKey(topicKey)
        .connectorClass(classOf[BrokenConnector])
        .connectorKey(connectorKey)
        .numberOfTasks(1)
        .create())

    try assertExist(workerClient, connectorKey)
    finally result(workerClient.delete(connectorKey))
  }

  @Test
  def testPauseAndResumeSource(): Unit = {
    val topicKey = TopicKey.of(CommonUtils.randomString(10), CommonUtils.randomString(10))
    val connectorKey = ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5))
    result(
      workerClient
        .connectorCreator()
        .topicKey(topicKey)
        .connectorClass(classOf[MyConnector])
        .connectorKey(connectorKey)
        .numberOfTasks(1)
        .create())
    try {
      assertExist(workerClient, connectorKey)
      val consumer =
        Consumer
          .builder()
          .topicName(topicKey.topicNameOnKafka)
          .offsetFromBegin()
          .connectionProps(testUtil.brokersConnProps)
          .keySerializer(Serializer.ROW)
          .valueSerializer(Serializer.BYTES)
          .build()
      try {
        // try to receive some data from topic
        var rows = consumer.poll(java.time.Duration.ofSeconds(10), 1)
        rows.size should not be 0
        rows.asScala.foreach(_.key.get shouldBe ROW)
        // pause connector
        result(workerClient.pause(connectorKey))

        await(() => result(workerClient.status(connectorKey)).connector.state == State.PAUSED.name)

        // try to receive all data from topic...10 seconds should be enough in this case
        rows = consumer.poll(java.time.Duration.ofSeconds(10), Int.MaxValue)
        rows.asScala.foreach(_.key.get shouldBe ROW)

        // connector is paused so there is no data
        rows = consumer.poll(java.time.Duration.ofSeconds(20), 1)
        rows.size shouldBe 0

        // resume connector
        result(workerClient.resume(connectorKey))

        await(() => result(workerClient.status(connectorKey)).connector.state == State.RUNNING.name)

        // since connector is resumed so some data are generated
        rows = consumer.poll(java.time.Duration.ofSeconds(20), 1)
        rows.size should not be 0
      } finally consumer.close()
    } finally result(workerClient.delete(connectorKey))
  }

  @Test
  def testValidate(): Unit = {
    val name = CommonUtils.randomString(10)
    val topicName = CommonUtils.randomString(10)
    val numberOfTasks = 1
    val settingInfo = result(
      workerClient
        .connectorValidator()
        .className(classOf[MyConnector].getName)
        .settings(Map(
          ConnectorDefUtils.CONNECTOR_NAME_DEFINITION.key() -> name,
          ConnectorDefUtils.TOPIC_NAMES_DEFINITION.key() -> StringList.toJsonString(
            Collections.singletonList(topicName)),
          ConnectorDefUtils.NUMBER_OF_TASKS_DEFINITION.key() -> numberOfTasks.toString
        ))
        .run())
    settingInfo.className.get shouldBe classOf[MyConnector].getName
    settingInfo.settings.size should not be 0
    settingInfo.topicNamesOnKafka.asScala shouldBe Seq(topicName)
    settingInfo.numberOfTasks.get shouldBe numberOfTasks
  }

  @Test
  def ignoreTopicNames(): Unit = an[NoSuchElementException] should be thrownBy result(
    workerClient.connectorValidator().className(classOf[MyConnector].getName).run())

  @Test
  def ignoreClassName(): Unit =
    an[NoSuchElementException] should be thrownBy result(workerClient.connectorValidator().run())

  @Test
  def testValidateWithoutValue(): Unit = {
    val topicKey = TopicKey.of(CommonUtils.randomString(10), CommonUtils.randomString(10))
    val settingInfo = result(
      workerClient.connectorValidator().className(classOf[MyConnector].getName).topicKey(topicKey).run())
    settingInfo.className.get shouldBe classOf[MyConnector].getName
    settingInfo.settings.size should not be 0
    settingInfo.topicNamesOnKafka.isEmpty shouldBe false
    settingInfo.numberOfTasks.isPresent shouldBe false
  }

  @Test
  def testColumnsDefinition(): Unit =
    result(workerClient.connectorDefinitions())
      .map(_.definitions.filter(_.key() == ConnectorDefUtils.COLUMNS_DEFINITION.key()).head)
      .foreach { definition =>
        definition.tableKeys().size() should not be 0
      }

  @Test
  def testAllPluginDefinitions(): Unit = {
    val plugins = result(workerClient.connectorDefinitions())
    plugins.size should not be 0
    plugins.foreach(plugin => check(plugin.definitions))
  }
  @Test
  def testListDefinitions(): Unit = {
    check(result(workerClient.definitions(classOf[MyConnector].getName)))
  }

  private[this] def check(settingDefinitionS: Seq[SettingDef]): Unit = {
    settingDefinitionS.size should not be 0

    settingDefinitionS.exists(_.key() == ConnectorDefUtils.CONNECTOR_CLASS_DEFINITION.key()) shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.CONNECTOR_CLASS_DEFINITION.key())
      .head
      .group() shouldBe ConnectorDefUtils.CORE_GROUP
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.CONNECTOR_CLASS_DEFINITION.key())
      .head
      .internal() shouldBe false
    settingDefinitionS.find(_.key() == ConnectorDefUtils.CONNECTOR_CLASS_DEFINITION.key()).head.editable() shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.CONNECTOR_CLASS_DEFINITION.key())
      .head
      .defaultValue() shouldBe null

    settingDefinitionS.exists(_.key() == ConnectorDefUtils.COLUMNS_DEFINITION.key()) shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.COLUMNS_DEFINITION.key())
      .head
      .group() shouldBe ConnectorDefUtils.CORE_GROUP
    settingDefinitionS.find(_.key() == ConnectorDefUtils.COLUMNS_DEFINITION.key()).head.internal() shouldBe false
    settingDefinitionS.find(_.key() == ConnectorDefUtils.COLUMNS_DEFINITION.key()).head.editable() shouldBe true
    settingDefinitionS.find(_.key() == ConnectorDefUtils.COLUMNS_DEFINITION.key()).head.defaultValue() shouldBe null

    settingDefinitionS.exists(_.key() == ConnectorDefUtils.NUMBER_OF_TASKS_DEFINITION.key()) shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.NUMBER_OF_TASKS_DEFINITION.key())
      .head
      .group() shouldBe ConnectorDefUtils.CORE_GROUP
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.NUMBER_OF_TASKS_DEFINITION.key())
      .head
      .internal() shouldBe false
    settingDefinitionS.find(_.key() == ConnectorDefUtils.NUMBER_OF_TASKS_DEFINITION.key()).head.editable() shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.NUMBER_OF_TASKS_DEFINITION.key())
      .head
      .defaultValue() shouldBe null

    settingDefinitionS.exists(_.key() == ConnectorDefUtils.TOPIC_NAMES_DEFINITION.key()) shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.TOPIC_NAMES_DEFINITION.key())
      .head
      .group() shouldBe ConnectorDefUtils.CORE_GROUP
    settingDefinitionS.find(_.key() == ConnectorDefUtils.TOPIC_NAMES_DEFINITION.key()).head.internal() shouldBe true
    settingDefinitionS.find(_.key() == ConnectorDefUtils.TOPIC_NAMES_DEFINITION.key()).head.editable() shouldBe true
    settingDefinitionS.find(_.key() == ConnectorDefUtils.TOPIC_NAMES_DEFINITION.key()).head.defaultValue() shouldBe null

    settingDefinitionS.exists(_.key() == ConnectorDefUtils.WORKER_CLUSTER_KEY_DEFINITION.key()) shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.WORKER_CLUSTER_KEY_DEFINITION.key())
      .head
      .group() shouldBe ConnectorDefUtils.CORE_GROUP
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.WORKER_CLUSTER_KEY_DEFINITION.key())
      .head
      .internal() shouldBe false
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.WORKER_CLUSTER_KEY_DEFINITION.key())
      .head
      .editable() shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.WORKER_CLUSTER_KEY_DEFINITION.key())
      .head
      .defaultValue() shouldBe null

    settingDefinitionS.exists(_.key() == ConnectorDefUtils.AUTHOR_DEFINITION.key()) shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.AUTHOR_DEFINITION.key())
      .head
      .group() shouldBe ConnectorDefUtils.CORE_GROUP
    settingDefinitionS.find(_.key() == ConnectorDefUtils.AUTHOR_DEFINITION.key()).head.internal() shouldBe false
    settingDefinitionS.find(_.key() == ConnectorDefUtils.AUTHOR_DEFINITION.key()).head.editable() shouldBe false
    settingDefinitionS.find(_.key() == ConnectorDefUtils.AUTHOR_DEFINITION.key()).head.defaultValue() shouldBe "unknown"

    settingDefinitionS.exists(_.key() == ConnectorDefUtils.VERSION_DEFINITION.key()) shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.VERSION_DEFINITION.key())
      .head
      .group() shouldBe ConnectorDefUtils.CORE_GROUP
    settingDefinitionS.find(_.key() == ConnectorDefUtils.VERSION_DEFINITION.key()).head.internal() shouldBe false
    settingDefinitionS.find(_.key() == ConnectorDefUtils.VERSION_DEFINITION.key()).head.editable() shouldBe false
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.VERSION_DEFINITION.key())
      .head
      .defaultValue() shouldBe "unknown"

    settingDefinitionS.exists(_.key() == ConnectorDefUtils.REVISION_DEFINITION.key()) shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.REVISION_DEFINITION.key())
      .head
      .group() shouldBe ConnectorDefUtils.CORE_GROUP
    settingDefinitionS.find(_.key() == ConnectorDefUtils.REVISION_DEFINITION.key()).head.internal() shouldBe false
    settingDefinitionS.find(_.key() == ConnectorDefUtils.REVISION_DEFINITION.key()).head.editable() shouldBe false
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.REVISION_DEFINITION.key())
      .head
      .defaultValue() shouldBe "unknown"

    settingDefinitionS.exists(_.key() == ConnectorDefUtils.KIND_DEFINITION.key()) shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.KIND_DEFINITION.key())
      .head
      .group() shouldBe ConnectorDefUtils.CORE_GROUP
    settingDefinitionS.find(_.key() == ConnectorDefUtils.KIND_DEFINITION.key()).head.internal() shouldBe false
    settingDefinitionS.find(_.key() == ConnectorDefUtils.KIND_DEFINITION.key()).head.editable() shouldBe false
    (settingDefinitionS.find(_.key() == ConnectorDefUtils.KIND_DEFINITION.key()).head.defaultValue() == "source"
    || settingDefinitionS
      .find(_.key() == ConnectorDefUtils.KIND_DEFINITION.key())
      .head
      .defaultValue() == "sink") shouldBe true

    settingDefinitionS.exists(_.key() == ConnectorDefUtils.KEY_CONVERTER_DEFINITION.key()) shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.KEY_CONVERTER_DEFINITION.key())
      .head
      .group() shouldBe ConnectorDefUtils.CORE_GROUP
    settingDefinitionS.find(_.key() == ConnectorDefUtils.KEY_CONVERTER_DEFINITION.key()).head.internal() shouldBe true
    settingDefinitionS.find(_.key() == ConnectorDefUtils.KEY_CONVERTER_DEFINITION.key()).head.editable() shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.KEY_CONVERTER_DEFINITION.key())
      .head
      .defaultValue() shouldBe ConverterType.NONE.className()

    settingDefinitionS.exists(_.key() == ConnectorDefUtils.VALUE_CONVERTER_DEFINITION.key()) shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.VALUE_CONVERTER_DEFINITION.key())
      .head
      .group() shouldBe ConnectorDefUtils.CORE_GROUP
    settingDefinitionS.find(_.key() == ConnectorDefUtils.VALUE_CONVERTER_DEFINITION.key()).head.internal() shouldBe true
    settingDefinitionS.find(_.key() == ConnectorDefUtils.VALUE_CONVERTER_DEFINITION.key()).head.editable() shouldBe true
    settingDefinitionS
      .find(_.key() == ConnectorDefUtils.VALUE_CONVERTER_DEFINITION.key())
      .head
      .defaultValue() shouldBe ConverterType.NONE.className()
  }

  @Test
  def passIncorrectColumns(): Unit = {
    val topicKey = TopicKey.of(CommonUtils.randomString(10), CommonUtils.randomString(10))
    val e = intercept[IllegalArgumentException] {
      result(
        workerClient
          .connectorCreator()
          .topicKey(topicKey)
          .connectorClass(classOf[MyConnector])
          .connectorKey(ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5)))
          .numberOfTasks(1)
          .settings(Map(ConnectorDefUtils.COLUMNS_DEFINITION.key() -> "Asdasdasd"))
          .create())
    }
    //see SettingDef.checker
    e.getMessage.contains("can't be converted to PropGroups type") shouldBe true
  }

  @Test
  def passIncorrectDuration(): Unit = {
    val topicKey = TopicKey.of(CommonUtils.randomString(10), CommonUtils.randomString(10))
    val e = intercept[IllegalArgumentException] {
      result(
        workerClient
          .connectorCreator()
          .topicKey(topicKey)
          .connectorClass(classOf[MyConnector])
          .connectorKey(ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5)))
          .numberOfTasks(1)
          .settings(Map(MyConnector.DURATION_KEY -> "Asdasdasd"))
          .create())
    }
    //see ConnectorDefinitions.validator
    e.getMessage.contains("can't be converted to Duration type") shouldBe true
  }

  @Test
  def pass1Second(): Unit = {
    val topicKey = TopicKey.of(CommonUtils.randomString(10), CommonUtils.randomString(10))
    result(
      workerClient
        .connectorCreator()
        .topicKey(topicKey)
        .connectorClass(classOf[MyConnector])
        .connectorKey(ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5)))
        .numberOfTasks(1)
        .settings(Map(MyConnector.DURATION_KEY -> "PT1S"))
        .create())
  }
  @Test
  def pass1Minute1Second(): Unit = {
    val topicKey = TopicKey.of(CommonUtils.randomString(10), CommonUtils.randomString(10))
    result(
      workerClient
        .connectorCreator()
        .topicKey(topicKey)
        .connectorClass(classOf[MyConnector])
        .connectorKey(ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5)))
        .numberOfTasks(1)
        .settings(Map(MyConnector.DURATION_KEY -> "PT1M1S"))
        .create())
  }

  @Test
  def nullConnectionProps(): Unit =
    an[NullPointerException] should be thrownBy WorkerClient.builder.connectionProps(null)

  @Test
  def emptyConnectionProps(): Unit =
    an[IllegalArgumentException] should be thrownBy WorkerClient.builder.connectionProps("")

  @Test
  def nullRetryLimit(): Unit = {
    an[IllegalArgumentException] should be thrownBy WorkerClient.builder.retryLimit(0)
    an[IllegalArgumentException] should be thrownBy WorkerClient.builder.retryLimit(-1)
  }

  @Test
  def nullRetryInterval(): Unit = an[NullPointerException] should be thrownBy WorkerClient.builder.retryInternal(null)
}
