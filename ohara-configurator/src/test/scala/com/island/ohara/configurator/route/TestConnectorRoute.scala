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

package com.island.ohara.configurator.route

import com.island.ohara.client.configurator.v0.WorkerApi.WorkerClusterStatus
import com.island.ohara.client.configurator.v0.{BrokerApi, ConnectorApi, TopicApi, WorkerApi}
import com.island.ohara.common.data.{Column, DataType}
import com.island.ohara.common.rule.OharaTest
import com.island.ohara.common.setting.{ConnectorKey, ObjectKey}
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import org.junit.{After, Before, Test}
import org.scalatest.Matchers
import spray.json.{JsNumber, JsString}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
class TestConnectorRoute extends OharaTest with Matchers {
  private[this] val configurator = Configurator.builder.fake(1, 1).build()

  private[this] val connectorApi = ConnectorApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val topicApi = TopicApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] var defaultWk: WorkerClusterStatus = _

  private[this] def result[T](f: Future[T]): T = Await.result(f, Duration("20 seconds"))

  @Before
  def setup(): Unit = {
    defaultWk = result(configurator.serviceCollie.workerCollie.clusters().map(_.keys.headOption))
      .getOrElse(throw new IllegalArgumentException("we expected at least one worker cluster, but none?"))
  }

  @Test
  def listConnectorDeployedOnNonexistentCluster(): Unit = {
    val connector = result(
      connectorApi.request
        .className(CommonUtils.randomString(10))
        .workerClusterKey(ObjectKey.of(CommonUtils.randomString(), CommonUtils.randomString()))
        .create())

    result(connectorApi.get(connector.key)).key shouldBe connector.key
  }

  @Test
  def runConnectorWithoutTopic(): Unit = {
    val connector = result(
      connectorApi.request.name(CommonUtils.randomString(10)).className(CommonUtils.randomString(10)).create())

    an[IllegalArgumentException] should be thrownBy result(connectorApi.start(connector.key))
  }

  @Test
  def test(): Unit = {
    // test add
    result(connectorApi.list()).size shouldBe 0

    val columns = Seq(Column.builder().name("cf").dataType(DataType.BOOLEAN).order(1).build(),
                      Column.builder().name("cf").dataType(DataType.BOOLEAN).order(2).build())
    val name = CommonUtils.randomString()
    val className = CommonUtils.randomString()
    val numberOfTasks = 3
    val response = result(
      connectorApi.request.name(name).className(className).columns(columns).numberOfTasks(numberOfTasks).create())
    response.name shouldBe name
    response.className shouldBe className
    response.columns shouldBe columns
    response.numberOfTasks shouldBe numberOfTasks

    // test update
    val className2 = CommonUtils.randomString()
    val numberOfTasks2 = 5
    val columns2 = Seq(Column.builder().name("cf").dataType(DataType.BOOLEAN).order(1).build())
    val response2 = result(
      connectorApi.request
        .key(response.key)
        .className(className2)
        .columns(columns2)
        .numberOfTasks(numberOfTasks2)
        .update())
    response2.name shouldBe name
    response2.className shouldBe className2
    response2.columns shouldBe columns2
    response2.numberOfTasks shouldBe numberOfTasks2

    // test delete
    result(connectorApi.list()).size shouldBe 1
    result(connectorApi.delete(response.key))
    result(connectorApi.list()).size shouldBe 0

    // test nonexistent data
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.get(ObjectKey.of(CommonUtils.randomString(), CommonUtils.randomString())))
  }

  @Test
  def removeConnectorFromDeletedCluster(): Unit = {
    val connector = result(
      ConnectorApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .workerClusterKey(defaultWk.key)
        .create())

    connector.workerClusterKey shouldBe defaultWk.key
    result(configurator.serviceCollie.workerCollie.remove(defaultWk.key))

    result(connectorApi.delete(connector.key))

    result(connectorApi.list()).exists(_.name == connector.name) shouldBe false
  }

  @Test
  def runConnectorOnNonexistentCluster(): Unit = {
    val c = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .workerClusterKey(ObjectKey.of(CommonUtils.randomString(), CommonUtils.randomString()))
        .create())

    an[IllegalArgumentException] should be thrownBy result(connectorApi.start(c.key))
  }

  @Test
  def runConnectorWithoutSpecificCluster(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    // absent worker cluster is ok since there is only one worker cluster
    val connector = result(
      connectorApi.request.className("com.island.ohara.connector.ftp.FtpSink").topicKey(topic.key).create())
    // In creation, workerClusterName will not be auto-filled
    connector.workerClusterKey shouldBe defaultWk.key
    // data stored in configurator should also get the auto-filled result
    result(connectorApi.get(connector.key)).workerClusterKey shouldBe defaultWk.key

    val bk = result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list()).head

    val wk = result(
      WorkerApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .brokerClusterKey(bk.key)
        .nodeNames(bk.nodeNames)
        .create())
    result(WorkerApi.access.hostname(configurator.hostname).port(configurator.port).start(wk.key))

    // there are two worker cluster so it fails to match worker cluster
    an[IllegalArgumentException] should be thrownBy result(
      ConnectorApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .create())

    val c2 = result(
      ConnectorApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .topicKey(topic.key)
        .workerClusterKey(wk.key)
        .create())
    //pass since we have assigned a worker cluster
    result(topicApi.start(topic.key))
    result(connectorApi.start(c2.key))
  }

  @Test
  def testIdempotentPause(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    val connector = result(
      ConnectorApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .topicKey(topic.key)
        .create())

    result(topicApi.start(topic.key))
    result(connectorApi.start(connector.key))

    (0 to 10).foreach(_ => result(connectorApi.pause(connector.key)))
  }

  @Test
  def testIdempotentResume(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    val connector = result(
      ConnectorApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .topicKey(topic.key)
        .create())

    result(topicApi.start(topic.key))
    result(connectorApi.start(connector.key))

    (0 to 10).foreach(_ => result(connectorApi.resume(connector.key)))
  }

  @Test
  def testIdempotentStop(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    val connector = result(
      ConnectorApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .topicKey(topic.key)
        .create())

    result(topicApi.start(topic.key))
    result(connectorApi.start(connector.key))

    (0 to 10).foreach(_ => result(connectorApi.stop(connector.key)))
  }

  @Test
  def testIdempotentStart(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    val connector = result(
      ConnectorApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .topicKey(topic.key)
        .create())

    result(topicApi.start(topic.key))
    result(connectorApi.start(connector.key))

    (0 to 10).foreach(_ => result(connectorApi.start(connector.key)))
  }

  @Test
  def failToChangeWorkerCluster(): Unit = {
    val bk = result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list()).head

    val wk = result(
      WorkerApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .brokerClusterKey(bk.key)
        .nodeNames(bk.nodeNames)
        .create())
    result(WorkerApi.access.hostname(configurator.hostname).port(configurator.port).start(wk.key))
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    val response = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .workerClusterKey(defaultWk.key)
        .topicKey(topic.key)
        .create())

    response.workerClusterKey shouldBe defaultWk.key
    result(topicApi.start(topic.key))
    result(connectorApi.start(response.key))
    // after start, you cannot change worker cluster
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.request.key(response.key).className(CommonUtils.randomString(10)).workerClusterKey(wk.key).update())

    result(connectorApi.stop(response.key))

    // the connector is stopped so it is ok to update it now.
    result(
      connectorApi.request
        .key(response.key)
        .className(CommonUtils.randomString(10))
        .workerClusterKey(wk.key)
        .update()).workerClusterKey shouldBe wk.key
  }

  @Test
  def testStartAnNonexistentConnector(): Unit =
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.start(ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5))))

  @Test
  def testStopAnNonexistentConnector(): Unit =
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.stop(ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5))))
  @Test
  def testPauseAnNonexistentConnector(): Unit =
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.pause(ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5))))

  @Test
  def testResumeAnNonexistentConnector(): Unit =
    an[IllegalArgumentException] should be thrownBy result(
      connectorApi.resume(ConnectorKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5))))

  @Test
  def updateTags(): Unit = {
    val tags = Map(
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsNumber(CommonUtils.randomInteger())
    )
    val connectorDesc = result(
      connectorApi.request.className("com.island.ohara.connector.ftp.FtpSink").tags(tags).create())
    connectorDesc.tags shouldBe tags

    val tags2 = Map(
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsNumber(CommonUtils.randomInteger())
    )
    val connectorDesc2 = result(
      connectorApi.request
        .className("com.island.ohara.connector.ftp.FtpSink")
        .name(connectorDesc.name)
        .tags(tags2)
        .update())
    connectorDesc2.tags shouldBe tags2

    val connectorDesc3 = result(connectorApi.request.name(connectorDesc.name).update())
    connectorDesc3.tags shouldBe tags2

    val connectorDesc4 = result(connectorApi.request.name(connectorDesc.name).tags(Map.empty).update())
    connectorDesc4.tags shouldBe Map.empty
  }

  @Test
  def failToDeletePropertiesOfRunningConnector(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())
    val connectorDesc = result(
      connectorApi.request.className("com.island.ohara.connector.ftp.FtpSink").topicKey(topic.key).create())
    result(topicApi.start(topic.key))
    result(connectorApi.start(connectorDesc.key))

    an[IllegalArgumentException] should be thrownBy result(connectorApi.request.key(connectorDesc.key).update())
    result(connectorApi.stop(connectorDesc.key))
    result(connectorApi.request.key(connectorDesc.key).update())
  }

  @Test
  def failToRunConnectorWithStoppedTopic(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())
    val connectorDesc = result(
      connectorApi.request.className("com.island.ohara.connector.ftp.FtpSink").topicKey(topic.key).create())
    an[IllegalArgumentException] should be thrownBy result(connectorApi.start(connectorDesc.key))

    result(topicApi.start(topic.key))
    result(connectorApi.start(connectorDesc.key))

  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
