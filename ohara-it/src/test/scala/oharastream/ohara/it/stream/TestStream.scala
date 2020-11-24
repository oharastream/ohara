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

package oharastream.ohara.it.stream

import java.io.File
import java.util.concurrent.ExecutionException

import com.typesafe.scalalogging.Logger
import oharastream.ohara.client.configurator.BrokerApi.BrokerClusterInfo
import oharastream.ohara.client.configurator.ClusterState
import oharastream.ohara.common.data.{Row, Serializer}
import oharastream.ohara.common.setting.TopicKey
import oharastream.ohara.common.util.CommonUtils
import oharastream.ohara.it.ContainerPlatform.ResourceRef
import oharastream.ohara.it.{ContainerPlatform, IntegrationTest}
import oharastream.ohara.kafka.Producer
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}
import org.scalatest.matchers.should.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._

@Tag("integration-test-stream")
@EnabledIfEnvironmentVariable(named = "ohara.it.docker", matches = ".*")
class TestStream extends IntegrationTest {
  private[this] val log = Logger(classOf[TestStream])

  private[this] def setup(ref: ResourceRef, nodeName: String): BrokerClusterInfo = {
    // create zookeeper cluster
    log.info("create zkCluster...start")
    val zkCluster = result(
      ref.zookeeperApi.request.key(ref.generateObjectKey).nodeName(nodeName).create()
    )
    result(ref.zookeeperApi.start(zkCluster.key))
    assertCluster(
      () => result(ref.zookeeperApi.list()),
      () => result(ref.containerApi.get(zkCluster.key).map(_.flatMap(_.containers))),
      zkCluster.key
    )
    log.info("create zkCluster...done")

    // create broker cluster
    log.info("create bkCluster...start")
    val bkCluster = result(
      ref.brokerApi.request
        .key(ref.generateObjectKey)
        .zookeeperClusterKey(zkCluster.key)
        .nodeName(nodeName)
        .create()
    )
    result(ref.brokerApi.start(bkCluster.key))
    assertCluster(
      () => result(ref.brokerApi.list()),
      () => result(ref.containerApi.get(bkCluster.key).map(_.flatMap(_.containers))),
      bkCluster.key
    )
    log.info("create bkCluster...done")
    bkCluster
  }

  @ParameterizedTest(name = "{displayName} with {argumentsWithNames}")
  @MethodSource(value = Array("parameters"))
  def testRunSimpleStream(platform: ContainerPlatform): Unit =
    close(platform.setup()) { resourceRef =>
      val brokerClusterInfo = setup(resourceRef, platform.nodeNames.head)
      val from              = TopicKey.of("default", CommonUtils.randomString(5))
      val to                = TopicKey.of("default", CommonUtils.randomString(5))
      val jar               = new File(CommonUtils.path(System.getProperty("user.dir"), "build", "libs", "ohara-it-stream.jar"))

      // create topic
      val topic1 = result(resourceRef.topicApi.request.key(from).brokerClusterKey(brokerClusterInfo.key).create())
      result(resourceRef.topicApi.start(topic1.key))
      val topic2 = result(resourceRef.topicApi.request.key(to).brokerClusterKey(brokerClusterInfo.key).create())
      result(resourceRef.topicApi.start(topic2.key))
      log.info(s"[testRunSimpleStream] topic creation [$topic1,$topic2]...done")

      // upload stream jar
      val jarInfo = result(resourceRef.fileApi.request.file(jar).upload())
      jarInfo.name shouldBe "ohara-it-stream.jar"
      log.info(s"[testRunSimpleStream] upload jar [$jarInfo]...done")

      // create stream properties
      val stream = result(
        resourceRef.streamApi.request
          .key(resourceRef.generateObjectKey)
          .jarKey(jarInfo.key)
          .brokerClusterKey(brokerClusterInfo.key)
          .nodeName(platform.nodeNames.head)
          .fromTopicKey(topic1.key)
          .toTopicKey(topic2.key)
          .create()
      )
      log.info(s"[testRunSimpleStream] stream properties creation [$stream]...done")

      stream.fromTopicKeys shouldBe Set(topic1.key)
      stream.toTopicKeys shouldBe Set(topic2.key)
      stream.state shouldBe None
      stream.error shouldBe None
      log.info(s"[testRunSimpleStream] stream properties update [$stream]...done")

      // get stream property (cluster not create yet, hence no state)
      val getProperties = result(resourceRef.streamApi.get(stream.key))
      getProperties.fromTopicKeys shouldBe Set(topic1.key)
      getProperties.toTopicKeys shouldBe Set(topic2.key)
      getProperties.state shouldBe None
      getProperties.error shouldBe None

      // start stream
      log.info(s"[testRunSimpleStream] stream start [${stream.key}]")
      result(resourceRef.streamApi.start(stream.key))
      await(() => result(resourceRef.streamApi.get(stream.key)).state.contains(ClusterState.RUNNING))
      log.info(s"[testRunSimpleStream] stream start [${stream.key}]...done")

      val res1 = result(resourceRef.streamApi.get(stream.key))
      res1.key shouldBe stream.key
      res1.error shouldBe None

      // check the cluster has the metrics data (each stream cluster has two metrics : IN_TOPIC and OUT_TOPIC)
      await(() => result(resourceRef.streamApi.get(stream.key)).meters.nonEmpty)
      result(resourceRef.streamApi.get(stream.key)).meters.size shouldBe 2

      // write some data into topic
      val producer = Producer
        .builder()
        .connectionProps(brokerClusterInfo.connectionProps)
        .allAcks()
        .keySerializer(Serializer.ROW)
        .valueSerializer(Serializer.BYTES)
        .build()
      try {
        await(
          () => {
            try producer
              .sender()
              .key(Row.EMPTY)
              .value(Array.emptyByteArray)
              .topicKey(topic1.key)
              .send()
              .get()
              .topicKey() == topic1.key
            catch {
              case e: ExecutionException =>
                e.getCause match {
                  case _: UnknownTopicOrPartitionException => false
                  case t: Throwable                        => throw t
                }
            }
          }
        )
      } finally producer.close()

      // wait until the metrics cache data update
      await(() => result(resourceRef.streamApi.get(stream.key)).meters.forall(_.value > 0))

      // check the metrics data again
      val metrics = result(resourceRef.streamApi.get(stream.key)).meters
      metrics.foreach { metric =>
        metric.document should include("the number of rows")
        metric.value shouldBe 1d
      }

      await(() => result(resourceRef.topicApi.get(from)).meters.nonEmpty)
      await(() => result(resourceRef.topicApi.get(to)).meters.nonEmpty)

      //stop stream
      result(resourceRef.streamApi.stop(stream.key))
      await(() => {
        // In configurator mode: clusters() will return the "stopped list" in normal case
        // In collie mode: clusters() will return the "cluster list except stop one" in normal case
        // we should consider these two cases by case...
        val clusters = result(resourceRef.streamApi.list())
        !clusters.map(_.key).contains(stream.key) || clusters.find(_.key == stream.key).get.state.isEmpty
      })
      result(resourceRef.streamApi.get(stream.key)).state.isEmpty shouldBe true

      // after stop stream, property should still exist
      result(resourceRef.streamApi.get(stream.key)).name shouldBe stream.name
    }(_ => ())
}

object TestStream {
  def parameters: java.util.stream.Stream[Arguments] = ContainerPlatform.all.map(o => Arguments.of(o)).asJava.stream()
}
