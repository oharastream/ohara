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

package com.island.ohara.agent

import com.island.ohara.client.configurator.v0.FileInfoApi.FileInfo
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.client.configurator.v0.StreamApi
import com.island.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import com.island.ohara.common.rule.OharaTest
import com.island.ohara.common.setting.{ObjectKey, TopicKey}
import com.island.ohara.common.util.CommonUtils
import org.junit.Test
import org.scalatest.Matchers._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TestStreamCreator extends OharaTest {

  private[this] def topicKey(): TopicKey = topicKey(CommonUtils.randomString())
  private[this] def topicKey(name: String): TopicKey = TopicKey.of("group", name)

  private[this] def brokerKey(): ObjectKey = ObjectKey.of(CommonUtils.randomString(), CommonUtils.randomString())

  private[this] def streamCreator(): StreamCollie.ClusterCreator = (executionContext, creation) => {
    if (executionContext == null) throw new AssertionError()
    Future.successful {
      StreamClusterInfo(
        settings = creation.settings,
        aliveNodes = Set.empty,
        metrics = Metrics.EMPTY,
        state = None,
        error = None,
        lastModified = CommonUtils.current()
      )
    }
  }

  private[this] def result[T](f: Future[T]): T = Await.result(f, 10 seconds)

  @Test
  def nullClusterName(): Unit = {
    an[NullPointerException] should be thrownBy streamCreator().name(null)
  }

  @Test
  def IllegalClusterName(): Unit = {
    an[DeserializationException] should be thrownBy streamCreator()
      .name("!@#$-")
      .group(CommonUtils.randomString(10))
      .nodeName(CommonUtils.randomString(10))
      .create()
  }

  @Test
  def nullNodeName(): Unit = {
    an[NullPointerException] should be thrownBy streamCreator().nodeNames(null)
  }

  @Test
  def emptyNodeName(): Unit = {
    //TODO We should reject empty nodeNames after #2288
    streamCreator().nodeNames(Set.empty)
  }

  @Test
  def nullGroup(): Unit = {
    an[NullPointerException] should be thrownBy streamCreator().group(null)
  }

  @Test
  def emptyGroup(): Unit = {
    an[IllegalArgumentException] should be thrownBy streamCreator().group("")
  }

  @Test
  def nullSettings(): Unit = an[NullPointerException] should be thrownBy streamCreator().settings(null)

  private[this] def fileInfo: FileInfo = new FileInfo(
    group = CommonUtils.randomString(),
    name = CommonUtils.randomString(),
    bytes = Array.empty,
    url = None,
    lastModified = CommonUtils.current(),
    classInfos = Seq.empty,
    tags = Map.empty
  )

  @Test
  def testNameLength(): Unit = {
    streamCreator()
      .name(CommonUtils.randomString(10))
      .group(CommonUtils.randomString(10))
      .brokerClusterKey(brokerKey())
      .fromTopicKey(topicKey())
      .toTopicKey(topicKey())
      .jmxPort(CommonUtils.availablePort())
      .nodeName(CommonUtils.randomString())
      .jarKey(fileInfo.key)
      .create()

    // name + group length > 100
    an[DeserializationException] should be thrownBy streamCreator()
      .name(CommonUtils.randomString(com.island.ohara.client.configurator.v0.LIMIT_OF_KEY_LENGTH))
      .group(CommonUtils.randomString(10))
      .brokerClusterKey(brokerKey())
      .jarKey(fileInfo.key)
      .fromTopicKey(topicKey())
      .toTopicKey(topicKey())
      .jmxPort(CommonUtils.availablePort())
      .nodeName(CommonUtils.randomString())
      .create()
  }

  @Test
  def testInvalidGroup(): Unit =
    an[DeserializationException] should be thrownBy streamCreator()
      .name(CommonUtils.randomString(10))
      .group(CommonUtils.randomString(com.island.ohara.client.configurator.v0.LIMIT_OF_KEY_LENGTH))
      .nodeName(CommonUtils.randomString())
      .create()

  @Test
  def testCopy(): Unit = {
    val info = StreamClusterInfo(
      settings = StreamApi.access.request
        .nodeNames(Set("n0"))
        .jarKey(ObjectKey.of("g", "f"))
        .brokerClusterKey(brokerKey())
        .fromTopicKey(topicKey())
        .toTopicKey(topicKey())
        .creation
        .settings,
      aliveNodes = Set.empty,
      state = None,
      error = None,
      metrics = Metrics.EMPTY,
      lastModified = CommonUtils.current()
    )

    // pass
    result(streamCreator().settings(info.settings).create())
  }

  @Test
  def testNormalCase(): Unit =
    // could set jmx port
    result(
      streamCreator()
        .name(CommonUtils.randomString(10))
        .group(CommonUtils.randomString(10))
        .brokerClusterKey(brokerKey())
        .jarKey(fileInfo.key)
        .fromTopicKey(topicKey())
        .toTopicKey(topicKey())
        .jmxPort(CommonUtils.availablePort())
        .nodeName(CommonUtils.randomString())
        .create())

  @Test
  def testParseJarKey(): Unit = {
    //a normal url
    val jarInfo = fileInfo
    val res = streamCreator()
      .name(CommonUtils.randomString(10))
      .group(CommonUtils.randomString(10))
      .brokerClusterKey(brokerKey())
      .nodeName(CommonUtils.randomString())
      .fromTopicKey(topicKey())
      .toTopicKey(topicKey())
      .jmxPort(CommonUtils.availablePort())
      .jarKey(jarInfo.key)
      .creation
    res.jarKey.group() shouldBe jarInfo.group
    res.jarKey.name() shouldBe jarInfo.name
  }

  @Test
  def ignoreFromTopic(): Unit =
    streamCreator()
      .name(CommonUtils.randomString(10))
      .group(CommonUtils.randomString(10))
      .brokerClusterKey(brokerKey())
      .jarKey(fileInfo.key)
      .toTopicKey(topicKey())
      .jmxPort(CommonUtils.availablePort())
      .nodeName(CommonUtils.randomString())
      .creation
      .fromTopicKeys shouldBe Set.empty

  @Test
  def ignoreToTopic(): Unit =
    streamCreator()
      .name(CommonUtils.randomString(10))
      .group(CommonUtils.randomString(10))
      .brokerClusterKey(brokerKey())
      .jarKey(fileInfo.key)
      .fromTopicKey(topicKey())
      .jmxPort(CommonUtils.availablePort())
      .nodeName(CommonUtils.randomString())
      .creation
      .toTopicKeys shouldBe Set.empty

  /**
    * the ignored jmx port is replaced by random one.
    */
  @Test
  def ignoreJmxPort(): Unit = CommonUtils.requireConnectionPort(
    streamCreator()
      .name(CommonUtils.randomString(10))
      .group(CommonUtils.randomString(10))
      .brokerClusterKey(brokerKey())
      .jarKey(fileInfo.key)
      .fromTopicKey(topicKey())
      .toTopicKey(topicKey())
      .nodeName(CommonUtils.randomString())
      .creation
      .jmxPort)

}
