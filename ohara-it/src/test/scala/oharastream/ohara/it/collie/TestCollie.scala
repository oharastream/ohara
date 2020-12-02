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

package oharastream.ohara.it.collie

import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.Logger
import oharastream.ohara.client.configurator.BrokerApi.BrokerClusterInfo
import oharastream.ohara.client.configurator.{ClusterInfo, ClusterState}
import oharastream.ohara.client.configurator.VolumeApi.{Volume, VolumeState}
import oharastream.ohara.client.configurator.WorkerApi.WorkerClusterInfo
import oharastream.ohara.client.configurator.ZookeeperApi.ZookeeperClusterInfo
import oharastream.ohara.client.kafka.ConnectorAdmin
import oharastream.ohara.common.data.Serializer
import oharastream.ohara.common.exception.{ExecutionException, TimeoutException}
import oharastream.ohara.common.setting.{ObjectKey, TopicKey}
import oharastream.ohara.common.util.CommonUtils
import oharastream.ohara.it.ContainerPlatform.ResourceRef
import oharastream.ohara.it.connector.{IncludeAllTypesSinkConnector, IncludeAllTypesSourceConnector}
import oharastream.ohara.it.{ContainerPlatform, IntegrationTest}
import oharastream.ohara.kafka.{Consumer, Producer, TopicAdmin}
import oharastream.ohara.metrics.BeanChannel
import org.apache.kafka.common.errors.InvalidReplicationFactorException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}
import org.scalatest.matchers.should.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

/**
  * This abstract class extracts the "required" information of running tests on true env.
  * All checks are verified in this class but we do run all test cases on different test in order to avoid
  * slow test cases run by single test jvm.
  *
  * NOTED: this test will forward random ports so it would be better to "close" firewall of remote node.
  *
  * Noted: this test depends on the ClusterNameHolder which helps us to cleanup all containers for all tests cases.
  * Hence, you don't need to add "finally" hook to do the cleanup.
  */
@Tag("integration-test-collie")
@EnabledIfEnvironmentVariable(named = "ohara.it.docker", matches = ".*")
class TestCollie extends IntegrationTest {
  private[this] val log = Logger(classOf[TestCollie])

  @ParameterizedTest(name = "{displayName} with {argumentsWithNames}")
  @MethodSource(value = Array("parameters"))
  def testMultiClusters(platform: ContainerPlatform): Unit =
    close(platform.setup())(resourceRef => testZookeeperBrokerWorker(2, resourceRef))(_ => ())

  @ParameterizedTest(name = "{displayName} with {argumentsWithNames}")
  @MethodSource(value = Array("parameters"))
  def testMultiNodeVolume(platform: ContainerPlatform): Unit =
    close(platform.setup())(resourceRef => testMultiNodeVolume(resourceRef))(_ => ())

  @ParameterizedTest(name = "{displayName} with {argumentsWithNames}")
  @MethodSource(value = Array("parameters"))
  def testSameNodeDifferentVolume(platform: ContainerPlatform): Unit =
    close(platform.setup())(resourceRef => testSameNodeDifferentVolume(resourceRef))(_ => ())

  /**
    * @param clusterCount how many cluster should be created at same time
    */
  private[this] def testZookeeperBrokerWorker(clusterCount: Int, resourceRef: ResourceRef): Unit = {
    val zookeeperClusterInfos = (0 until clusterCount).map(_ => testZookeeper(resourceRef))
    try {
      val brokerClusterInfos = zookeeperClusterInfos.map { cluster =>
        val volumeName = s"bkvolume${CommonUtils.randomString(5)}"
        checkVolumeNotExists(resourceRef, Seq(volumeName))
        val bkVolumes = Set(
          result(
            resourceRef.volumeApi.request
              .key(resourceRef.generateObjectKey)
              .name(volumeName)
              .nodeNames(Set(resourceRef.nodeNames.head))
              .path(s"/tmp/${CommonUtils.randomString(10)}")
              .create()
          )
        )
        bkVolumes.foreach { bkVolume =>
          result(resourceRef.volumeApi.start(bkVolume.key))
          await(() => result(resourceRef.volumeApi.get(bkVolume.key)).state.contains(VolumeState.RUNNING))
        }
        testBroker(cluster, resourceRef, bkVolumes)
      }
      try {
        val workerClusterInfos = brokerClusterInfos.map(cluster => testWorker(cluster, resourceRef))
        try testNodeServices(zookeeperClusterInfos ++ brokerClusterInfos ++ workerClusterInfos, resourceRef)
        finally workerClusterInfos.foreach(cluster => testStopWorker(cluster, resourceRef))
      } finally brokerClusterInfos.foreach(cluster => testStopBroker(cluster, resourceRef))
    } finally zookeeperClusterInfos.foreach { cluster =>
      testStopZookeeper(cluster, resourceRef)
    }
  }

