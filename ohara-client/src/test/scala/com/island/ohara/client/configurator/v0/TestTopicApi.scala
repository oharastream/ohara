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

package com.island.ohara.client.configurator.v0

import com.island.ohara.client.configurator.v0.TopicApi._
import com.island.ohara.common.rule.OharaTest
import com.island.ohara.common.util.CommonUtils
import org.junit.Test
import org.scalatest.Matchers
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
class TestTopicApi extends OharaTest with Matchers {

  @Test
  def nullKeyInGet(): Unit =
    an[NullPointerException] should be thrownBy TopicApi.access.get(null)

  @Test
  def nullKeyInDelete(): Unit =
    an[NullPointerException] should be thrownBy TopicApi.access.delete(null)

  @Test
  def emptyGroup(): Unit = an[IllegalArgumentException] should be thrownBy TopicApi.access.request.group("")

  @Test
  def nullGroup(): Unit = an[NullPointerException] should be thrownBy TopicApi.access.request.group(null)

  @Test
  def ignoreNameOnCreation(): Unit = TopicApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .creation
    .name
    .length should not be 0

  @Test
  def ignoreNameOnUpdate(): Unit = an[NoSuchElementException] should be thrownBy TopicApi.access
    .hostname(CommonUtils.randomString())
    .port(CommonUtils.availablePort())
    .request
    .update()

  @Test
  def emptyName(): Unit = an[IllegalArgumentException] should be thrownBy TopicApi.access.request.name("")

  @Test
  def nullName(): Unit = an[NullPointerException] should be thrownBy TopicApi.access.request.name(null)

  @Test
  def nullBrokerClusterKey(): Unit =
    an[NullPointerException] should be thrownBy TopicApi.access.request.brokerClusterKey(null)

  @Test
  def negativeNumberOfPartitions(): Unit =
    an[IllegalArgumentException] should be thrownBy TopicApi.access.request.numberOfPartitions(-1)

  @Test
  def negativeNumberOfReplications(): Unit =
    an[IllegalArgumentException] should be thrownBy TopicApi.access.request.numberOfReplications(-1)

  @Test
  def parseCreation(): Unit = {
    val brokerClusterName = CommonUtils.randomString()
    val numberOfPartitions = 100
    val numberOfReplications = 10

    val creation = TopicApi.TOPIC_CREATION_FORMAT.read(s"""
                                                           |{
                                                           |}
       """.stripMargin.parseJson)

    creation.group shouldBe GROUP_DEFAULT
    creation.name.length shouldBe LIMIT_OF_KEY_LENGTH / 2
    creation.brokerClusterKey shouldBe None
    creation.numberOfPartitions shouldBe TopicApi.DEFAULT_NUMBER_OF_PARTITIONS
    creation.numberOfReplications shouldBe TopicApi.DEFAULT_NUMBER_OF_REPLICATIONS

    val group = CommonUtils.randomString()
    val name = CommonUtils.randomString()
    val creation2 = TopicApi.TOPIC_CREATION_FORMAT.read(s"""
         |{
         | "$GROUP_KEY": "$group",
         | "$NAME_KEY": "$name",
         | "$BROKER_CLUSTER_KEY_KEY": "$brokerClusterName",
         | "$NUMBER_OF_PARTITIONS_KEY": $numberOfPartitions,
         | "$NUMBER_OF_REPLICATIONS_KEY": $numberOfReplications
         |}
       """.stripMargin.parseJson)

    creation2.group shouldBe group
    creation2.name shouldBe name
    creation2.brokerClusterKey.get.name() shouldBe brokerClusterName
    creation2.numberOfPartitions shouldBe numberOfPartitions
    creation2.numberOfReplications shouldBe numberOfReplications
  }

