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

import com.island.ohara.agent.k8s.K8SClient
import com.island.ohara.agent.{ClusterCollie, Crane}
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.common.rule.SmallTest
import com.island.ohara.common.util.CommonUtils
import org.junit.Test
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
class TestFakeConfigurator extends SmallTest with Matchers {

  @Test
  def testFakeCluster(): Unit = {
    Seq(
      (1, 1),
      (1, 2),
      (2, 1),
      (99, 99)
    ).foreach {
      case (numberOfBrokers, numberOfWorkers) =>
        val configurator = Configurator.builder().fake(numberOfBrokers, numberOfWorkers).build()
        try {
          Await.result(configurator.clusterCollie.brokerCollie().clusters, 10 seconds).size shouldBe numberOfBrokers
          Await.result(configurator.clusterCollie.workerCollie().clusters, 10 seconds).size shouldBe numberOfWorkers
          Await
            .result(configurator.clusterCollie.clusters, 10 seconds)
            // one broker generates one zk cluster
            .size shouldBe (numberOfBrokers + numberOfBrokers + numberOfWorkers)
          val nodes = Await.result(configurator.store.values[Node](), 10 seconds)
          nodes.isEmpty shouldBe false
          Await
            .result(configurator.clusterCollie.clusters, 10 seconds)
            .flatMap(_._1.nodeNames)
            .foreach(name => nodes.exists(_.name == name) shouldBe true)
          Await.result(configurator.crane.list, 10 seconds)
        } finally configurator.close()
    }
  }

  @Test
  def createWorkerClusterWithoutBrokerCluster(): Unit = {
    an[IllegalArgumentException] should be thrownBy Configurator.builder().fake(0, 1).build()
  }

  @Test
  def createFakeConfiguratorWithoutClusters(): Unit = {
    val configurator = Configurator.builder().fake(0, 0).build()
    try Await.result(configurator.clusterCollie.clusters, 10 seconds).size shouldBe 0
    finally configurator.close()
  }

  @Test
  def reassignClusterCollieAfterFake(): Unit =
    an[IllegalArgumentException] should be thrownBy Configurator
      .builder()
      // in fake mode, a fake collie will be created
      .fake(1, 1)
      .clusterCollie(MockitoSugar.mock[ClusterCollie])
      .build()

  @Test
  def reassignCraneAfterFake(): Unit =
    an[IllegalArgumentException] should be thrownBy Configurator
      .builder()
      // in fake mode, we use fake docker client
      .fake(1, 1)
      // assign is NOT ok, since crane is defined in fake mode!!!
      .crane(MockitoSugar.mock[Crane])
      .build()

  @Test
  def reassignCrane(): Unit =
    an[IllegalArgumentException] should be thrownBy Configurator
      .builder()
      .crane(MockitoSugar.mock[Crane])
      .crane(MockitoSugar.mock[Crane])
      .build()

  @Test
  def reassignK8sClient(): Unit = an[IllegalArgumentException] should be thrownBy Configurator
    .builder()
    .k8sClient(MockitoSugar.mock[K8SClient])
    .k8sClient(MockitoSugar.mock[K8SClient])
    .build()

  @Test
  def reassignClusterCollie(): Unit = an[IllegalArgumentException] should be thrownBy Configurator
    .builder()
    .clusterCollie(MockitoSugar.mock[ClusterCollie])
    .clusterCollie(MockitoSugar.mock[ClusterCollie])
    .build()

  @Test
  def reassignStoreType(): Unit = an[IllegalArgumentException] should be thrownBy Configurator
    .builder()
    // in fake mode, we have created a store
    .fake(1, 1)
    // you can't change the store type now
    .inMemoryStore()
    .build()

  @Test
  def reassignHomeFolder(): Unit = an[IllegalArgumentException] should be thrownBy Configurator
    .builder()
    // in fake mode, we have created a store
    .fake(1, 1)
    // you can't change the folder of store now
    .homeFolder(CommonUtils.createTempFolder(methodName()).getCanonicalPath)
    .build()
}
