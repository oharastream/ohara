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

package oharastream.ohara.it.shabondi

import com.typesafe.scalalogging.Logger
import oharastream.ohara.client.configurator.BrokerApi.BrokerClusterInfo
import oharastream.ohara.client.configurator.ShabondiApi.ShabondiClusterInfo
import oharastream.ohara.client.configurator.TopicApi.TopicInfo
import oharastream.ohara.client.configurator.{ClusterInfo, ClusterState, ShabondiApi}
import oharastream.ohara.common.setting.TopicKey
import oharastream.ohara.common.util.CommonUtils
import oharastream.ohara.it.ContainerPlatform.ResourceRef
import oharastream.ohara.it.{ContainerPlatform, IntegrationTest}
import oharastream.ohara.metrics.BeanChannel
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}
import org.scalatest.matchers.should.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

@Tag("integration-test-client")
@EnabledIfEnvironmentVariable(named = "ohara.it.docker", matches = ".*")
class TestShabondi extends IntegrationTest {
  private[this] val log = Logger(classOf[TestShabondi])

  private[this] def setup(resourceRef: ResourceRef): BrokerClusterInfo = {
    // create zookeeper cluster
    log.info("create zkCluster...start")
    val zkCluster = result(
      resourceRef.zookeeperApi.request.key(resourceRef.generateObjectKey).nodeName(resourceRef.nodeNames.head).create()
    )
    result(resourceRef.zookeeperApi.start(zkCluster.key))
    assertCluster(
      () => result(resourceRef.zookeeperApi.list()),
      () => result(resourceRef.containerApi.get(zkCluster.key).map(_.flatMap(_.containers))),
      zkCluster.key
    )
    log.info("create zkCluster...done")

    // create broker cluster
    log.info("create bkCluster...start")
    val bkCluster = result(
      resourceRef.brokerApi.request
        .key(resourceRef.generateObjectKey)
        .zookeeperClusterKey(zkCluster.key)
        .nodeName(resourceRef.nodeNames.head)
        .create()
    )
    result(resourceRef.brokerApi.start(bkCluster.key))
    assertCluster(
      () => result(resourceRef.brokerApi.list()),
      () => result(resourceRef.containerApi.get(bkCluster.key).map(_.flatMap(_.containers))),
      bkCluster.key
    )
    log.info("create bkCluster...done")
    bkCluster
  }

  @ParameterizedTest(name = "{displayName} with {argumentsWithNames}")
  @MethodSource(value = Array("parameters"))
  def testStartAndStopShabondiSource(platform: ContainerPlatform): Unit =
    close(platform.setup()) { resourceRef =>
      val brokerClusterInfo = setup(resourceRef)
      val topic1            = startTopic(brokerClusterInfo, resourceRef)

      // we make sure the broker cluster exists again (for create topic)
      assertCluster(
        () => result(resourceRef.brokerApi.list()),
        () => result(resourceRef.containerApi.get(brokerClusterInfo.key).map(_.flatMap(_.containers))),
        brokerClusterInfo.key
      )
      log.info(s"assert broker cluster [${brokerClusterInfo.key}]...done")

      // ----- create Shabondi Source
      val shabondiSource: ShabondiApi.ShabondiClusterInfo =
        createShabondiService(ShabondiApi.SHABONDI_SOURCE_CLASS, topic1.key, brokerClusterInfo, resourceRef)
      log.info(s"shabondi creation [$shabondiSource]...done")

      // assert Shabondi Source cluster info
      val clusterInfo = result(resourceRef.shabondiApi.get(shabondiSource.key))
      clusterInfo.shabondiClass shouldBe ShabondiApi.SHABONDI_SOURCE_CLASS_NAME
      clusterInfo.sourceToTopics shouldBe Set(topic1.key)
      clusterInfo.state shouldBe None
      clusterInfo.error shouldBe None

      assertStartAndStop(ShabondiApi.SHABONDI_SOURCE_CLASS, shabondiSource, resourceRef)
    }(_ => ())