  private[this] def testNodeServices(clusterInfos: Seq[ClusterInfo], resourceRef: ResourceRef): Unit = {
    val nodes = result(resourceRef.nodeApi.list())
    nodes.size should not be 0
    clusterInfos
      .foreach { clusterInfo =>
        clusterInfo.nodeNames.foreach { name =>
          val services = nodes.find(_.hostname == name).get.services
          services.flatMap(_.clusterKeys) should contain(clusterInfo.key)
        }
      }
  }

  private[this] def testMultiNodeVolume(resourceRef: ResourceRef): Unit = {
    val path = s"/tmp/${CommonUtils.randomString(10)}"
    val name = s"volume${CommonUtils.randomString(5)}"
    checkVolumeNotExists(resourceRef, Seq(name))
    val volume = result(
      resourceRef.volumeApi.request
        .key(resourceRef.generateObjectKey)
        .name(name)
        .nodeNames(resourceRef.nodeNames)
        .path(path)
        .create()
    )
    try {
      result(resourceRef.volumeApi.start(volume.key))
      result(resourceRef.volumeApi.list()).filter(_.name == name).foreach { volume =>
        volume.name shouldBe name
        volume.path shouldBe path
        resourceRef.nodeNames.size shouldBe volume.nodeNames.size
      }
    } finally {
      result(resourceRef.volumeApi.stop(volume.key))   // Delete docker or k8s volume and folder data
      result(resourceRef.volumeApi.delete(volume.key)) // Delete api data for the ohara volume
      checkVolumeNotExists(resourceRef, Seq(name))
    }
  }

  private[this] def testSameNodeDifferentVolume(resourceRef: ResourceRef): Unit = {
    val path  = s"/tmp/${CommonUtils.randomString(10)}"
    val names = Seq(s"volume1${CommonUtils.randomString(5)}", s"volume2${CommonUtils.randomString(5)}")
    checkVolumeNotExists(resourceRef, names)
    val volumes = names.map { name =>
      result(
        resourceRef.volumeApi.request
          .key(resourceRef.generateObjectKey)
          .name(name)
          .nodeNames(Set(resourceRef.nodeNames.head))
          .path(path)
          .create()
      )
    }
    try {
      volumes.foreach { volume =>
        result(resourceRef.volumeApi.start(volume.key))
      }
      result(resourceRef.volumeApi.list()).foreach { volume =>
        names.contains(volume.name) shouldBe true
        volume.path shouldBe path
      }
      result(resourceRef.volumeApi.list()).size shouldBe names.size
    } finally {
      volumes.foreach { volume =>
        result(resourceRef.volumeApi.stop(volume.key))
        result(resourceRef.volumeApi.delete(volume.key))
      }
      checkVolumeNotExists(resourceRef, names)
    }
  }

  private[this] def checkVolumeNotExists(resourceRef: ResourceRef, names: Seq[String]): Unit = {
    names.foreach { volumeName =>
      await(() => !result(resourceRef.volumeApi.list()).exists(_.name == volumeName))
    }
  }

  private[this] def testZookeeper(resourceRef: ResourceRef): ZookeeperClusterInfo = {
    val zookeeperClusterInfo = result(
      resourceRef.zookeeperApi.request
        .key(resourceRef.generateObjectKey)
        .jmxPort(CommonUtils.availablePort())
        .clientPort(CommonUtils.availablePort())
        .electionPort(CommonUtils.availablePort())
        .peerPort(CommonUtils.availablePort())
        .nodeName(resourceRef.nodeNames.head)
        .create()
    )
    result(resourceRef.zookeeperApi.start(zookeeperClusterInfo.key))
    assertCluster(
      () => result(resourceRef.zookeeperApi.list()),
      () => result(resourceRef.containerApi.get(zookeeperClusterInfo.key)).flatMap(_.containers),
      zookeeperClusterInfo.key
    )
    zookeeperClusterInfo
  }