  @Test
  def parseJsonForUpdate(): Unit = {
    val name = CommonUtils.randomString()
    val brokerClusterName = CommonUtils.randomString()
    val numberOfPartitions = 100
    val numberOfReplications = 10
    val update = TopicApi.TOPIC_UPDATING_FORMAT.read(s"""
                                                                  |{
                                                                  | "$NAME_KEY": "$name",
                                                                  | "$BROKER_CLUSTER_KEY_KEY": "$brokerClusterName",
                                                                  | "$NUMBER_OF_PARTITIONS_KEY": $numberOfPartitions,
                                                                  | "$NUMBER_OF_REPLICATIONS_KEY": $numberOfReplications
                                                                  |}
       """.stripMargin.parseJson)

    update.brokerClusterKey.get.name() shouldBe brokerClusterName
    update.settings(NUMBER_OF_PARTITIONS_KEY) shouldBe JsNumber(numberOfPartitions)
    update.settings(NUMBER_OF_REPLICATIONS_KEY) shouldBe JsNumber(numberOfReplications)

    val update2 = TopicApi.TOPIC_UPDATING_FORMAT.read(s"""
         |{
         |}
       """.stripMargin.parseJson)

    update2.brokerClusterKey shouldBe None
    update2.settings should not contain NUMBER_OF_PARTITIONS_KEY
    update2.settings should not contain NUMBER_OF_REPLICATIONS_KEY
  }

  @Test
  def nullTags(): Unit = an[NullPointerException] should be thrownBy TopicApi.access.request.tags(null)

  @Test
  def emptyTags(): Unit = TopicApi.access.request.tags(Map.empty)

  @Test
  def testNameLimit(): Unit = an[DeserializationException] should be thrownBy
    TopicApi.access
      .hostname(CommonUtils.randomString())
      .port(CommonUtils.availablePort())
      .request
      .name(CommonUtils.randomString(LIMIT_OF_KEY_LENGTH))
      .group(CommonUtils.randomString(LIMIT_OF_KEY_LENGTH))
      .creation

  @Test
  def negativeReplicationsIsIllegalInCreation(): Unit = intercept[DeserializationException] {
    TopicApi.TOPIC_CREATION_FORMAT.read(s"""
                                           |{
                                           |  "$NUMBER_OF_REPLICATIONS_KEY": -1
                                           |}
       """.stripMargin.parseJson)
  }.getMessage should include("negative")

  @Test
  def negativePartitionsIsIllegalInCreation(): Unit = intercept[DeserializationException] {
    TopicApi.TOPIC_CREATION_FORMAT.read(s"""
                                           |{
                                           |  "$NUMBER_OF_PARTITIONS_KEY": -1
                                           |}
       """.stripMargin.parseJson)
  }.getMessage should include("negative")

  @Test
  def negativeReplicationsIsIllegalInUpdating(): Unit = intercept[DeserializationException] {
    TopicApi.TOPIC_UPDATING_FORMAT.read(s"""
                                           |{
                                           |  "$NUMBER_OF_REPLICATIONS_KEY": -1
                                           |}
       """.stripMargin.parseJson)
  }.getMessage should include("negative")

  @Test
  def negativePartitionsIsIllegalInUpdating(): Unit = intercept[DeserializationException] {
    TopicApi.TOPIC_UPDATING_FORMAT.read(s"""
                                           |{
                                           |  "$NUMBER_OF_PARTITIONS_KEY": -1
                                           |}
       """.stripMargin.parseJson)
  }.getMessage should include("negative")

  @Test
  def zeroReplicationsIsIllegalInCreation(): Unit = intercept[DeserializationException] {
    TopicApi.TOPIC_CREATION_FORMAT.read(s"""
                                           |{
                                           |  "$NUMBER_OF_REPLICATIONS_KEY": 0
                                           |}
       """.stripMargin.parseJson)
  }.getMessage should include("negative")

  @Test
  def zeroPartitionsIsIllegalInCreation(): Unit = intercept[DeserializationException] {
    TopicApi.TOPIC_CREATION_FORMAT.read(s"""
                                           |{
                                           |  "$NUMBER_OF_PARTITIONS_KEY": 0
                                           |}
       """.stripMargin.parseJson)
  }.getMessage should include("negative")

  @Test
  def zeroReplicationsIsIllegalInUpdating(): Unit = intercept[DeserializationException] {
    TopicApi.TOPIC_UPDATING_FORMAT.read(s"""
                                           |{
                                           |  "$NUMBER_OF_REPLICATIONS_KEY": 0
                                           |}
       """.stripMargin.parseJson)
  }.getMessage should include("negative")

  @Test
  def zeroPartitionsIsIllegalInUpdating(): Unit = intercept[DeserializationException] {
    TopicApi.TOPIC_UPDATING_FORMAT.read(s"""
                                           |{
                                           |  "$NUMBER_OF_PARTITIONS_KEY": 0
                                           |}
       """.stripMargin.parseJson)
  }.getMessage should include("negative")
}
