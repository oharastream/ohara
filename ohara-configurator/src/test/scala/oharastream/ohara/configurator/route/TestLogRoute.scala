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

package oharastream.ohara.configurator.route

import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

import oharastream.ohara.client.configurator.ShabondiApi.ShabondiClusterInfo
import oharastream.ohara.client.configurator.TopicApi.TopicInfo
import oharastream.ohara.client.configurator._
import oharastream.ohara.common.rule.OharaTest
import oharastream.ohara.common.setting.{ObjectKey, TopicKey}
import oharastream.ohara.common.util.{CommonUtils, Releasable}
import oharastream.ohara.configurator.Configurator
import org.junit.jupiter.api.{AfterEach, Test}
import org.scalatest.matchers.should.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
class TestLogRoute extends OharaTest {
  private[this] val configurator = Configurator.builder.fake().build()

  private[this] val logApi = LogApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val nodeApi = NodeApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val zkApi = ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val bkApi = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val wkApi = WorkerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val streamApi = StreamApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val shabondiApi = ShabondiApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val fileApi = FileInfoApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val topicApi = TopicApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def result[T](f: Future[T]): T = Await.result(f, Duration(20, TimeUnit.SECONDS))

  private def startTopic(bkKey: ObjectKey): TopicInfo = {
    val topic = TopicKey.of("default", CommonUtils.randomString(5))
    val topicInfo = result(
      topicApi.request.key(topic).brokerClusterKey(bkKey).create()
    )
    result(topicApi.start(topicInfo.key))
    topicInfo
  }

  @Test
  def fetchLogFromZookeeper(): Unit = {
    val cluster     = result(zkApi.list()).head
    val clusterLogs = result(logApi.log4ZookeeperCluster(cluster.key))
    clusterLogs.clusterKey shouldBe cluster.key
    clusterLogs.logs.isEmpty shouldBe false
  }

  @Test
  def fetchLogFromBroker(): Unit = {
    val cluster     = result(bkApi.list()).head
    val clusterLogs = result(logApi.log4BrokerCluster(cluster.key))
    clusterLogs.clusterKey shouldBe cluster.key
    clusterLogs.logs.isEmpty shouldBe false
  }

  @Test
  def fetchLogFromWorker(): Unit = {
    val cluster     = result(wkApi.list()).head
    val clusterLogs = result(logApi.log4WorkerCluster(cluster.key))
    clusterLogs.clusterKey shouldBe cluster.key
    clusterLogs.logs.isEmpty shouldBe false
  }

