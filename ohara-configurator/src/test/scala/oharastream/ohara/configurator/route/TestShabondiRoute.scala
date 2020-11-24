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

import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit

import oharastream.ohara.client.configurator.ShabondiApi.ShabondiClusterInfo
import oharastream.ohara.client.configurator.{BrokerApi, ClusterState, NodeApi, ShabondiApi, TopicApi}
import oharastream.ohara.common.rule.OharaTest
import oharastream.ohara.common.setting.{ClassType, ObjectKey, TopicKey}
import oharastream.ohara.common.util.{CommonUtils, Releasable}
import oharastream.ohara.configurator.Configurator
import oharastream.ohara.shabondi.ShabondiDefinitions._
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import org.scalatest.matchers.should.Matchers._
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class TestShabondiRoute extends OharaTest {
  private[this] val configurator = Configurator.builder.fake(1, 0).build()
  private[this] val nodeApi      = NodeApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val topicApi     = TopicApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val brokerApi    = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val brokerClusterInfo = await(
    BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list()
  ).head
  private[this] val shabondiApi      = ShabondiApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val nodeName: String = await(nodeApi.list()).map(_.hostname).head

  private[this] val topicKey  = TopicKey.of("g", CommonUtils.randomString(10))
  private[this] val objectKey = ObjectKey.of("group", "name")

  private[this] def await[T](f: Future[T]): T = Await.result(f, Duration(20, TimeUnit.SECONDS))

  private[this] def awaitTrue(f: () => Boolean, swallowException: Boolean = false): Unit =
    CommonUtils.await(
      () =>
        try f()
        catch {
          case _: Throwable if swallowException =>
            false
        },
      JDuration.ofSeconds(20)
    )

  @BeforeEach
  def setup(): Unit = {
    await(topicApi.request.brokerClusterKey(brokerClusterInfo.key).key(topicKey).create())
    await(topicApi.start(topicKey))
  }

  @AfterEach
  def tearDown(): Unit = Releasable.close(configurator)

  @Test
  def failToStopTopicIfItIsUsedByShabondiSource(): Unit =
    verifyShabondiDependencies(
      await(
        shabondiApi.request
          .group(objectKey.group)
          .name(objectKey.name)
          .shabondiClass(ShabondiApi.SHABONDI_SOURCE_CLASS_NAME)
          .clientPort(CommonUtils.availablePort())
          .brokerClusterKey(brokerClusterInfo.key)
          .nodeName(nodeName)
          .sourceToTopics(Set(topicKey))
          .create()
      ).key,
      brokerClusterInfo.key,
      topicKey
    )

  @Test
  def failToStopTopicIfItIsUsedByShabondiSink(): Unit =
    verifyShabondiDependencies(
      await(
        shabondiApi.request
          .group(objectKey.group)
          .name(objectKey.name)
          .shabondiClass(ShabondiApi.SHABONDI_SINK_CLASS_NAME)
          .clientPort(CommonUtils.availablePort())
          .brokerClusterKey(brokerClusterInfo.key)
          .nodeName(nodeName)
          .sinkFromTopics(Set(topicKey))
          .create()
      ).key,
      brokerClusterInfo.key,
      topicKey
    )

  private[this] def verifyShabondiDependencies(
    shabondiKey: ObjectKey,
    brokerClusterKey: ObjectKey,
    topicKey: TopicKey
  ): Unit = {
    await(shabondiApi.start(shabondiKey))
    an[IllegalArgumentException] should be thrownBy await(brokerApi.stop(brokerClusterKey))
    an[IllegalArgumentException] should be thrownBy await(topicApi.stop(topicKey))

    await(shabondiApi.stop(shabondiKey))
    await(topicApi.stop(topicKey))
    await(brokerApi.stop(brokerClusterKey))

    an[IllegalArgumentException] should be thrownBy await(topicApi.delete(topicKey))
    an[IllegalArgumentException] should be thrownBy await(brokerApi.delete(brokerClusterKey))
    await(shabondiApi.delete(shabondiKey))
    await(topicApi.delete(topicKey))
    await(brokerApi.delete(brokerClusterKey))
  }

  @Test
  def testShouldThrowExceptionWhenCreateOnNonExistentNode(): Unit = {
    val (clientPort, nodeName) = (CommonUtils.availablePort(), "non-existent-node")
    val clusterInfo: Future[ShabondiApi.ShabondiClusterInfo] =
      shabondiApi.request
        .group(objectKey.group)
        .name(objectKey.name)
        .shabondiClass(ShabondiApi.SHABONDI_SOURCE_CLASS_NAME)
        .clientPort(clientPort)
        .brokerClusterKey(brokerClusterInfo.key)
        .nodeName(nodeName)
        .create()

    an[IllegalArgumentException] should be thrownBy await(clusterInfo)
  }

  @Test
  def testShouldThrowExceptionWithInvalidClassName(): Unit = {
    val clusterInfo: Future[ShabondiApi.ShabondiClusterInfo] =
      shabondiApi.request
        .group(objectKey.group)
        .name(objectKey.name)
        .shabondiClass("oharastream.ohara.shabondi.Source") // Invalid class name
        .clientPort(CommonUtils.availablePort())
        .brokerClusterKey(brokerClusterInfo.key)
        .nodeName(nodeName)
        .create()

    an[IllegalArgumentException] should be thrownBy await(clusterInfo)
  }

  @Test
  def testSourceCreate(): Unit = {
    val clientPort = CommonUtils.availablePort()
    val clusterInfo =
      createShabondiService(ShabondiApi.SHABONDI_SOURCE_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))

    clusterInfo.group should ===(objectKey.group)
    clusterInfo.name should ===(objectKey.name)
    clusterInfo.shabondiClass should ===(ShabondiApi.SHABONDI_SOURCE_CLASS_NAME)
    clusterInfo.clientPort should ===(clientPort)
    clusterInfo.brokerClusterKey should ===(brokerClusterInfo.key)
    clusterInfo.nodeNames should contain(nodeName)
    clusterInfo.endpoint should ===(s"http://$nodeName:$clientPort/")
  }

  @Test
  def testSourceUpdate(): Unit = {
    val clientPort = CommonUtils.availablePort()
    val clusterInfo =
      createShabondiService(ShabondiApi.SHABONDI_SOURCE_CLASS, objectKey, clientPort, Set(nodeName), Set.empty)

    clusterInfo.group should ===(objectKey.group)
    clusterInfo.name should ===(objectKey.name)
    clusterInfo.shabondiClass should ===(ShabondiApi.SHABONDI_SOURCE_CLASS_NAME)
    clusterInfo.clientPort should ===(clientPort)
    clusterInfo.brokerClusterKey should ===(brokerClusterInfo.key)
    clusterInfo.nodeNames should contain(nodeName)
    clusterInfo.sourceToTopics.size shouldBe 0
    clusterInfo.imageName should ===(IMAGE_NAME_DEFAULT)
    clusterInfo.endpoint should ===(s"http://$nodeName:$clientPort/")

    val newClientPort = CommonUtils.availablePort()
    val updatedClusterInfo = await(
      shabondiApi.request
        .group(objectKey.group)
        .name(objectKey.name)
        .clientPort(newClientPort)
        .sourceToTopics(Set(topicKey))
        .imageName("ohara/shabondi")
        .update()
    )
    updatedClusterInfo.clientPort should ===(newClientPort)
    updatedClusterInfo.sourceToTopics should ===(Set(topicKey))
    updatedClusterInfo.imageName should ===(IMAGE_NAME_DEFAULT)
    updatedClusterInfo.endpoint should ===(s"http://$nodeName:$newClientPort/")
  }

  @Test
  def testSinkUpdate(): Unit = {
    val clientPort = CommonUtils.availablePort()
    val clusterInfo =
      createShabondiService(ShabondiApi.SHABONDI_SINK_CLASS, objectKey, clientPort, Set(nodeName), Set.empty)

    clusterInfo.group should ===(objectKey.group)
    clusterInfo.name should ===(objectKey.name)
    clusterInfo.shabondiClass should ===(ShabondiApi.SHABONDI_SINK_CLASS_NAME)
    clusterInfo.clientPort should ===(clientPort)
    clusterInfo.brokerClusterKey should ===(brokerClusterInfo.key)
    clusterInfo.nodeNames should contain(nodeName)
    clusterInfo.sinkFromTopics.size shouldBe 0
    clusterInfo.imageName should ===(IMAGE_NAME_DEFAULT)
    clusterInfo.endpoint should ===(s"http://$nodeName:$clientPort/groups/" + "${groupName}")

    val newClientPort = CommonUtils.availablePort()
    val updatedClusterInfo = await(
      shabondiApi.request
        .group(objectKey.group)
        .name(objectKey.name)
        .clientPort(newClientPort)
        .sinkFromTopics(Set(topicKey))
        .imageName("ohara/shabondi")
        .settings(
          Map(
            SINK_POLL_TIMEOUT_DEFINITION.key -> JsString(JDuration.ofSeconds(10).toString),
            SINK_GROUP_IDLETIME.key          -> JsString(JDuration.ofMinutes(30).toString)
          )
        )
        .update()
    )
    updatedClusterInfo.clientPort should ===(newClientPort)
    updatedClusterInfo.sinkFromTopics should ===(Set(topicKey))
    updatedClusterInfo.imageName should ===(IMAGE_NAME_DEFAULT)
    updatedClusterInfo.settings should contain(SINK_POLL_TIMEOUT_DEFINITION.key -> JsString("PT10S"))
    updatedClusterInfo.settings should contain(SINK_GROUP_IDLETIME.key          -> JsString("PT30M"))
    updatedClusterInfo.endpoint should ===(s"http://$nodeName:$newClientPort/groups/" + "${groupName}")
  }

  @Test
  def testSourceStart(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SOURCE_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))

    await(shabondiApi.start(objectKey))

    awaitTrue(() => {
      val shabondiList = await(shabondiApi.list())
      shabondiList.size shouldBe 1
      shabondiList.head.sourceToTopics shouldBe Set(topicKey)
      shabondiList.head.state.get shouldBe ClusterState.RUNNING
      shabondiList.head.aliveNodes.head shouldBe nodeName
      shabondiList.head.meters.nonEmpty
    })
  }

  @Test
  def testSinkStart(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SINK_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))

    await(shabondiApi.start(objectKey))

    awaitTrue(() => {
      val shabondiList = await(shabondiApi.list())
      shabondiList.size shouldBe 1
      shabondiList.head.sinkFromTopics shouldBe Set(topicKey)
      shabondiList.head.state.get shouldBe ClusterState.RUNNING
      shabondiList.head.aliveNodes.head shouldBe nodeName
      shabondiList.head.meters.nonEmpty
    })
  }

  @Test
  def testSourceStartAndStop(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SOURCE_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))

    await(shabondiApi.start(objectKey))
    awaitTrue(() => {
      val shabondiList = await(shabondiApi.list())
      shabondiList.size shouldBe 1
      shabondiList.head.sourceToTopics shouldBe Set(topicKey)
      shabondiList.head.state.get shouldBe ClusterState.RUNNING
      shabondiList.head.aliveNodes.head shouldBe nodeName
      shabondiList.head.meters.nonEmpty
    })

    await(shabondiApi.stop(objectKey))

    awaitTrue(() => {
      val shabondiList1 = await(shabondiApi.list())
      shabondiList1.size should ===(1)
      shabondiList1.head.state should ===(None)
      shabondiList1.head.meters.isEmpty
    })
  }

  @Test
  def testSinkStartAndStop(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SINK_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))

    await(shabondiApi.start(objectKey))
    awaitTrue(() => {
      val shabondiList = await(shabondiApi.list())
      shabondiList.size shouldBe 1
      shabondiList.head.sinkFromTopics shouldBe Set(topicKey)
      shabondiList.head.state.get shouldBe ClusterState.RUNNING
      shabondiList.head.aliveNodes.head shouldBe nodeName
      shabondiList.head.meters.nonEmpty
    })

    await(shabondiApi.stop(objectKey))

    awaitTrue(() => {
      val shabondiList1 = await(shabondiApi.list())
      shabondiList1.size should ===(1)
      shabondiList1.head.state should ===(None)
      shabondiList1.head.meters.isEmpty
    })
  }

  @Test
  def testSourceCanDelete(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SOURCE_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))

    await(shabondiApi.delete(objectKey))
    val shabondiList1 = await(shabondiApi.list())
    shabondiList1.size should ===(0)
  }

  @Test
  def testSinkCanDelete(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SINK_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))

    await(shabondiApi.delete(objectKey))
    val shabondiList1 = await(shabondiApi.list())
    shabondiList1.size should ===(0)
  }

  @Test
  def testSourceDeleteWhenRunning(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SOURCE_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))

    await(shabondiApi.start(objectKey))

    an[IllegalArgumentException] should be thrownBy await(shabondiApi.delete(objectKey))
  }

  @Test
  def testSinkDeleteWhenRunning(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SINK_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))

    await(shabondiApi.start(objectKey))

    an[IllegalArgumentException] should be thrownBy await(shabondiApi.delete(objectKey))
  }

  @Test
  def testSourceCanDeleteMultipleTimes(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SOURCE_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))

    await(shabondiApi.delete(objectKey))
    await(shabondiApi.delete(objectKey))
    await(shabondiApi.delete(objectKey))
  }

  @Test
  def testSinkCanDeleteMultipleTimes(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SINK_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))

    await(shabondiApi.delete(objectKey))
    await(shabondiApi.delete(objectKey))
    await(shabondiApi.delete(objectKey))
  }

  @Test
  def testSourceCanStopMultipleTimes(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SOURCE_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))
    await(shabondiApi.start(objectKey))

    await(shabondiApi.stop(objectKey))
    await(shabondiApi.stop(objectKey))
    await(shabondiApi.stop(objectKey))
  }

  @Test
  def testSinkCanStopMultipleTimes(): Unit = {
    val clientPort = CommonUtils.availablePort()
    createShabondiService(ShabondiApi.SHABONDI_SINK_CLASS, objectKey, clientPort, Set(nodeName), Set(topicKey))
    await(shabondiApi.start(objectKey))

    await(shabondiApi.stop(objectKey))
    await(shabondiApi.stop(objectKey))
    await(shabondiApi.stop(objectKey))
  }

  @Test
  def testShouldThrowExceptionIfTopicNotExistWhenSourceStart(): Unit = {
    val notStartedTopic = TopicKey.of("g1", "t1")
    await(topicApi.request.brokerClusterKey(brokerClusterInfo.key).key(notStartedTopic).create())

    val clientPort = CommonUtils.availablePort()
    createShabondiService(
      ShabondiApi.SHABONDI_SOURCE_CLASS,
      objectKey,
      clientPort,
      Set(nodeName),
      Set(notStartedTopic)
    )

    an[IllegalArgumentException] should be thrownBy await(shabondiApi.start(objectKey))
  }

  @Test
  def testShouldThrowExceptionIfTopicNotExistWhenSinkStart(): Unit = {
    val notStartedTopic = TopicKey.of("g1", "t1")
    await(topicApi.request.brokerClusterKey(brokerClusterInfo.key).key(notStartedTopic).create())

    val clientPort = CommonUtils.availablePort()
    createShabondiService(
      ShabondiApi.SHABONDI_SINK_CLASS,
      objectKey,
      clientPort,
      Set(nodeName),
      Set(notStartedTopic)
    )

    an[IllegalArgumentException] should be thrownBy await(shabondiApi.start(objectKey))
  }

  @Test
  def testShouldThrowExceptionIfMultipleNodeNamesWhenSourceStart(): Unit = {
    val notStartedTopic = TopicKey.of("g1", "t1")
    await(topicApi.request.brokerClusterKey(brokerClusterInfo.key).key(notStartedTopic).create())

    createShabondiService(
      ShabondiApi.SHABONDI_SOURCE_CLASS,
      objectKey,
      CommonUtils.availablePort(),
      Set(nodeName),
      Set(notStartedTopic)
    )

    an[IllegalArgumentException] should be thrownBy await(shabondiApi.start(objectKey))
  }

  @Test
  def testShouldThrowExceptionIfMultipleNodeNamesWhenSinkStart(): Unit = {
    val notStartedTopic = TopicKey.of("g1", "t1")
    await(topicApi.request.brokerClusterKey(brokerClusterInfo.key).key(notStartedTopic).create())

    createShabondiService(
      ShabondiApi.SHABONDI_SINK_CLASS,
      objectKey,
      CommonUtils.availablePort(),
      Set(nodeName),
      Set(notStartedTopic)
    )

    an[IllegalArgumentException] should be thrownBy await(shabondiApi.start(objectKey))
  }

  @Test
  def testSourceKind(): Unit = {
    val notStartedTopic = TopicKey.of("g1", "t1")
    await(topicApi.request.brokerClusterKey(brokerClusterInfo.key).key(notStartedTopic).create())
    createShabondiService(
      ShabondiApi.SHABONDI_SOURCE_CLASS,
      objectKey,
      CommonUtils.availablePort(),
      Set(nodeName),
      Set(notStartedTopic)
    ).settings("kind").asInstanceOf[JsString].value shouldBe ClassType.SOURCE.key()
  }

  @Test
  def testSinkKind(): Unit = {
    val notStartedTopic = TopicKey.of("g1", "t1")
    await(topicApi.request.brokerClusterKey(brokerClusterInfo.key).key(notStartedTopic).create())
    createShabondiService(
      ShabondiApi.SHABONDI_SINK_CLASS,
      objectKey,
      CommonUtils.availablePort(),
      Set(nodeName),
      Set(notStartedTopic)
    ).settings("kind").asInstanceOf[JsString].value shouldBe ClassType.SINK.key()
  }

  private def createShabondiService(
    shabondiClass: Class[_],
    key: ObjectKey,
    clientPort: Int,
    nodeNames: Set[String],
    topicKeys: Set[TopicKey]
  ): ShabondiClusterInfo = {
    val request = shabondiApi.request
      .name(key.name)
      .group(key.group)
      .brokerClusterKey(brokerClusterInfo.key)
      .nodeNames(nodeNames) // note: nodeNames only support one node currently.
      .shabondiClass(shabondiClass.getName)
      .clientPort(clientPort)
    shabondiClass match {
      case ShabondiApi.SHABONDI_SOURCE_CLASS => request.sourceToTopics(topicKeys)
      case ShabondiApi.SHABONDI_SINK_CLASS   => request.sinkFromTopics(topicKeys)
      case _                                 => throw new UnsupportedOperationException(s"$shabondiClass is unsupported")
    }
    await(request.create())
  }
}
