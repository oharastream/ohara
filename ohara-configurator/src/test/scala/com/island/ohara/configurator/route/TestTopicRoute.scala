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

import com.island.ohara.client.configurator.v0.TopicApi.{Request, TopicInfo, TopicState}
import com.island.ohara.client.configurator.v0.{BrokerApi, TopicApi, ZookeeperApi}
import com.island.ohara.common.rule.OharaTest
import com.island.ohara.common.setting.{ObjectKey, TopicKey}
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.configurator.Configurator
import org.apache.kafka.common.config.TopicConfig
import org.junit.{After, Test}
import org.scalatest.Matchers
import spray.json.{JsNumber, JsString}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
class TestTopicRoute extends OharaTest with Matchers {

  private[this] val configurator = Configurator.builder.fake(1, 0).build()

  private[this] val topicApi = TopicApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def result[T](f: Future[T]): T = Await.result(f, Duration("20 seconds"))

  @Test
  def listTopicDeployedOnNonexistentCluster(): Unit = {
    val topic = result(
      topicApi.request.brokerClusterKey(ObjectKey.of(CommonUtils.randomString(), CommonUtils.randomString())).create())

    result(topicApi.get(topic.key)).key shouldBe topic.key
  }

  @Test
  def test(): Unit = {
    // test add
    result(topicApi.list()).size shouldBe 0
    val name = CommonUtils.randomString(10)
    val numberOfPartitions: Int = 3
    val numberOfReplications: Short = 3
    val response = result(
      topicApi.request
        .name(name)
        .numberOfPartitions(numberOfPartitions)
        .numberOfReplications(numberOfReplications)
        .create())
    response.name shouldBe name
    response.numberOfPartitions shouldBe numberOfPartitions
    response.numberOfReplications shouldBe numberOfReplications

    // test get
    val response2 = result(topicApi.get(response.key))
    response.name shouldBe response2.name
    response.brokerClusterKey shouldBe response2.brokerClusterKey
    response.numberOfPartitions shouldBe response2.numberOfPartitions
    response.numberOfReplications shouldBe response2.numberOfReplications

    // test update
    val numberOfPartitions3: Int = 5
    val response3 = result(topicApi.request.name(name).numberOfPartitions(numberOfPartitions3).update())
    response3.numberOfPartitions shouldBe numberOfPartitions3

    // test delete
    result(topicApi.list()).size shouldBe 1
    result(topicApi.delete(response.key))
    result(topicApi.list()).size shouldBe 0

    // test nonexistent data
    an[IllegalArgumentException] should be thrownBy result(
      topicApi.get(TopicKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5))))
  }

  @Test
  def removeTopicFromNonexistentBrokerCluster(): Unit = {
    val name = CommonUtils.randomString(10)
    val bk = result(configurator.serviceCollie.brokerCollie.clusters()).keys.head
    result(
      topicApi.request
        .name(name)
        .create()
        .flatMap { topicInfo =>
          BrokerApi.access
            .hostname(configurator.hostname)
            .port(configurator.port)
            .stop(bk.key)
            .flatMap(_ => topicApi.delete(topicInfo.key))
        }
        .flatMap(_ => topicApi.list())
        .map(topics => topics.exists(_.name == name))) shouldBe false
  }

  @Test
  def createTopicOnNonexistentCluster(): Unit = {
    // we don't check the existence of broker cluster in creating properties
    val topic = result(
      topicApi.request
        .name(CommonUtils.randomString(10))
        .brokerClusterKey(ObjectKey.of(CommonUtils.randomString(), CommonUtils.randomString()))
        .create())
    an[IllegalArgumentException] should be thrownBy result(topicApi.start(topic.key))
  }

  @Test
  def createTopicWithoutBrokerClusterName(): Unit = {
    val zk = result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).list()).head

    val zk2 = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(zk.nodeNames)
        .create()
    )
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk2.key))

    val bk2 = result(
      BrokerApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .zookeeperClusterKey(zk2.key)
        .nodeNames(zk2.nodeNames)
        .create())
    result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).start(bk2.key))

    an[IllegalArgumentException] should be thrownBy result(topicApi.request.name(CommonUtils.randomString(10)).create())

    result(topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(bk2.key).create())
  }

  @Test
  def testUpdateBrokerClusterName(): Unit = {
    val zk = result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).list()).head

    val zk2 = result(
      ZookeeperApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .nodeNames(zk.nodeNames)
        .create()
    )
    result(ZookeeperApi.access.hostname(configurator.hostname).port(configurator.port).start(zk2.key))

    val bk = result(
      BrokerApi.access
        .hostname(configurator.hostname)
        .port(configurator.port)
        .request
        .name(CommonUtils.randomString(10))
        .zookeeperClusterKey(zk2.key)
        .nodeNames(zk2.nodeNames)
        .create())
    result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).start(bk.key))

    val bks = result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list())
    bks.size shouldBe 2

    val topicInfo = result(topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(bks.head.key).create())

    result(topicApi.request.key(topicInfo.key).brokerClusterKey(bks.last.key).update())
  }

  @Test
  def testPartitions(): Unit = {
    val topic0 = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    // we can't reduce number of partitions
    an[IllegalArgumentException] should be thrownBy result(
      topicApi.request.name(topic0.name).numberOfPartitions(topic0.numberOfPartitions - 1).update())

    val topic1 = result(topicApi.request.name(topic0.name).numberOfPartitions(topic0.numberOfPartitions + 1).update())

    topic0.name shouldBe topic1.name
    topic0.name shouldBe topic1.name
    topic0.numberOfPartitions + 1 shouldBe topic1.numberOfPartitions
    topic0.numberOfReplications shouldBe topic1.numberOfReplications
  }

  @Test
  def testReplications(): Unit = {
    val topicInfo = result(topicApi.request.name(CommonUtils.randomString(10)).numberOfReplications(3).create())
    topicInfo.state shouldBe None

    result(topicApi.start(topicInfo.key))

    // we can't reduce number of replications
    an[IllegalArgumentException] should be thrownBy result(
      topicApi.request
        .key(topicInfo.key)
        .numberOfReplications((topicInfo.numberOfReplications - 1).asInstanceOf[Short])
        .update())

    // we can't add number of replications
    an[IllegalArgumentException] should be thrownBy result(
      topicApi.request
        .key(topicInfo.key)
        .numberOfReplications((topicInfo.numberOfReplications + 1).asInstanceOf[Short])
        .update())

    // pass since we don't make changes on number of replications
    result(
      topicApi.request.name(CommonUtils.randomString(10)).numberOfReplications(topicInfo.numberOfReplications).create())
  }

  @Test
  def duplicateDelete(): Unit =
    (0 to 10).foreach(_ =>
      result(topicApi.delete(TopicKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5)))))

  @Test
  def duplicateUpdate(): Unit =
    (0 to 10).foreach(_ => result(topicApi.request.name(CommonUtils.randomString()).update()))

  @Test
  def testUpdateNumberOfPartitions(): Unit = {
    val numberOfPartitions = 2
    updatePartOfField(
      _.numberOfPartitions(numberOfPartitions),
      topicInfo =>
        topicInfo.copy(settings =
          TopicApi.access.request.settings(topicInfo.settings).numberOfPartitions(numberOfPartitions).creation.settings)
    )
  }

  @Test
  def testUpdateNumberOfReplications(): Unit = {
    val numberOfReplications: Short = 2
    updatePartOfField(
      _.numberOfReplications(numberOfReplications),
      topicInfo =>
        topicInfo.copy(
          settings = TopicApi.access.request
            .settings(topicInfo.settings)
            .numberOfReplications(numberOfReplications)
            .creation
            .settings)
    )
  }

  private[this] def updatePartOfField(req: Request => Request, _expected: TopicInfo => TopicInfo): Unit = {
    val previous = result(
      topicApi.request.name(CommonUtils.randomString()).numberOfReplications(1).numberOfPartitions(1).update())
    val updated = result(req(topicApi.request.name(previous.name)).update())
    val expected = _expected(previous)
    updated.name shouldBe expected.name
    updated.brokerClusterKey shouldBe expected.brokerClusterKey
    updated.numberOfReplications shouldBe expected.numberOfReplications
    updated.numberOfPartitions shouldBe expected.numberOfPartitions
  }

  @Test
  def deleteAnTopicRemovedFromKafka(): Unit = {
    val topicName = CommonUtils.randomString(10)

    val topic = result(topicApi.request.name(topicName).create())

    val brokerClusterInfo = result(
      BrokerApi.access.hostname(configurator.hostname).port(configurator.port).get(topic.brokerClusterKey))
    val topicAdmin = result(configurator.serviceCollie.brokerCollie.topicAdmin(brokerClusterInfo))
    try {
      topicAdmin.delete(topic.key)
      // the topic is removed but we don't throw exception.
      result(topicApi.delete(topic.key))
    } finally topicAdmin.close()
  }

  @Test
  def updateTags(): Unit = {
    val tags = Map(
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsNumber(CommonUtils.randomInteger())
    )
    val topicDesc = result(topicApi.request.tags(tags).create())
    topicDesc.tags shouldBe tags

    val tags2 = Map(
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsNumber(CommonUtils.randomInteger())
    )
    val topicDesc2 = result(topicApi.request.key(topicDesc.key).tags(tags2).update())
    topicDesc2.tags shouldBe tags2

    val topicDesc3 = result(topicApi.request.key(topicDesc.key).update())
    topicDesc3.tags shouldBe tags2

    val topicDesc4 = result(topicApi.request.key(topicDesc.key).tags(Map.empty).update())
    topicDesc4.tags shouldBe Map.empty
  }

  @Test
  def testCustomConfigs(): Unit = {
    val key = TopicConfig.SEGMENT_BYTES_CONFIG
    val value = 1024 * 1024
    val topicDesc = result(topicApi.request.configs(Map(key -> value.toString)).create())
    topicDesc.configs(key) shouldBe value.toString
  }

  @Test
  def testStartAndStop(): Unit = {
    val topicDesc = result(topicApi.request.create())
    topicDesc.state shouldBe None
    result(topicApi.start(topicDesc.key))
    result(topicApi.get(topicDesc.key)).state should not be None
    result(topicApi.stop(topicDesc.key))
    result(topicApi.get(topicDesc.key)).state shouldBe None
  }

  @Test
  def testGroup(): Unit = {
    val group = CommonUtils.randomString(10)
    val topicDesc = result(topicApi.request.group(group).create())
    topicDesc.group shouldBe group
    result(topicApi.list()).size shouldBe 1
    result(topicApi.list()).exists(_.key == topicDesc.key) shouldBe true
  }

  @Test
  def testCreateSameTopicAfterCreateWithoutAction(): Unit = {
    // This is the backward-compatibility test
    val name = CommonUtils.randomString()
    val topic = result(topicApi.request.name(name).create())
    result(topicApi.get(topic.key)).name shouldBe name

    result(topicApi.delete(topic.key))
    result(topicApi.list()).size shouldBe 0

    result(topicApi.request.name(name).create()).name shouldBe name
  }

  @Test
  def testCreateSameTopicAfterCreateWithAction(): Unit = {
    val name = CommonUtils.randomString()
    val topic = result(topicApi.request.name(name).create())
    result(topicApi.start(topic.key))
    val res = result(topicApi.get(topic.key))
    res.name shouldBe name
    res.state.get shouldBe TopicState.RUNNING

    // stop and delete action sequentially should remove the topic totally
    result(topicApi.stop(topic.key))
    result(topicApi.get(topic.key)).state.isEmpty shouldBe true
    result(topicApi.delete(topic.key))
    result(topicApi.list()).size shouldBe 0

    // pass
    result(topicApi.request.name(name).create())
    result(topicApi.start(topic.key))
    result(topicApi.get(topic.key)).state.get shouldBe TopicState.RUNNING

    result(topicApi.stop(topic.key))
    result(topicApi.get(topic.key)).state.isEmpty shouldBe true
  }

  @Test
  def failToDeleteRunningTopic(): Unit = {
    val topic = result(topicApi.request.create())
    result(topicApi.start(topic.key))
    an[IllegalArgumentException] should be thrownBy result(topicApi.delete(topic.key))

    result(topicApi.stop(topic.key))
    result(topicApi.delete(topic.key))
    result(topicApi.list()).exists(_.key == topic.key) shouldBe false
  }

  @Test
  def stopTopicFromStoppingBrokerCluster(): Unit = {
    val topic = result(topicApi.request.create())
    val bk = result(configurator.serviceCollie.brokerCollie.clusters()).keys.head
    result(topicApi.start(topic.key))

    // remove broker cluster
    result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).stop(bk.key))
    result(topicApi.stop(topic.key))
    result(topicApi.delete(topic.key))
  }

  @Test
  def stopTopicFromNonexistentBrokerCluster(): Unit = {
    val topic = result(topicApi.request.create())
    val bk = result(configurator.serviceCollie.brokerCollie.clusters()).keys.head
    result(topicApi.start(topic.key))

    // remove broker cluster
    result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).stop(bk.key))
    result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).delete(bk.key))
    result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list()).size shouldBe 0
    result(topicApi.stop(topic.key))
    result(topicApi.delete(topic.key))
  }

  @Test
  def deleteTopicFromNonexistentBrokerCluster(): Unit = {
    val topic = result(topicApi.request.create())
    val bk = result(configurator.serviceCollie.brokerCollie.clusters()).keys.head
    result(topicApi.start(topic.key))

    // remove broker cluster
    result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).stop(bk.key))
    result(topicApi.delete(topic.key))
  }

  @Test
  def failToUpdateRunningTopic(): Unit = {
    val topic = result(topicApi.request.create())
    result(topicApi.start(topic.key))
    an[IllegalArgumentException] should be thrownBy result(topicApi.request.key(topic.key).update())

    result(topicApi.stop(topic.key))
    // topic is stopped now.
    result(topicApi.request.key(topic.key).update())
  }

  @Test
  def checkDefaultConfigs(): Unit = {
    val topic = result(topicApi.request.create())
    TopicRoute.TOPIC_CUSTOM_CONFIGS.foreach {
      case (k, v) =>
        topic.settings(k) shouldBe v
    }
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