  private[this] def testStopZookeeper(zookeeperClusterInfo: ZookeeperClusterInfo, resourceRef: ResourceRef): Unit = {
    result(resourceRef.zookeeperApi.stop(zookeeperClusterInfo.key))
    await(() => {
      // In configurator mode: clusters() will return the "stopped list" in normal case
      // In collie mode: clusters() will return the "cluster list except stop one" in normal case
      // we should consider these two cases by case...
      val clusters = result(resourceRef.zookeeperApi.list())
      !clusters
        .map(_.key)
        .contains(zookeeperClusterInfo.key) || clusters.find(_.key == zookeeperClusterInfo.key).get.state.isEmpty
    })
    // the cluster is stopped actually, delete the data
    result(resourceRef.zookeeperApi.delete(zookeeperClusterInfo.key))
  }

  private[this] def testBroker(
    zookeeperClusterInfo: ZookeeperClusterInfo,
    resourceRef: ResourceRef,
    volumes: Set[Volume]
  ): BrokerClusterInfo = {
    log.info("[BROKER] start to run broker cluster")
    val clusterKey = resourceRef.generateObjectKey
    log.info(s"[BROKER] verify existence of broker cluster:$clusterKey...done")
    val nodeName: String = resourceRef.nodeNames.head
    val clientPort       = CommonUtils.availablePort()
    val jmxPort          = CommonUtils.availablePort()
    def assert(brokerCluster: BrokerClusterInfo): BrokerClusterInfo = {
      brokerCluster.key shouldBe clusterKey
      brokerCluster.zookeeperClusterKey shouldBe zookeeperClusterInfo.key
      brokerCluster.nodeNames.head shouldBe nodeName
      brokerCluster.clientPort shouldBe clientPort
      brokerCluster.jmxPort shouldBe jmxPort
      brokerCluster
    }
    val brokerClusterInfo = assert(
      result(
        resourceRef.brokerApi.request
          .key(clusterKey)
          .clientPort(clientPort)
          .jmxPort(jmxPort)
          .zookeeperClusterKey(zookeeperClusterInfo.key)
          .nodeName(nodeName)
          .logDirs(volumes.map(_.key))
          .create()
      )
    )
    result(resourceRef.brokerApi.start(brokerClusterInfo.key))
    log.info("[BROKER] start to run broker cluster...done")
    assertCluster(
      () => result(resourceRef.brokerApi.list()),
      () => result(resourceRef.containerApi.get(brokerClusterInfo.key)).flatMap(_.containers),
      brokerClusterInfo.key
    )
    assert(result(resourceRef.brokerApi.get(brokerClusterInfo.key)))
    log.info("[BROKER] verify cluster api...done")
    result(resourceRef.brokerApi.get(brokerClusterInfo.key)).key shouldBe brokerClusterInfo.key
    result(resourceRef.containerApi.get(clusterKey)).flatMap(_.containers).foreach { container =>
      container.nodeName shouldBe nodeName
      container.name.contains(clusterKey.name) shouldBe true
      container.name should not be container.hostname
      container.name.length should be > container.hostname.length
      container.portMappings.size shouldBe 2
      container.portMappings.exists(_.containerPort == clientPort) shouldBe true
    }
    result(resourceRef.logApi.log4BrokerCluster(clusterKey)).logs.size shouldBe 1
    result(resourceRef.logApi.log4BrokerCluster(clusterKey)).logs
      .map(_.value)
      .foreach { log =>
        log.length should not be 0
        log.toLowerCase should not contain "exception"
      }
    log.info("[BROKER] verify:log done")
    var curCluster = brokerClusterInfo
    testTopic(curCluster)
    testJmx(curCluster)
    curCluster = testAddNodeToRunningBrokerCluster(curCluster, resourceRef)
    testTopic(curCluster)
    testJmx(curCluster)
    // we can't remove the node used to set up broker cluster since the internal topic is hosted by it.
    // In this test case, the internal topic has single replication. If we remove the node host the internal topic
    // the broker cluster will be out-of-sync and all data operation will be frozen.
    curCluster = testRemoveNodeToRunningBrokerCluster(curCluster, nodeName, resourceRef)
    testTopic(curCluster)
    testJmx(curCluster)
    brokerClusterInfo
  }