  @ParameterizedTest(name = "{displayName} with {argumentsWithNames}")
  @MethodSource(value = Array("parameters"))
  def testStartAndStopShabondiSink(platform: ContainerPlatform): Unit =
    close(platform.setup()) { resourceRef =>
      val brokerClusterInfo = setup(resourceRef)
      val topic1            = startTopic(brokerClusterInfo, resourceRef)

      // we make sure the broker cluster exists again (for create topic)
      assertCluster(
        () => result(resourceRef.brokerApi.list()),
        () => result(resourceRef.containerApi.get(brokerClusterInfo.key).map(_.flatMap(_.containers))),
        brokerClusterInfo.key
      )
      log.info(s"assert broker cluster [${brokerClusterInfo.key}]...done")

      // ----- create Shabondi Sink
      val shabondiSink: ShabondiApi.ShabondiClusterInfo =
        createShabondiService(ShabondiApi.SHABONDI_SINK_CLASS, topic1.key, brokerClusterInfo, resourceRef)
      log.info(s"shabondi creation [$shabondiSink]...done")

      // assert Shabondi Sink cluster info
      val clusterInfo = result(resourceRef.shabondiApi.get(shabondiSink.key))
      clusterInfo.shabondiClass shouldBe ShabondiApi.SHABONDI_SINK_CLASS_NAME
      clusterInfo.sinkFromTopics shouldBe Set(topic1.key)
      clusterInfo.state shouldBe None
      clusterInfo.error shouldBe None

      assertStartAndStop(ShabondiApi.SHABONDI_SINK_CLASS, shabondiSink, resourceRef)
    }(_ => ())

  private[this] def startTopic(brokerClusterInfo: BrokerClusterInfo, resourceRef: ResourceRef): TopicInfo = {
    val topic = TopicKey.of("default", CommonUtils.randomString(5))
    val topicInfo = result(
      resourceRef.topicApi.request.key(topic).brokerClusterKey(brokerClusterInfo.key).create()
    )
    result(resourceRef.topicApi.start(topicInfo.key))
    log.info(s"start topic [$topic]...done")
    topicInfo
  }

  private[this] def createShabondiService(
    shabondiClass: Class[_],
    topicKey: TopicKey,
    brokerClusterInfo: BrokerClusterInfo,
    resourceRef: ResourceRef
  ): ShabondiClusterInfo = {
    val request = resourceRef.shabondiApi.request
      .key(resourceRef.generateObjectKey)
      .brokerClusterKey(brokerClusterInfo.key)
      .nodeName(resourceRef.nodeNames.head)
      .shabondiClass(shabondiClass.getName)
      .clientPort(CommonUtils.availablePort())
    shabondiClass match {
      case ShabondiApi.SHABONDI_SOURCE_CLASS => request.sourceToTopics(Set(topicKey))
      case ShabondiApi.SHABONDI_SINK_CLASS   => request.sinkFromTopics(Set(topicKey))
      case _                                 => throw new UnsupportedOperationException(s"$shabondiClass is unsupported")
    }
    result(request.create())
  }

  private[this] def assertStartAndStop(
    shabondiClass: Class[_],
    clusterInfo: ShabondiClusterInfo,
    resourceRef: ResourceRef
  ): Unit = {
    // ---- Start Shabondi service
    result(resourceRef.shabondiApi.start(clusterInfo.key))
    await(() => {
      val resultInfo = result(resourceRef.shabondiApi.get(clusterInfo.key))
      resultInfo.state.contains(ClusterState.RUNNING)
    })

    { // assert Shabondi Source cluster info
      val resultInfo = result(resourceRef.shabondiApi.get(clusterInfo.key))
      resultInfo.shabondiClass shouldBe shabondiClass.getName
      resultInfo.nodeNames shouldBe Set(resourceRef.nodeNames.head)
      resultInfo.aliveNodes shouldBe Set(resourceRef.nodeNames.head)
      resultInfo.deadNodes shouldBe Set.empty
    }

    testJmx(clusterInfo)

    // ---- Stop Shabondi service
    result(resourceRef.shabondiApi.stop(clusterInfo.key))
    await(() => {
      val clusters = result(resourceRef.shabondiApi.list())
      !clusters.map(_.key).contains(clusterInfo.key) ||
      clusters.find(_.key == clusterInfo.key).get.state.isEmpty
    })

    { // assert Shabondi Source cluster info
      val resultInfo = result(resourceRef.shabondiApi.get(clusterInfo.key))
      resultInfo.state shouldBe None
      resultInfo.nodeNames shouldBe Set(resourceRef.nodeNames.head)
      resultInfo.aliveNodes shouldBe Set.empty
      resultInfo.deadNodes shouldBe Set.empty
    }
  }

  private[this] def testJmx(cluster: ClusterInfo): Unit =
    cluster.nodeNames.foreach(
      node =>
        await(
          () =>
            try BeanChannel.builder().hostname(node).port(cluster.jmxPort).build().nonEmpty()
            catch {
              // the jmx service may be not ready.
              case _: Throwable =>
                false
            }
        )
    )
}

object TestShabondi {
  def parameters: java.util.stream.Stream[Arguments] = ContainerPlatform.all.map(o => Arguments.of(o)).asJava.stream()
}
