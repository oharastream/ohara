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

package com.island.ohara.configurator

import com.island.ohara.client.configurator.v0.ConnectorApi.State
import com.island.ohara.client.configurator.v0.{ConnectorApi, TopicApi}
import com.island.ohara.client.kafka.WorkerClient
import com.island.ohara.common.util.{CommonUtils, Releasable}
import com.island.ohara.testing.WithBrokerWorker
import org.junit.{After, Test}
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TestControlConnector extends WithBrokerWorker with Matchers {

  private[this] val configurator =
    Configurator.builder.fake(testUtil.brokersConnProps, testUtil().workersConnProps()).build()

  private[this] val connectorApi = ConnectorApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] val topicApi = TopicApi.access.hostname(configurator.hostname).port(configurator.port)

  private[this] def result[T](f: Future[T]): T = Await.result(f, 10 seconds)

  private[this] def await(f: () => Boolean): Unit = CommonUtils.await(() => f(), java.time.Duration.ofSeconds(20))

  @Test
  def testNormalCase(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    val sink = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[DumbSink].getName)
        .numberOfTasks(1)
        .topicKey(topic.key)
        .create())

    // test idempotent start
    result(topicApi.start(topic.key))
    (0 until 3).foreach(_ => result(connectorApi.start(sink.key)))

    val workerClient = WorkerClient(testUtil.workersConnProps)
    try {
      await(
        () =>
          try if (result(workerClient.exist(sink.key))) true else false
          catch {
            case _: Throwable => false
        })
      await(() => result(workerClient.status(sink.key)).connector.state == State.RUNNING.name)
      result(connectorApi.get(sink.key)).status.get.state shouldBe State.RUNNING

      // test idempotent pause
      (0 until 3).foreach(_ => result(connectorApi.pause(sink.key)))
      await(() => result(workerClient.status(sink.key)).connector.state == State.PAUSED.name)
      result(connectorApi.get(sink.key)).status.get.state shouldBe State.PAUSED

      // test idempotent resume
      (0 until 3).foreach(_ => result(connectorApi.resume(sink.key)))
      await(() => result(workerClient.status(sink.key)).connector.state == State.RUNNING.name)
      result(connectorApi.get(sink.key)).status.get.state shouldBe State.RUNNING

      // test idempotent stop. the connector should be removed
      (0 until 3).foreach(_ => result(connectorApi.stop(sink.key)))
      await(() => if (result(workerClient.nonExist(sink.key))) true else false)
      result(connectorApi.get(sink.key)).status shouldBe None
    } finally {
      if (result(workerClient.exist(sink.key))) result(workerClient.delete(sink.key))
    }
  }

  @Test
  def testUpdateRunningConnector(): Unit = {
    val topicName = CommonUtils.randomString(10)
    val topic = result(topicApi.request.name(topicName).create())
    val sink = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[DumbSink].getName)
        .topicKey(topic.key)
        .numberOfTasks(1)
        .create())
    // test start
    result(topicApi.start(topic.key))
    result(connectorApi.start(sink.key))
    val workerClient = WorkerClient(testUtil.workersConnProps)
    try {
      await(
        () =>
          try if (result(workerClient.exist(sink.key))) true else false
          catch {
            case _: Throwable => false
        })
      await(() => result(workerClient.status(sink.key)).connector.state == State.RUNNING.name)

      an[IllegalArgumentException] should be thrownBy result(
        connectorApi.request
          .name(sink.name)
          .group(sink.group)
          .className(classOf[DumbSink].getName)
          .numberOfTasks(1)
          .create())

      // test stop. the connector should be removed
      result(connectorApi.stop(sink.key))
      await(() => if (result(workerClient.nonExist(sink.key))) true else false)
      result(connectorApi.get(sink.key)).status shouldBe None
    } finally if (result(workerClient.exist(sink.key))) result(workerClient.delete(sink.key))
  }

  @Test
  def deleteRunningConnector(): Unit = {
    val topicName = CommonUtils.randomString(10)
    val topic = result(topicApi.request.name(topicName).create())
    val sink = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[DumbSink].getName)
        .topicKey(topic.key)
        .numberOfTasks(1)
        .create())
    // test start
    result(topicApi.start(topic.key))
    result(connectorApi.start(sink.key))
    val workerClient = WorkerClient(testUtil.workersConnProps)
    try {
      await(
        () =>
          try if (result(workerClient.exist(sink.key))) true else false
          catch {
            case _: Throwable => false
        })
      result(workerClient.delete(sink.key))
      result(workerClient.exist(sink.key)) shouldBe false
    } finally if (result(workerClient.exist(sink.key))) result(workerClient.delete(sink.key))
  }

  @Test
  def testNodeName(): Unit = {
    val topic = result(topicApi.request.name(CommonUtils.randomString(10)).create())

    val sink = result(
      connectorApi.request
        .name(CommonUtils.randomString(10))
        .className(classOf[DumbSink].getName)
        .numberOfTasks(1)
        .topicKey(topic.key)
        .create())

    result(topicApi.start(topic.key))
    result(connectorApi.start(sink.key))
    await(() => result(connectorApi.get(sink.key)).status.nonEmpty)
    await(() => result(connectorApi.get(sink.key)).tasksStatus.nonEmpty)
    result(connectorApi.get(sink.key)).status.get.nodeName == CommonUtils.hostname()
    result(connectorApi.get(sink.key)).tasksStatus.foreach(_.nodeName shouldBe CommonUtils.hostname())
  }

  @After
  def tearDown(): Unit = Releasable.close(configurator)
}