  private[this] def testStopBroker(brokerClusterInfo: BrokerClusterInfo, resourceRef: ResourceRef): Unit = {
    try {
      result(resourceRef.brokerApi.stop(brokerClusterInfo.key))
      await(() => {
        // In configurator mode: clusters() will return the "stopped list" in normal case
        // In collie mode: clusters() will return the "cluster list except stop one" in normal case
        // we should consider these two cases by case...
        val clusters = result(resourceRef.brokerApi.list())
        !clusters
          .map(_.key)
          .contains(brokerClusterInfo.key) || clusters.find(_.key == brokerClusterInfo.key).get.state.isEmpty
      })
      // the cluster is stopped actually, delete the data
      result(resourceRef.brokerApi.delete(brokerClusterInfo.key))
    } finally {
      brokerClusterInfo.volumeMaps.keys.foreach { objectKey =>
        result(resourceRef.volumeApi.stop(objectKey))   // Stop broker volume
        result(resourceRef.volumeApi.delete(objectKey)) // Delete broker volume
      }
    }
  }

  private[this] def testAddNodeToRunningBrokerCluster(
    previousCluster: BrokerClusterInfo,
    resourceRef: ResourceRef
  ): BrokerClusterInfo = {
    await(() => result(resourceRef.brokerApi.list()).exists(_.key == previousCluster.key))
    log.info(s"[BROKER] nodes:${resourceRef.nodeNames} previous:${previousCluster.nodeNames}")
    an[IllegalArgumentException] should be thrownBy result(
      resourceRef.brokerApi.removeNode(previousCluster.key, previousCluster.nodeNames.head)
    )
    val freeNodes = resourceRef.nodeNames.diff(previousCluster.nodeNames)
    if (freeNodes.nonEmpty) {
      await { () =>
        // nothing happens if we add duplicate nodes
        result(resourceRef.brokerApi.addNode(previousCluster.key, previousCluster.nodeNames.head))
        // we can't add a nonexistent node
        // we always get IllegalArgumentException if we sent request by restful api
        // However, if we use collie impl, an NoSuchElementException will be thrown...
        an[Throwable] should be thrownBy result(
          resourceRef.brokerApi.addNode(previousCluster.key, CommonUtils.randomString())
        )
        val newNode = freeNodes.head
        log.info(s"[BROKER] add new node:$newNode to cluster:${previousCluster.key}")

        previousCluster.volumeMaps.keys.foreach { key =>
          await { () =>
            result(resourceRef.volumeApi.addNode(key, newNode))
            result(resourceRef.volumeApi.get(key)).state.contains(VolumeState.RUNNING) &&
            result(resourceRef.volumeApi.get(key)).nodeNames.contains(newNode)
          }
        }
        val newCluster = result(
          resourceRef.brokerApi
            .addNode(previousCluster.key, newNode)
            .flatMap(_ => resourceRef.brokerApi.get(previousCluster.key))
        )
        await(() => newCluster.state.contains(ClusterState.RUNNING))
        log.info(s"[BROKER] add new node:$newNode to cluster:${previousCluster.key}...done")
        newCluster.key shouldBe previousCluster.key
        newCluster.imageName shouldBe previousCluster.imageName
        newCluster.zookeeperClusterKey shouldBe previousCluster.zookeeperClusterKey
        newCluster.clientPort shouldBe previousCluster.clientPort
        newCluster.nodeNames.size - previousCluster.nodeNames.size shouldBe 1
        result(resourceRef.brokerApi.get(newCluster.key)).aliveNodes.contains(newNode)
      }
      result(resourceRef.brokerApi.get(previousCluster.key))
    } else previousCluster
  }