  @Test
  def fetchLogFromStream(): Unit = {
    val file              = result(fileApi.request.file(RouteUtils.streamFile).upload())
    val brokerClusterInfo = result(bkApi.list()).head
    val fromTopic         = startTopic(brokerClusterInfo.key)
    result(topicApi.start(fromTopic.key))
    result(topicApi.get(fromTopic.key)).state should not be None
    val toTopic = startTopic(brokerClusterInfo.key)
    result(topicApi.start(toTopic.key))
    result(topicApi.get(toTopic.key)).state should not be None
    val nodeNames = result(zkApi.list()).head.nodeNames
    val cluster = result(
      streamApi.request
        .nodeNames(nodeNames)
        .fromTopicKey(fromTopic.key)
        .toTopicKey(toTopic.key)
        .jarKey(file.key)
        .brokerClusterKey(
          result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list()).head.key
        )
        .create()
    )
    result(streamApi.start(cluster.key))
    val clusterLogs = result(logApi.log4StreamCluster(cluster.key))
    clusterLogs.clusterKey shouldBe cluster.key
    clusterLogs.logs.isEmpty shouldBe false
  }

  @Test
  def fetchLogFromShabondiSource(): Unit = {
    val availableNodeNames = result(nodeApi.list()).map(_.hostname)
    val brokerClusterInfo  = result(bkApi.list()).head
    val topicInfo          = startTopic(brokerClusterInfo.key)

    val shabondiKey            = ObjectKey.of("group", "name")
    val (clientPort, nodeName) = (CommonUtils.availablePort(), availableNodeNames(0))
    createShabondiService(
      ShabondiApi.SHABONDI_SOURCE_CLASS,
      brokerClusterInfo,
      shabondiKey,
      clientPort,
      Set(nodeName),
      Set(topicInfo.key)
    )
    result(shabondiApi.start(shabondiKey))

    val clusterLogs = result(logApi.log4ShabondiCluster(shabondiKey))
    clusterLogs.clusterKey shouldBe shabondiKey
    clusterLogs.logs.isEmpty shouldBe false
  }

  @Test
  def fetchLogFromShabondiSink(): Unit = {
    val availableNodeNames = result(nodeApi.list()).map(_.hostname)
    val brokerClusterInfo  = result(bkApi.list()).head
    val topicInfo          = startTopic(brokerClusterInfo.key)

    val shabondiKey            = ObjectKey.of("group", "name")
    val (clientPort, nodeName) = (CommonUtils.availablePort(), availableNodeNames(0))
    createShabondiService(
      ShabondiApi.SHABONDI_SINK_CLASS,
      brokerClusterInfo,
      shabondiKey,
      clientPort,
      Set(nodeName),
      Set(topicInfo.key)
    )
    result(shabondiApi.start(shabondiKey))

    val clusterLogs = result(logApi.log4ShabondiCluster(shabondiKey))
    clusterLogs.clusterKey shouldBe shabondiKey
    clusterLogs.logs.isEmpty shouldBe false
  }

  private def createShabondiService(
    shabondiClass: Class[_],
    bkClusterInfo: BrokerApi.BrokerClusterInfo,
    key: ObjectKey,
    clientPort: Int,
    nodeNames: Set[String],
    topicKeys: Set[TopicKey]
  ): ShabondiClusterInfo = {
    val request = shabondiApi.request
      .name(key.name)
      .group(key.group)
      .brokerClusterKey(bkClusterInfo.key)
      .nodeNames(nodeNames) // note: nodeNames only support one node currently.
      .shabondiClass(shabondiClass.getName)
      .clientPort(clientPort)
    shabondiClass match {
      case ShabondiApi.SHABONDI_SOURCE_CLASS => request.sourceToTopics(topicKeys)
      case ShabondiApi.SHABONDI_SINK_CLASS   => request.sinkFromTopics(topicKeys)
      case _                                 => throw new UnsupportedOperationException(s"$shabondiClass is unsupported")
    }
    result(request.create())
  }
  @Test
  def fetchLogFromUnknown(): Unit = {
    val unknownKey = ObjectKey.of("default", CommonUtils.randomString(10))
    an[IllegalArgumentException] should be thrownBy result(logApi.log4ZookeeperCluster(unknownKey))
    an[IllegalArgumentException] should be thrownBy result(logApi.log4BrokerCluster(unknownKey))
    an[IllegalArgumentException] should be thrownBy result(logApi.log4WorkerCluster(unknownKey))
    an[IllegalArgumentException] should be thrownBy result(logApi.log4StreamCluster(unknownKey))
    an[IllegalArgumentException] should be thrownBy result(logApi.log4ShabondiCluster(unknownKey))
  }

  /**
    * in unit test, the configurator is NOT on docker container. Hence, we can't get log...
    */
  @Test
  def fetchLogFromConfigurator(): Unit = result(logApi.log4Configurator())

  @Test
  def testSinceSeconds(): Unit = {
    val df           = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS")
    val minTime      = df.parse("2019-12-17 03:20:00,339").getTime
    val weirdString0 = CommonUtils.randomString()
    val weirdString1 = CommonUtils.randomString()
    val strings: Iterator[String] = new Iterator[String] {
      private[this] val lines = Seq(
        "2019-12-17 03:20:00,337 INFO  [main] configurator.Configurator$(391): start a configurator built on hostname:node00 and port:12345",
        weirdString0,
        "2019-12-17 03:20:00,339 INFO  [main] configurator.Configurator$(393): enter ctrl+c to terminate the configurator",
        weirdString1,
        "2019-12-17 03:20:38,352 INFO  [main] configurator.Configurator$(397): Current data size:0"
      )
      private[this] var index       = 0
      override def hasNext: Boolean = index < lines.size
      override def next(): String =
        try lines(index)
        finally index += 1
    }
    val result = LogRoute.seekLogByTimestamp(strings, minTime)
    result should not include ("configurator.Configurator$(391)")
    result should not include (weirdString0)
    result should include(weirdString1)
    result should include("configurator.Configurator$(397)")
  }

  @AfterEach
  def tearDown(): Unit = Releasable.close(configurator)
}
