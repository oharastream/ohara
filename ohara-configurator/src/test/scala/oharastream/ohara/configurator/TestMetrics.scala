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

package oharastream.ohara.configurator

import java.util.concurrent.TimeUnit

import oharastream.ohara.client.configurator.ClusterState
import oharastream.ohara.client.configurator.ShabondiApi.ShabondiClusterInfo
import oharastream.ohara.client.configurator.{
  BrokerApi,
  ConnectorApi,
  FileInfoApi,
  PipelineApi,
  ShabondiApi,
  StreamApi,
  TopicApi,
  WorkerApi
}
import oharastream.ohara.common.data.Serializer
import oharastream.ohara.common.setting.{ObjectKey, TopicKey}
import oharastream.ohara.common.util.{CommonUtils, Releasable}
import oharastream.ohara.configurator.route.RouteUtils
import oharastream.ohara.kafka.Producer
import oharastream.ohara.metrics.BeanChannel
import oharastream.ohara.testing.WithBrokerWorker
import org.junit.jupiter.api.{AfterEach, Test}
import org.scalatest.matchers.should.Matchers._
import spray.json.{JsNumber, JsString}

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
class TestMetrics extends WithBrokerWorker {
  private[this] val configurator =
    Configurator.builder.fake(testUtil.brokersConnProps, testUtil().workersConnProps()).build()

  private[this] val connectorApi = ConnectorApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val topicApi     = TopicApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val streamApi    = StreamApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val fileApi      = FileInfoApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val shabondiApi  = ShabondiApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val workerClusterInfo = result(
    WorkerApi.access.hostname(configurator.hostname).port(configurator.port).list()
  ).head

  private[this] val nodeNames = workerClusterInfo.nodeNames

  private[this] def result[T](f: Future[T]): T = Await.result(f, Duration(15, TimeUnit.SECONDS))

  private[this] def awaitTrue(f: () => Boolean, swallowException: Boolean = false): Unit =
    CommonUtils.await(
      () =>
        try f()
        catch {
          case _: Throwable if swallowException =>
            false
        },
      java.time.Duration.ofSeconds(20)
    )

  private[this] def assertNoMetricsForTopic(topicId: String): Unit =
    CommonUtils.await(
      () => BeanChannel.local().topicMeters().asScala.count(_.topicName() == topicId) == 0,
      java.time.Duration.ofSeconds(20)
    )

