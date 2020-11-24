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

import oharastream.ohara.client.configurator.ShabondiApi.ShabondiClusterInfo
import oharastream.ohara.client.configurator._
import oharastream.ohara.common.rule.OharaTest
import oharastream.ohara.common.setting.{ConnectorKey, ObjectKey, TopicKey}
import oharastream.ohara.common.util.{CommonUtils, Releasable}
import oharastream.ohara.configurator.{Configurator, FallibleSink}
import org.junit.jupiter.api.{AfterEach, Test}
import org.scalatest.matchers.should.Matchers._
import spray.json.{JsArray, JsNumber, JsObject, JsString, JsTrue}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

// there are too many test cases in this file so we promote  it from small test to medium test
class TestPipelineRoute extends OharaTest {
  private[this] val configurator = Configurator.builder.fake(1, 1).build()

  private[this] val fileApi   = FileInfoApi.access.hostname(configurator.hostname).port(configurator.port)
  private[this] val brokerApi = BrokerApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val pipelineApi = PipelineApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val connectorApi = ConnectorApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val topicApi = TopicApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val streamApi = StreamApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val shabondiApi = ShabondiApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val workerClusterInfo = result(
    WorkerApi.access.hostname(configurator.hostname).port(configurator.port).list()
  ).head

  private[this] val nodeNames = workerClusterInfo.nodeNames

  private[this] def result[T](f: Future[T]): T = Await.result(f, Duration("30 seconds"))