  private[this] def testTopic(cluster: BrokerClusterInfo): Unit = {
    val topicKey = TopicKey.of("g", CommonUtils.randomString())
    val brokers  = cluster.nodeNames.map(_ + s":${cluster.clientPort}").mkString(",")
    log.info(s"[BROKER] start to create topic:$topicKey on broker cluster:$brokers")
    val topicAdmin = TopicAdmin.of(brokers)
    try {
      log.info(s"[BROKER] start to check the sync information. active broker nodes:${cluster.nodeNames}")
      // make sure all active broker nodes are sync!
      await(() => topicAdmin.brokerPorts().toCompletableFuture.get().size() == cluster.nodeNames.size)
      log.info(s"[BROKER] start to check the sync information. active broker nodes:${cluster.nodeNames} ... done")
      log.info(s"[BROKER] number of replications:${cluster.nodeNames.size}")
      await(
        () =>
          try {
            topicAdmin.topicCreator().numberOfPartitions(1).numberOfReplications(1).topicKey(topicKey).create()
            true
          } catch {
            case e: ExecutionException =>
              e.getCause match {
                // the new broker needs time to sync information to other existed bk nodes.
                case _: InvalidReplicationFactorException => false
                case _: Throwable                         => throw e.getCause
              }
            case e: TimeoutException =>
              log.error(s"[BROKER] create topic error ${e.getCause}")
              false
          }
      )
      log.info(s"[BROKER] start to create topic:$topicKey on broker cluster:$brokers ... done")
      val producer = Producer
        .builder()
        .connectionProps(brokers)
        .allAcks()
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.STRING)
        .build()
      val numberOfRecords = 5
      log.info(s"[BROKER] start to send $numberOfRecords data")
      (0 until numberOfRecords).foreach(
        index => producer.sender().key(index.toString).value(index.toString).topicKey(topicKey).send()
      )
      producer.flush()
      producer.close()
      log.info(s"[BROKER] start to send data ... done")
      log.info(s"[BROKER] start to receive data")
      val consumer = Consumer
        .builder()
        .connectionProps(brokers)
        .offsetFromBegin()
        .topicKey(topicKey)
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.STRING)
        .build()
      try {
        val records = consumer.poll(Duration.ofSeconds(30), numberOfRecords)
        records.size() shouldBe numberOfRecords
        records.stream().forEach(record => record.key().get() shouldBe record.value().get())
        log.info(s"[BROKER] start to receive data ... done")
      } finally consumer.close()
    } finally topicAdmin.close()
  }

  private[this] def testRemoveNodeToRunningBrokerCluster(
    previousCluster: BrokerClusterInfo,
    excludedNode: String,
    resourceRef: ResourceRef
  ): BrokerClusterInfo = {
    await(() => result(resourceRef.brokerApi.list()).exists(_.key == previousCluster.key))
    if (previousCluster.nodeNames.size > 1) {
      val beRemovedNode: String = previousCluster.nodeNames.filter(_ != excludedNode).head
      await { () =>
        log.info(
          s"[BROKER] start to remove node:$beRemovedNode from bk cluster:${previousCluster.key} nodes:${previousCluster.nodeNames
            .mkString(",")}"
        )
        result(resourceRef.brokerApi.removeNode(previousCluster.key, beRemovedNode))
        log.info(s"[BROKER] start to remove node:$beRemovedNode from bk cluster:${previousCluster.key} ... done")
        val newCluster = result(resourceRef.brokerApi.get(previousCluster.key))
        newCluster.key == previousCluster.key &&
        newCluster.imageName == previousCluster.imageName &&
        newCluster.zookeeperClusterKey == previousCluster.zookeeperClusterKey &&
        newCluster.clientPort == previousCluster.clientPort &&
        previousCluster.nodeNames.size - newCluster.nodeNames.size == 1 &&
        !result(resourceRef.brokerApi.get(newCluster.key)).aliveNodes.contains(beRemovedNode)
      }
      result(resourceRef.brokerApi.get(previousCluster.key))
    } else previousCluster
  }

  private[this] def testWorker(brokerClusterInfo: BrokerClusterInfo, resourceRef: ResourceRef): WorkerClusterInfo = {
    log.info("[WORKER] start to test worker")
    val nodeName   = resourceRef.nodeNames.head
    val clusterKey = resourceRef.generateObjectKey
    log.info("[WORKER] verify:nonExists done")
    val clientPort = CommonUtils.availablePort()
    val jmxPort    = CommonUtils.availablePort()
    def assert(workerCluster: WorkerClusterInfo): WorkerClusterInfo = {
      workerCluster.brokerClusterKey shouldBe brokerClusterInfo.key
      workerCluster.key shouldBe clusterKey
      workerCluster.nodeNames.head shouldBe nodeName
      workerCluster.clientPort shouldBe clientPort
      workerCluster.jmxPort shouldBe jmxPort
      workerCluster.configTopicPartitions shouldBe 1
      workerCluster.configTopicReplications shouldBe 1
      workerCluster.statusTopicPartitions shouldBe 1
      workerCluster.statusTopicReplications shouldBe 1
      workerCluster.offsetTopicPartitions shouldBe 1
      workerCluster.offsetTopicReplications shouldBe 1
      workerCluster
    }
    log.info("[WORKER] create ...")

    val workerClusterInfo = assert(
      result(
        resourceRef.workerApi.request
          .key(clusterKey)
          .clientPort(clientPort)
          .jmxPort(jmxPort)
          .brokerClusterKey(brokerClusterInfo.key)
          .nodeName(nodeName)
          .create()
      )
    )
    log.info("[WORKER] create done")
    result(resourceRef.workerApi.start(workerClusterInfo.key))
    log.info("[WORKER] start done")
    assertCluster(
      () => result(resourceRef.workerApi.list()),
      () => result(resourceRef.containerApi.get(workerClusterInfo.key)).flatMap(_.containers),
      workerClusterInfo.key
    )
    log.info("[WORKER] check existence")
    assert(result(resourceRef.workerApi.get(workerClusterInfo.key)))
    log.info("[WORKER] verify:create done")
    result(resourceRef.workerApi.get(workerClusterInfo.key)).key shouldBe workerClusterInfo.key
    log.info("[WORKER] verify:exist done")
    // we can't assume the size since other tests may create zk cluster at the same time
    result(resourceRef.workerApi.list()).isEmpty shouldBe false
    log.info("[WORKER] verify:list done")
    result(resourceRef.containerApi.get(clusterKey)).flatMap(_.containers).foreach { container =>
      container.nodeName shouldBe nodeName
      container.name.contains(clusterKey.name) shouldBe true
      container.name should not be container.hostname
      container.name.length should be > container.hostname.length
      // [BEFORE] ServiceCollieImpl applies --network=host to all worker containers so there is no port mapping.
      // The following checks are disabled rather than deleted since it seems like a bug if we don't check the port mapping.
      // [AFTER] ServiceCollieImpl use bridge network now
      container.portMappings.size shouldBe 2
      container.portMappings.exists(_.containerPort == clientPort) shouldBe true
    }
    val logs = result(resourceRef.logApi.log4WorkerCluster(clusterKey)).logs.map(_.value)
    logs.size shouldBe 1
    logs.foreach { log =>
      log.length should not be 0
      log.toLowerCase should not contain "- ERROR"
    // we cannot assume "k8s get logs" are complete since log may rotate
    // so log could be empty in k8s environment
    // also see : https://github.com/kubernetes/kubernetes/issues/11046#issuecomment-121140315
    }
    log.info("[WORKER] verify:log done")
    var curCluster = workerClusterInfo
    testConnectors(curCluster)
    testJmx(curCluster)
    curCluster = testAddNodeToRunningWorkerCluster(curCluster, resourceRef)
    testConnectors(curCluster)
    testJmx(curCluster)
    // The issue of inconsistent settings after adding/removing nodes.
    // This issue is going to resolve in #2677.
    curCluster = testRemoveNodeToRunningWorkerCluster(curCluster, nodeName, resourceRef)
    testConnectors(curCluster)
    testJmx(curCluster)
    workerClusterInfo
  }

  private[this] def testStopWorker(workerClusterInfo: WorkerClusterInfo, resourceRef: ResourceRef): Unit = {
    result(resourceRef.workerApi.stop(workerClusterInfo.key))
    await(() => {
      // In configurator mode: clusters() will return the "stopped list" in normal case
      // In collie mode: clusters() will return the "cluster list except stop one" in normal case
      // we should consider these two cases by case...
      val clusters = result(resourceRef.workerApi.list())
      !clusters
        .map(_.key)
        .contains(workerClusterInfo.key) || clusters.find(_.key == workerClusterInfo.key).get.state.isEmpty
    })
    // the cluster is stopped actually, delete the data
    result(resourceRef.workerApi.delete(workerClusterInfo.key))
  }

  private[this] def testConnectors(cluster: WorkerClusterInfo): Unit =
    await(
      () =>
        try {
          log.info(s"worker node head: ${cluster.nodeNames.head}:${cluster.clientPort}")
          result(ConnectorAdmin(cluster).connectorDefinitions()).nonEmpty
        } catch {
          case e: Throwable =>
            log.info(s"[WORKER] worker cluster:${cluster.name} is starting ... retry", e)
            false
        }
    )

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

  private[this] def testAddNodeToRunningWorkerCluster(
    previousCluster: WorkerClusterInfo,
    resourceRef: ResourceRef
  ): WorkerClusterInfo = {
    await(() => result(resourceRef.workerApi.list()).exists(_.key == previousCluster.key))
    an[IllegalArgumentException] should be thrownBy result(
      resourceRef.workerApi.removeNode(previousCluster.key, previousCluster.nodeNames.head)
    )
    val freeNodes = resourceRef.nodeNames.diff(previousCluster.nodeNames)
    if (freeNodes.nonEmpty) {
      val newNode = freeNodes.head
      // it is ok to add duplicate nodes
      result(resourceRef.workerApi.addNode(previousCluster.key, previousCluster.nodeNames.head))
      // we can't add a nonexistent node
      // we always get IllegalArgumentException if we sent request by restful api
      // However, if we use collie impl, an NoSuchElementException will be thrown...
      an[Throwable] should be thrownBy result(
        resourceRef.workerApi.addNode(previousCluster.key, CommonUtils.randomString())
      )
      log.info(s"[WORKER] start to add node:$newNode to a running worker cluster")
      val newCluster = result(
        resourceRef.workerApi
          .addNode(previousCluster.key, newNode)
          .flatMap(_ => resourceRef.workerApi.get(previousCluster.key))
      )
      newCluster.key shouldBe previousCluster.key
      newCluster.imageName shouldBe previousCluster.imageName
      newCluster.configTopicName shouldBe previousCluster.configTopicName
      newCluster.statusTopicName shouldBe previousCluster.statusTopicName
      newCluster.offsetTopicName shouldBe previousCluster.offsetTopicName
      newCluster.groupId shouldBe previousCluster.groupId
      newCluster.brokerClusterKey shouldBe previousCluster.brokerClusterKey
      newCluster.clientPort shouldBe previousCluster.clientPort
      newCluster.nodeNames.size - previousCluster.nodeNames.size shouldBe 1
      await(() => result(resourceRef.workerApi.get(newCluster.key)).nodeNames.contains(newNode))
      newCluster
    } else previousCluster
  }

  private[this] def testRemoveNodeToRunningWorkerCluster(
    previousCluster: WorkerClusterInfo,
    excludedNode: String,
    resourceRef: ResourceRef
  ): WorkerClusterInfo = {
    await(() => result(resourceRef.workerApi.list()).exists(_.key == previousCluster.key))
    if (previousCluster.nodeNames.size > 1) {
      val beRemovedNode = previousCluster.nodeNames.filter(_ != excludedNode).head
      await(() => {
        log.info(s"[WORKER] start to remove node:$beRemovedNode from ${previousCluster.key}")
        result(resourceRef.workerApi.removeNode(previousCluster.key, beRemovedNode))
        log.info(s"[WORKER] start to remove node:$beRemovedNode from ${previousCluster.key} ... done")
        val newCluster = result(resourceRef.workerApi.get(previousCluster.key))
        newCluster.key == previousCluster.key &&
        newCluster.imageName == previousCluster.imageName &&
        newCluster.configTopicName == previousCluster.configTopicName &&
        newCluster.statusTopicName == previousCluster.statusTopicName &&
        newCluster.offsetTopicName == previousCluster.offsetTopicName &&
        newCluster.groupId == previousCluster.groupId &&
        newCluster.brokerClusterKey == previousCluster.brokerClusterKey &&
        newCluster.clientPort == previousCluster.clientPort &&
        previousCluster.nodeNames.size - newCluster.nodeNames.size == 1 &&
        !result(resourceRef.workerApi.get(newCluster.key)).nodeNames.contains(beRemovedNode)
      })
      result(resourceRef.workerApi.get(previousCluster.key))
    } else previousCluster
  }

  @ParameterizedTest(name = "{displayName} with {argumentsWithNames}")
  @MethodSource(value = Array("parameters"))
  def testLoadCustomJar(platform: ContainerPlatform): Unit =
    close(platform.setup()) { resourceRef =>
      val currentPath = new File(".").getCanonicalPath
      // Both jars are pre-generated. see readme in test/resources

      // jars should use "group" (here is worker name) to identify which worker cluster will use it
      val jars = result(
        Future.traverse(
          Seq(
            new File(currentPath, "build/libs/ohara-it-source.jar"),
            new File(currentPath, "build/libs/ohara-it-sink.jar")
          )
        )(file => {
          // avoid too "frequently" create group folder for same group files
          TimeUnit.SECONDS.sleep(1)
          resourceRef.fileApi.request.file(file).upload()
        })
      )

      val zkCluster = result(
        resourceRef.zookeeperApi.request
          .key(resourceRef.generateObjectKey)
          .nodeName(platform.nodeNames.head)
          .create()
          .flatMap(
            info => resourceRef.zookeeperApi.start(info.key).flatMap(_ => resourceRef.zookeeperApi.get(info.key))
          )
      )
      assertCluster(
        () => result(resourceRef.zookeeperApi.list()),
        () => result(resourceRef.containerApi.get(zkCluster.key).map(_.flatMap(_.containers))),
        zkCluster.key
      )
      log.info(s"zkCluster:$zkCluster")
      val bkCluster = result(
        resourceRef.brokerApi.request
          .key(resourceRef.generateObjectKey)
          .zookeeperClusterKey(zkCluster.key)
          .nodeName(platform.nodeNames.head)
          .create()
          .flatMap(info => resourceRef.brokerApi.start(info.key).flatMap(_ => resourceRef.brokerApi.get(info.key)))
      )
      assertCluster(
        () => result(resourceRef.brokerApi.list()),
        () => result(resourceRef.containerApi.get(bkCluster.key).map(_.flatMap(_.containers))),
        bkCluster.key
      )
      log.info(s"bkCluster:$bkCluster")
      val wkCluster = result(
        resourceRef.workerApi.request
          .key(resourceRef.generateObjectKey)
          .brokerClusterKey(bkCluster.key)
          .pluginKeys(jars.map(jar => ObjectKey.of(jar.group, jar.name)).toSet)
          .nodeName(platform.nodeNames.head)
          .create()
      )
      result(resourceRef.workerApi.start(wkCluster.key))
      assertCluster(
        () => result(resourceRef.workerApi.list()),
        () => result(resourceRef.containerApi.get(wkCluster.key).map(_.flatMap(_.containers))),
        wkCluster.key
      )
      // add all remaining node to the running worker cluster
      platform.nodeNames.diff(wkCluster.nodeNames).foreach { hostname =>
        result(resourceRef.workerApi.addNode(wkCluster.key, hostname))
      }
      // make sure all workers have loaded the test-purposed connector.
      result(resourceRef.workerApi.list()).find(_.name == wkCluster.name).get.nodeNames.foreach { _ =>
        val connectorAdmin = ConnectorAdmin(wkCluster)

        await(
          () =>
            try {
              result(connectorAdmin.connectorDefinition(classOf[IncludeAllTypesSinkConnector].getName))
              result(connectorAdmin.connectorDefinition(classOf[IncludeAllTypesSourceConnector].getName))
              true
            } catch {
              case _: Throwable => false
            }
        )
      }
      await(() => {
        val connectors = result(resourceRef.inspectApi.workerInfo(wkCluster.key)).classInfos
        connectors.map(_.className).contains(classOf[IncludeAllTypesSinkConnector].getName) &&
        connectors.map(_.className).contains(classOf[IncludeAllTypesSourceConnector].getName)
      })
    }(_ => ())
}

object TestCollie {
  def parameters: java.util.stream.Stream[Arguments] = ContainerPlatform.all.map(o => Arguments.of(o)).asJava.stream()
}