  private def createTopic(): TopicApi.TopicInfo = {
    result(
      topicApi.request
        .name(CommonUtils.randomString(10))
        .brokerClusterKey(
          result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list()).head.key
        )
        .create()
    )
  }

  @Test
  def testTopic(): Unit = {
    val topic = createTopic()
    result(topicApi.start(topic.key))
    val producer = Producer
      .builder()
      .connectionProps(testUtil().brokersConnProps())
      .keySerializer(Serializer.STRING)
      .valueSerializer(Serializer.STRING)
      .build()
    try {
      producer
        .sender()
        .topicKey(topic.key)
        .key(CommonUtils.randomString())
        .value(CommonUtils.randomString())
        .send()
        .get()
    } finally producer.close()

    CommonUtils.await(
      () => {
        val meters = result(topicApi.get(topic.key)).meters
        // metrics should have queryTime also
        meters.nonEmpty && meters.head.queryTime > 0
      },
      java.time.Duration.ofSeconds(20)
    )

    result(topicApi.stop(topic.key))
    result(topicApi.delete(topic.key))

    assertNoMetricsForTopic(topic.name)
  }

  @Test
  def testConnector(): Unit = {
    val topic = createTopic()
    result(topicApi.start(topic.key))

    val sink = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[FallibleSink].getName)
        .topicKey(topic.key)
        .numberOfTasks(1)
        .workerClusterKey(workerClusterInfo.key)
        .create()
    )

    sink.meters.size shouldBe 0

    result(connectorApi.start(sink.key))

    CommonUtils.await(
      () => {
        val meters = result(connectorApi.get(sink.key)).meters
        // custom metrics should have queryTime and startTime also
        meters.nonEmpty &&
        meters.head.queryTime > 0 &&
        meters.head.startTime.isDefined &&
        meters.head.lastModified.isDefined &&
        meters.head.valueInPerSec.isDefined
      },
      java.time.Duration.ofSeconds(20)
    )

    result(connectorApi.stop(sink.key))

    CommonUtils.await(() => {
      result(connectorApi.get(sink.key)).meters.isEmpty
    }, java.time.Duration.ofSeconds(20))
  }

  @Test
  def testPipeline(): Unit = {
    val topic = createTopic()
    result(topicApi.start(topic.key))

    val sink = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[FallibleSink].getName)
        .topicKey(topic.key)
        .numberOfTasks(1)
        .workerClusterKey(workerClusterInfo.key)
        .create()
    )

    val pipelineApi = PipelineApi.access.hostname(configurator.hostname).port(configurator.port)

    val pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).endpoint(topic).endpoint(sink).create()
    )

    pipeline.objects.filter(_.key == sink.key).head.meters.size shouldBe 0
    result(connectorApi.start(sink.key))

    // the connector is running so we should "see" the beans.
    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.key)).objects.filter(_.key == sink.key).head.meters.nonEmpty,
      java.time.Duration.ofSeconds(20)
    )

    result(connectorApi.stop(sink.key))

    // the connector is stopped so we should NOT "see" the beans.
    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.key)).objects.filter(_.key == sink.key).head.meters.isEmpty,
      java.time.Duration.ofSeconds(20)
    )
  }

  @Test
  def testTopicMeterInPerfSource(): Unit = {
    val topic = createTopic()
    result(topicApi.start(topic.key))

    val source = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className("oharastream.ohara.connector.perf.PerfSource")
        .topicKey(topic.key)
        .numberOfTasks(1)
        .settings(
          Map("perf.batch" -> JsNumber(1), "perf.frequence" -> JsString(java.time.Duration.ofSeconds(1).toString))
        )
        .workerClusterKey(workerClusterInfo.key)
        .create()
    )

    val pipelineApi = PipelineApi.access.hostname(configurator.hostname).port(configurator.port)

    val pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).endpoint(topic).endpoint(source).create()
    )

    pipeline.objects.filter(_.key == source.key).head.meters.size shouldBe 0
    result(connectorApi.start(source.key))

    // the connector is running so we should "see" the beans.
    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.key)).objects.filter(_.key == source.key).head.meters.nonEmpty,
      java.time.Duration.ofSeconds(20)
    )

    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.key)).objects.filter(_.key == topic.key).head.meters.nonEmpty,
      java.time.Duration.ofSeconds(20)
    )

    result(connectorApi.stop(source.key))

    // the connector is stopped so we should NOT "see" the beans.
    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.key)).objects.filter(_.key == source.key).head.meters.isEmpty,
      java.time.Duration.ofSeconds(20)
    )

    // remove topic
    result(connectorApi.delete(source.key))
    CommonUtils.await(
      () => !result(pipelineApi.get(pipeline.key)).objects.exists(_.key == source.key),
      java.time.Duration.ofSeconds(30)
    )
  }

  @Test
  def testStreamMeterInPipeline(): Unit = {
    val wkInfo  = result(configurator.serviceCollie.workerCollie.clusters()).head
    val jar     = RouteUtils.streamFile
    val jarInfo = result(fileApi.request.file(jar).group(wkInfo.name).upload())
    jarInfo.name shouldBe jar.getName

    val t1 = createTopic()
    val t2 = createTopic()
    result(topicApi.start(t1.key))
    result(topicApi.start(t2.key))

    val stream = result(
      streamApi.request
        .jarKey(jarInfo.key)
        .fromTopicKey(t1.key)
        .toTopicKey(t2.key)
        .brokerClusterKey(
          result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list()).head.key
        )
        .nodeNames(nodeNames)
        .create()
    )

    val pipelineApi = PipelineApi.access.hostname(configurator.hostname).port(configurator.port)

    val pipeline = result(
      pipelineApi.request
        .name(CommonUtils.randomString(10))
        .endpoint(t1)
        .endpoint(t2)
        .endpoint(stream)
        .create()
    )

    pipeline.objects.filter(_.key == stream.key).head.meters.size shouldBe 0

    result(streamApi.start(stream.key))
    // the stream is running so we should "see" the beans.
    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.key)).objects.filter(_.key == stream.key).head.meters.nonEmpty,
      java.time.Duration.ofSeconds(20)
    )

    result(streamApi.stop(stream.key))
    // the stream is stopped so we should NOT "see" the beans.
    CommonUtils.await(
      () => result(pipelineApi.get(pipeline.key)).objects.filter(_.key == stream.key).head.meters.isEmpty,
      java.time.Duration.ofSeconds(20)
    )
  }

  @Test
  def testShabondiMeterInPipeline(): Unit = {
    val bkKey
      : ObjectKey = result(BrokerApi.access.hostname(configurator.hostname).port(configurator.port).list()).head.key
    val topic1    = createTopic()
    result(topicApi.start(topic1.key))

    // ----- create Shabondi Source & Sink & Pipeline
    val shabondiSource = createShabondiService(ShabondiApi.SHABONDI_SOURCE_CLASS, bkKey, topic1.key)
    val shabondiSink   = createShabondiService(ShabondiApi.SHABONDI_SINK_CLASS, bkKey, topic1.key)
    // ----- create Pipeline: Shabondi Source --> Topic --> Shabondi Sink
    val pipelineApi = PipelineApi.access.hostname(configurator.hostname).port(configurator.port)
    val pipeline = result(
      pipelineApi.request
        .name(CommonUtils.randomString(10))
        .endpoint(topic1)
        .endpoint(shabondiSource)
        .endpoint(shabondiSink)
        .create()
    )
    val sourceObject = pipeline.objects.filter(_.key == shabondiSource.key).head
    val sinkObject   = pipeline.objects.filter(_.key == shabondiSink.key).head
    sourceObject.meters.size shouldBe 0
    sinkObject.meters.size shouldBe 0

    // ---- Start Shabondi Source & Sink
    result(shabondiApi.start(shabondiSource.key))
    result(shabondiApi.start(shabondiSink.key))

    awaitTrue(() => {
      val clusterInfo1 = result(shabondiApi.get(shabondiSource.key))
      val clusterInfo2 = result(shabondiApi.get(shabondiSink.key))
      clusterInfo1.state.contains(ClusterState.RUNNING) &&
      clusterInfo2.state.contains(ClusterState.RUNNING)
    })

    awaitTrue(() => { // should have meter(fake)
      val objects: Set[PipelineApi.ObjectAbstract] = result(pipelineApi.get(pipeline.key)).objects
      val meters1                                  = objects.filter(_.key == shabondiSource.key).head.meters
      val meters2                                  = objects.filter(_.key == shabondiSink.key).head.meters
      meters1.nonEmpty && meters2.nonEmpty
    })

    // ---- Stop Shabondi Source & Sink
    result(shabondiApi.stop(shabondiSource.key))
    result(shabondiApi.stop(shabondiSink.key))
    awaitTrue(() => { // should not have any meter(fake)
      val objects: Set[PipelineApi.ObjectAbstract] = result(pipelineApi.get(pipeline.key)).objects
      val meters1                                  = objects.filter(_.key == shabondiSource.key).head.meters
      val meters2                                  = objects.filter(_.key == shabondiSink.key).head.meters
      meters1.isEmpty && meters2.isEmpty
    })
  }

  private def createShabondiService(
    shabondiClass: Class[_],
    bkKey: ObjectKey,
    topicKey: TopicKey
  ): ShabondiClusterInfo = {
    val request = shabondiApi.request
      .name(CommonUtils.randomString(10))
      .brokerClusterKey(bkKey)
      .nodeName(nodeNames.head)
      .shabondiClass(shabondiClass.getName)
      .clientPort(CommonUtils.availablePort())
    shabondiClass match {
      case ShabondiApi.SHABONDI_SOURCE_CLASS => request.sourceToTopics(Set(topicKey))
      case ShabondiApi.SHABONDI_SINK_CLASS   => request.sinkFromTopics(Set(topicKey))
      case _                                 => throw new UnsupportedOperationException(s"$shabondiClass is unsupported")
    }
    result(request.create())
  }

  @AfterEach
  def tearDown(): Unit = Releasable.close(configurator)
}