  @Test
  def testEndpointAndObjects(): Unit = {
    val topic = result(
      topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(result(brokerApi.list()).head.key).create()
    )
    result(topicApi.start(topic.key))

    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[FallibleSink].getName)
        .numberOfTasks(1)
        .topicKey(topic.key)
        .workerClusterKey(workerClusterInfo.key)
        .create()
    )
    result(connectorApi.start(connector.key))

    var pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).endpoints(Seq(connector, topic)).create()
    )

    pipeline.endpoints.size shouldBe 2
    pipeline.endpoints.map(_.key) should contain(connector.key)
    pipeline.endpoints.map(_.key) should contain(topic.key)
    pipeline.objects.size shouldBe 2

    result(connectorApi.stop(connector.key))
    result(connectorApi.delete(connector.key))

    pipeline = result(pipelineApi.get(pipeline.key))

    // connector is gone
    pipeline.objects.size shouldBe 1

    val pipelines = result(pipelineApi.list())

    pipelines.size shouldBe 1
    pipelines.head.endpoints.size shouldBe 2
    // topic is gone
    pipelines.head.objects.size shouldBe 1

    // remove broker cluster to make topic fail to update state
    val bk = result(brokerApi.list()).head.key

    // we can't remove the broker cluster via APIs since our strong check obstruct us from entering dangerous deletion
    result(configurator.serviceCollie.brokerCollie.remove(bk)) shouldBe true

    // broker cluster is gone so the object abstract should contain error
    pipeline = result(pipelineApi.get(pipeline.key))
    // topic is gone
    pipeline.objects.size shouldBe 1
    pipeline.objects.head.error should not be None
  }

  @Test
  def testAddStreamToPipeline(): Unit = {
    // create worker
    val fileInfo = result(fileApi.request.file(RouteUtils.streamFile).upload())
    val bk       = result(brokerApi.list()).head
    val from     = result(topicApi.request.brokerClusterKey(bk.key).create())
    result(topicApi.start(from.key))
    val to = result(topicApi.request.brokerClusterKey(bk.key).create())
    result(topicApi.start(to.key))

    // create an empty stream
    val stream = result(
      streamApi.request
        .fromTopicKey(from.key)
        .toTopicKey(to.key)
        .brokerClusterKey(bk.key)
        .jarKey(fileInfo.key)
        .nodeNames(nodeNames)
        .create()
    )

    val pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).endpoint(stream).create()
    )
    pipeline.endpoints.size shouldBe 1
    pipeline.endpoints.map(_.key) should contain(stream.key)
    pipeline.objects.size shouldBe 1
    pipeline.objects.head.className should not be None
    pipeline.objects.head.error shouldBe None
    pipeline.jarKeys.size shouldBe 1
    pipeline.jarKeys.head shouldBe fileInfo.key
  }

  @Test
  def addMultiPipelines(): Unit = {
    val topic0 = result(
      topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(result(brokerApi.list()).head.key).create()
    )

    val topic1 = result(
      topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(result(brokerApi.list()).head.key).create()
    )

    val pipeline0 = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).endpoints(Seq(topic0, topic1)).create()
    )

    val pipeline1 = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).endpoints(Seq(topic0, topic1)).create()
    )

    val pipelines = result(pipelineApi.list())
    pipelines.size shouldBe 2
    pipelines.exists(_.name == pipeline0.name) shouldBe true
    pipelines.exists(_.name == pipeline1.name) shouldBe true
  }

  @Test
  def listConnectorWhichIsNotRunning(): Unit = {
    val topic = result(
      topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(result(brokerApi.list()).head.key).create()
    )

    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[FallibleSink].getName)
        .numberOfTasks(1)
        .workerClusterKey(workerClusterInfo.key)
        .topicKey(topic.key)
        .create()
    )

    val pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).endpoints(Seq(connector, topic)).create()
    )

    pipeline.objects.size shouldBe 2
    pipeline.objects.foreach { obj =>
      obj.error shouldBe None
      obj.state shouldBe None
    }
  }

  @Test
  def testRunningConnector(): Unit = {
    val topic = result(
      topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(result(brokerApi.list()).head.key).create()
    )
    result(topicApi.start(topic.key))
    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[FallibleSink].getName)
        .numberOfTasks(1)
        .topicKey(topic.key)
        .workerClusterKey(workerClusterInfo.key)
        .create()
    )

    result(connectorApi.start(connector.key))

    val pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).endpoints(Seq(connector, connector)).create()
    )

    // duplicate object is removed
    pipeline.objects.size shouldBe 1
    pipeline.objects.foreach { obj =>
      obj.error shouldBe None
      obj.state should not be None
    }
  }

  @Test
  def nonexistentConnectorClass(): Unit = {
    val topic = result(
      topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(result(brokerApi.list()).head.key).create()
    )
    result(topicApi.start(topic.key))

    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .numberOfTasks(1)
        .topicKey(topic.key)
        .workerClusterKey(workerClusterInfo.key)
        .create()
    )
    result(connectorApi.start(connector.key))

    val pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).endpoints(Seq(connector, topic)).create()
    )

    pipeline.objects.size shouldBe 2
    pipeline.objects.filter(_.name == connector.name).foreach { obj =>
      obj.error.isEmpty shouldBe false
      obj.state shouldBe None
    }
  }

  @Test
  def duplicateDelete(): Unit =
    (0 to 10).foreach(
      _ => result(pipelineApi.delete(ObjectKey.of(CommonUtils.randomString(5), CommonUtils.randomString(5))))
    )

  @Test
  def duplicateUpdate(): Unit = {
    val count = 10
    (0 until count).foreach(
      _ => result(pipelineApi.request.name(CommonUtils.randomString(10)).endpoints(Seq.empty).update())
    )
    result(pipelineApi.list()).size shouldBe count
  }

  @Test
  def updatingNonexistentNameCanNotIgnoreEndpoints(): Unit = {
    val name     = CommonUtils.randomString(10)
    val pipeline = result(pipelineApi.request.name(name).endpoints(Seq.empty).update())
    result(pipelineApi.list()).size shouldBe 1
    pipeline.name shouldBe name
    pipeline.endpoints shouldBe Set.empty
  }

  @Test
  def updateOnlyEndpoint(): Unit = {
    val topic = result(
      topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(result(brokerApi.list()).head.key).create()
    )
    val pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).endpoint(topic).update()
    )
    pipeline.endpoints.size shouldBe 1
    pipeline.endpoints.map(_.key) should contain(topic.key)

    val pipeline2 = result(pipelineApi.request.key(pipeline.key).endpoints(Seq.empty).update())
    result(pipelineApi.list()).size shouldBe 1
    pipeline2.name shouldBe pipeline.name
    pipeline2.endpoints shouldBe Set.empty
  }

  @Test
  def testDuplicateObjectName(): Unit = {
    val name  = CommonUtils.randomString(10)
    val topic = result(topicApi.request.name(name).brokerClusterKey(result(brokerApi.list()).head.key).create())

    val connector = result(
      connectorApi.request
        .name(name)
        .className(classOf[FallibleSink].getName)
        .topicKey(topic.key)
        .numberOfTasks(1)
        .workerClusterKey(workerClusterInfo.key)
        .create()
    )

    val pipeline = result(
      pipelineApi.request.name(CommonUtils.randomString(10)).endpoints(Seq(topic, connector)).create()
    )
    pipeline.endpoints.size shouldBe 2
    pipeline.objects.size shouldBe 2
  }

  @Test
  def updateTags(): Unit = {
    val tags = Map(
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsNumber(CommonUtils.randomInteger())
    )
    val pipelineDesc = result(pipelineApi.request.tags(tags).create())
    pipelineDesc.tags shouldBe tags

    val tags2 = Map(
      CommonUtils.randomString(10) -> JsString(CommonUtils.randomString(10)),
      CommonUtils.randomString(10) -> JsNumber(CommonUtils.randomInteger())
    )
    val pipelineDesc2 = result(pipelineApi.request.name(pipelineDesc.name).tags(tags2).update())
    pipelineDesc2.tags shouldBe tags2

    val pipelineDesc3 = result(pipelineApi.request.name(pipelineDesc.name).update())
    pipelineDesc3.tags shouldBe tags2

    val pipelineDesc4 = result(pipelineApi.request.name(pipelineDesc.name).tags(Map.empty).update())
    pipelineDesc4.tags shouldBe Map.empty
  }

  @Test
  def testGroup(): Unit = {
    // default group
    result(pipelineApi.request.create()).group shouldBe oharastream.ohara.client.configurator.GROUP_DEFAULT

    val group   = CommonUtils.randomString(10)
    val ftpInfo = result(pipelineApi.request.group(group).create())
    ftpInfo.group shouldBe group

    result(pipelineApi.list()).size shouldBe 2

    // update an existent object
    result(pipelineApi.request.group(ftpInfo.group).name(ftpInfo.name).update())

    result(pipelineApi.list()).size shouldBe 2

    // update an nonexistent (different group) object
    val group2 = CommonUtils.randomString(10)
    result(pipelineApi.request.group(group2).name(ftpInfo.name).create()).group shouldBe group2

    result(pipelineApi.list()).size shouldBe 3
  }

  @Test
  def testDuplicateObjects(): Unit = {
    val topic = result(
      topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(result(brokerApi.list()).head.key).create()
    )

    val pipeline = result(
      pipelineApi.request.endpoints(Seq(topic, topic, topic, topic)).create()
    )

    pipeline.endpoints.size shouldBe 1
    pipeline.objects.size shouldBe 1
    pipeline.objects.head.key shouldBe topic.key
  }

  @Test
  def connectorObjectAlwaysCarryClassName(): Unit = {
    val topic = result(
      topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(result(brokerApi.list()).head.key).create()
    )
    val className = CommonUtils.randomString()
    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(className)
        .workerClusterKey(workerClusterInfo.key)
        .topicKey(topic.key)
        .create()
    )
    val pipeline = result(pipelineApi.request.endpoints(Seq(connector, topic)).create())
    pipeline.objects.size shouldBe 2
    pipeline.objects.find(_.key == connector.key).get.className shouldBe Some(className)
  }

  @Test
  def testRefresh(): Unit = {
    val topic = result(
      topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(result(brokerApi.list()).head.key).create()
    )
    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(CommonUtils.randomString(10))
        .workerClusterKey(workerClusterInfo.key)
        .topicKey(topic.key)
        .create()
    )
    val pipeline = result(
      pipelineApi.request
        .endpoints(Seq(topic, connector))
        .create()
    )
    pipeline.objects.size shouldBe 2

    result(connectorApi.delete(connector.key))
    result(pipelineApi.refresh(pipeline.key))

    // the topic is removed so the endpoint "connector" should be removed
    result(pipelineApi.get(pipeline.key)).endpoints.size shouldBe 1
    result(pipelineApi.get(pipeline.key)).endpoints.map(_.key) should contain(topic.key)
    result(pipelineApi.get(pipeline.key)).objects.size shouldBe 1
  }

  @Test
  def testTopicState(): Unit = {
    val topic = result(
      topicApi.request.name(CommonUtils.randomString(10)).brokerClusterKey(result(brokerApi.list()).head.key).create()
    )
    val pipeline = result(pipelineApi.request.endpoint(topic).create())
    pipeline.objects.size shouldBe 1
    // the topic is not running so no state is responded
    pipeline.objects.flatMap(_.state).size shouldBe 0

    result(topicApi.start(topic.key))

    result(pipelineApi.get(pipeline.key)).objects.head.state.get shouldBe "RUNNING"
  }

  @Test
  def testNameFilter(): Unit = {
    val name     = CommonUtils.randomString(10)
    val topic    = result(topicApi.request.brokerClusterKey(result(brokerApi.list()).head.key).create())
    val pipeline = result(pipelineApi.request.name(name).endpoint(topic).create())
    (0 until 3).foreach(_ => result(pipelineApi.request.endpoint(topic).create()))
    result(pipelineApi.list()).size shouldBe 4
    val pipelines = result(pipelineApi.query.name(name).execute())
    pipelines.size shouldBe 1
    pipelines.head.key shouldBe pipeline.key
  }

  @Test
  def testGroupFilter(): Unit = {
    val group    = CommonUtils.randomString(10)
    val topic    = result(topicApi.request.brokerClusterKey(result(brokerApi.list()).head.key).create())
    val pipeline = result(pipelineApi.request.group(group).endpoint(topic).create())
    (0 until 3).foreach(_ => result(pipelineApi.request.endpoint(topic).create()))
    result(pipelineApi.list()).size shouldBe 4
    val pipelines = result(pipelineApi.query.group(group).execute())
    pipelines.size shouldBe 1
    pipelines.head.key shouldBe pipeline.key
  }

  @Test
  def testTagsFilter(): Unit = {
    val tags = Map(
      "a" -> JsString("b"),
      "b" -> JsNumber(123),
      "c" -> JsTrue,
      "d" -> JsArray(JsString("B")),
      "e" -> JsObject("a" -> JsNumber(123))
    )
    val topic    = result(topicApi.request.brokerClusterKey(result(brokerApi.list()).head.key).create())
    val pipeline = result(pipelineApi.request.tags(tags).endpoint(topic).create())
    (0 until 3).foreach(_ => result(pipelineApi.request.endpoint(topic).create()))
    result(pipelineApi.list()).size shouldBe 4
    val pipelines = result(pipelineApi.query.tags(tags).execute())
    pipelines.size shouldBe 1
    pipelines.head.key shouldBe pipeline.key
  }

  @Test
  def testEndpoint(): Unit = {
    val topic    = result(topicApi.request.brokerClusterKey(result(brokerApi.list()).head.key).create())
    val pipeline = result(pipelineApi.request.endpoint(topic).create())
    pipeline.endpoints.size shouldBe 1
    pipeline.objects.size shouldBe 1
    pipeline.objects.head.group shouldBe topic.group
    pipeline.objects.head.name shouldBe topic.name
    pipeline.objects.head.kind shouldBe topic.kind
    pipeline.objects.head.lastModified shouldBe topic.lastModified
    pipeline.objects.head.tags shouldBe topic.tags
  }

  @Test
  def testRefreshEndpoint(): Unit = {
    val topic0   = result(topicApi.request.brokerClusterKey(result(brokerApi.list()).head.key).create())
    val topic1   = result(topicApi.request.brokerClusterKey(result(brokerApi.list()).head.key).create())
    val pipeline = result(pipelineApi.request.endpoint(topic0).endpoint(topic1).create())
    pipeline.endpoints.size shouldBe 2
    pipeline.objects.size shouldBe 2
    result(topicApi.delete(topic0.key))
    result(pipelineApi.refresh(pipeline.key))
    result(pipelineApi.get(pipeline.key)).endpoints.size shouldBe 1
    result(pipelineApi.get(pipeline.key)).objects.size shouldBe 1
  }

  @Test
  def testDuplicateAbstractions(): Unit = {
    val topic = result(topicApi.request.brokerClusterKey(result(brokerApi.list()).head.key).create())
    val connector = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[FallibleSink].getName)
        .numberOfTasks(1)
        .topicKey(topic.key)
        .workerClusterKey(workerClusterInfo.key)
        .key(ConnectorKey.of(topic.key.group(), topic.key.name()))
        .create()
    )
    connector.key shouldBe topic.key
    val pipeline = result(pipelineApi.request.endpoint(topic).create())
    pipeline.endpoints.size shouldBe 1
    pipeline.objects.size shouldBe 1
    pipeline.objects.head.kind shouldBe TopicApi.KIND
  }

  @Test
  def testAddShabondiSourceAndSinkToPipeline(): Unit = {
    val bk    = result(brokerApi.list()).head
    val topic = result(topicApi.request.brokerClusterKey(bk.key).create())
    result(topicApi.start(topic.key))

    // Create shabondi and pipeline
    val shabondiSource = createShabondiService(ShabondiApi.SHABONDI_SOURCE_CLASS, bk.key, topic.key)
    val shabondiSink   = createShabondiService(ShabondiApi.SHABONDI_SINK_CLASS, bk.key, topic.key)

    val pipeline = result(
      pipelineApi.request
        .name(CommonUtils.randomString(10))
        .endpoint(topic)
        .endpoint(shabondiSource)
        .endpoint(shabondiSink)
        .create()
    )

    pipeline.endpoints.size shouldBe 3
    pipeline.objects.size shouldBe 3
    pipeline.objects.foreach { obj =>
      obj.kind match {
        case "topic"    => obj.state shouldBe Some("RUNNING")
        case "shabondi" => obj.state shouldBe None
        case k          => fail(s"Invalid object kind: $k")
      }
    }
    pipeline.endpoints.map(_.key) should contain(shabondiSource.key)
    pipeline.endpoints.map(_.key) should contain(shabondiSink.key)

    // Start shabondi and pipeline
    result(shabondiApi.start(shabondiSource.key))
    result(shabondiApi.start(shabondiSink.key))
    val pipeline1: PipelineApi.Pipeline = result(pipelineApi.list()).head

    pipeline1.objects.size shouldBe 3
    pipeline1.objects.foreach { obj =>
      obj.state shouldBe Some("RUNNING")
    }
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
