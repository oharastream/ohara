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

package oharastream.ohara.agent

import java.util.Objects

import oharastream.ohara.agent.Collie.ClusterCreator
import oharastream.ohara.agent.container.{ContainerClient, ContainerName, ContainerVolume}
import oharastream.ohara.client.configurator.BrokerApi.BrokerClusterInfo
import oharastream.ohara.client.configurator.ContainerApi.ContainerInfo
import oharastream.ohara.client.configurator.MetricsApi.{Meter, Metrics}
import oharastream.ohara.client.configurator.NodeApi.Node
import oharastream.ohara.client.configurator.ShabondiApi.ShabondiClusterInfo
import oharastream.ohara.client.configurator.StreamApi.StreamClusterInfo
import oharastream.ohara.client.configurator.VolumeApi.Volume
import oharastream.ohara.client.configurator.WorkerApi.WorkerClusterInfo
import oharastream.ohara.client.configurator.ZookeeperApi.ZookeeperClusterInfo
import oharastream.ohara.client.configurator.{ClusterInfo, ClusterRequest, ClusterState}
import oharastream.ohara.common.annotations.Optional
import oharastream.ohara.common.setting.ObjectKey
import oharastream.ohara.common.util.CommonUtils
import oharastream.ohara.metrics.BeanChannel
import oharastream.ohara.metrics.basic.CounterMBean
import oharastream.ohara.metrics.kafka.TopicMeter

import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Collie is a cute dog helping us to "manage" a bunch of sheep.
  */
trait Collie {
  /**
    * @return internal container client
    */
  protected def containerClient: ContainerClient

  /**
    * remove whole cluster by specified key. The process, mostly, has a graceful shutdown
    * which can guarantee the data consistency. However, the graceful downing whole cluster may take some time...
    *
    * @param key cluster key
    * @param executionContext thread pool
    * @return true if it does remove a running cluster. Otherwise, false
    */
  final def remove(key: ObjectKey)(implicit executionContext: ExecutionContext): Future[Boolean] =
    clusters().flatMap(_.find(_.key == key).fold(Future.successful(false)) { cluster =>
      doRemove(cluster, cluster.containers)
        .map(_ => true)
    })

  /**
    * remove whole cluster gracefully.
    * @param clusterInfo cluster info
    * @param beRemovedContainer the container to remove
    * @param executionContext thread pool
    * @return true if success. otherwise false
    */
  protected def doRemove(clusterInfo: ClusterStatus, beRemovedContainer: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext
  ): Future[Unit]

  /**
    * This method open a door to sub class to implement a force remove which kill whole cluster without graceful shutdown.
    * NOTED: The default implementation is reference to graceful remove.
    * @param key cluster key
    * @param executionContext thread pool
    * @return true if it does remove a running cluster. Otherwise, false
    */
  final def forceRemove(key: ObjectKey)(implicit executionContext: ExecutionContext): Future[Boolean] =
    clusters().flatMap(_.find(_.key == key).fold(Future.successful(false)) { cluster =>
      doForceRemove(cluster, cluster.containers)
        .map(_ => true)
    })

  /**
    * remove whole cluster forcely. the impl, by default, is similar to doRemove().
    * @param clusterInfo cluster info
    * @param containerInfos containers info
    * @param executionContext thread pool
    * @return true if success. otherwise false
    */
  protected def doForceRemove(clusterInfo: ClusterStatus, containerInfos: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext
  ): Future[Unit] = doRemove(clusterInfo, containerInfos)

  /**
    * get logs from all containers.
    * NOTED: It is ok to get logs from a "dead" cluster.
    * @param key cluster key
    * @return all log content from cluster. Each container has a log.
    */
  def log(key: ObjectKey, sinceSeconds: Option[Long])(
    implicit executionContext: ExecutionContext
  ): Future[Map[ContainerName, String]] =
    cluster(key)
      .map(_.containers)
      .flatMap(Future.traverse(_)(container => containerClient.log(container.name, sinceSeconds)))
      .map(_.flatten.toMap)

  /**
    * create a cluster creator. The subclass should have following rules.
    * 1) create an new cluster if there is no specific cluster
    * 2) add new nodes to a running cluster if there is a running cluster and the new nodes is different from running nodes
    * for example, running nodes are a0 and a1, and the new settings carries a1 and a2. The sub class should assume user
    * want to add an new node "a2" to the running cluster.
    * 3) otherwise, does nothing.
    *
    * However, it probably throw exception in facing 2) condition if the cluster does NOT support to add node at runtime
    * (for example, zookeeper).
    * @return creator of cluster
    */
  def creator: ClusterCreator

  /**
    * the actual method to prcess the creation.
    * Noted the input arguments are checked
    * @param executionContext thread pool
    * @param containerInfo expected container info
    * @param node node
    * @param routes routes
    * @param arguments argument
    * @param volumeMaps the volumes mounted to this service
    * @return async call
    */
  protected def doCreator(
    executionContext: ExecutionContext,
    containerInfo: ContainerInfo,
    node: Node,
    routes: Map[String, String],
    arguments: Seq[String],
    volumeMaps: Map[Volume, String]
  ): Future[Unit]

  /**
    * invoked after all containers are created.
    * It is useful to commit something to collie impl. for example, update cache
    * @param clusterStatus current cluster status
    * @param existentNodes the origin nodes
    * @param routes new routes
    * @param volumeMaps the volumes mounted to this service
    * @param executionContext thread pool
    * @return async call
    */
  protected def postCreate(
    clusterStatus: ClusterStatus,
    existentNodes: Map[Node, ContainerInfo],
    routes: Map[String, String],
    volumeMaps: Map[Volume, String]
  )(implicit executionContext: ExecutionContext): Future[Unit]

  /**
    * fetch all clusters and belonging containers from cache.
    * Note: this function will only get running containers
    *
    * @param executionContext execution context
    * @return cluster and containers information
    */
  def clusters()(implicit executionContext: ExecutionContext): Future[Seq[ClusterStatus]]

  /**
    * get the cluster information from a cluster
    * @param key cluster key
    * @return cluster information
    */
  def cluster(key: ObjectKey)(implicit executionContext: ExecutionContext): Future[ClusterStatus] =
    clusters().map(
      _.find(_.key == key)
        .getOrElse(throw new NoSuchClusterException(s"$kind cluster with objectKey [$key] is not running"))
    )

  /**
    * @param key cluster key
    * @return true if the cluster exists
    */
  def exist(key: ObjectKey)(implicit executionContext: ExecutionContext): Future[Boolean] =
    clusters().map(_.exists(_.key == key))

  /**
    * @param key cluster key
    * @return true if the cluster doesn't exist
    */
  def nonExist(key: ObjectKey)(implicit executionContext: ExecutionContext): Future[Boolean] =
    exist(key).map(!_)

  /**
    * remove a node from a running cluster.
    * NOTED: this is a async operation since graceful downing a node from a running service may be slow.
    * NOTED: the cluster is gone if there is only one instance.
    * @param key cluster key
    * @param nodeName node name
    * @return true if it does remove a node from a running cluster. Otherwise, false
    */
  final def removeNode(key: ObjectKey, nodeName: String)(implicit executionContext: ExecutionContext): Future[Boolean] =
    clusters().flatMap(
      _.find(_.key == key)
        .map { cluster =>
          cluster.containers
            .find(_.nodeName == nodeName)
            .map(container => doRemove(cluster, Seq(container)).map(_ => true))
            .getOrElse(Future.successful(false))
        }
        .getOrElse(Future.successful(false))
    )

  /**
    * Get the cluster state by containers.
    * <p>
    * Note: we should separate the implementation from docker and k8s environment.
    * <p>
    * a cluster state machine:
    *       -----------------       -----------------       --------
    *       | Some(PENDING) |  -->  | Some(RUNNING) |  -->  | None |
    *       -----------------       -----------------       --------
    *                                      |
    *                                      | (terminated failure or running failure)
    *                                      |       ----------------
    *                                      ----->  | Some(FAILED) |
    *                                              ----------------
    * The cluster state rules
    * 1) RUNNING: all of the containers have been created and at least one container is in "running" state
    * 2) FAILED: all of the containers are terminated and at least one container has terminated failure
    * 3) PENDING: one of the containers are in creating phase
    * 4) UNKNOWN: other situations
    * 4) None: no containers
    *
    * @param containers container list
    * @return the cluster state
    */
  protected def toClusterState(containers: Seq[ContainerInfo]): Option[ClusterState]

  /**
    * all ssh collies share the single cache, and we need a way to distinguish the difference status from different
    * services. Hence, this implicit field is added to cache to find out the cached data belonging to this collie.
    * @return service name
    */
  def kind: ClusterKind

  /**
    * the store used by this colle.
    * @return data collie
    */
  def dataCollie: DataCollie

  /**
    * the fake mode take the metrics from local jvm.
    */
  protected def topicMeters(cluster: ClusterInfo): Map[String, Seq[TopicMeter]] = cluster match {
    case _: BrokerClusterInfo =>
      cluster.aliveNodes.map { hostname =>
        hostname -> BeanChannel.builder().hostname(hostname).port(cluster.jmxPort).build().topicMeters().asScala.toSeq
      }.toMap
    case _ => Map.empty
  }

  /**
    * the fake mode take the metrics from local jvm.
    */
  protected def counterMBeans(cluster: ClusterInfo): Map[String, Seq[CounterMBean]] = cluster match {
    case _: BrokerClusterInfo =>
      /**
        * the metrics we fetch from kafka are only topic metrics so we skip the other beans
        */
      Map.empty
    case _ =>
      cluster.aliveNodes.map { hostname =>
        hostname -> BeanChannel.builder().hostname(hostname).port(cluster.jmxPort).build().counterMBeans().asScala.toSeq
      }.toMap
  }

  /**
    * Get all counter beans from cluster
    * @param key cluster key
    * @return counter beans. the key is mapped to the instance name and value is the meter
    */
  final def metrics(
    key: ObjectKey
  )(implicit executionContext: ExecutionContext): Future[Map[String, Map[ObjectKey, Metrics]]] =
    cluster(key)
      .flatMap { status =>
        (status.kind match {
          case ClusterKind.ZOOKEEPER =>
            dataCollie.value[ZookeeperClusterInfo](key).map(_.copy(aliveNodes = status.aliveNodes))
          case ClusterKind.BROKER =>
            dataCollie.value[BrokerClusterInfo](key).map(_.copy(aliveNodes = status.aliveNodes))
          case ClusterKind.WORKER =>
            dataCollie.value[WorkerClusterInfo](key).map(_.copy(aliveNodes = status.aliveNodes))
          case ClusterKind.STREAM =>
            dataCollie.value[StreamClusterInfo](key).map(_.copy(aliveNodes = status.aliveNodes))
          case ClusterKind.SHABONDI =>
            dataCollie.value[ShabondiClusterInfo](key).map(_.copy(aliveNodes = status.aliveNodes))
        }).map { clusterInfo =>
          counterMBeans(clusterInfo)
            .map {
              case (hostname, counters) =>
                hostname -> counters.groupBy(_.key()).map {
                  case (key, counters) =>
                    key -> Metrics(counters.map { counter =>
                      Meter(
                        name = counter.item,
                        value = counter.getValue.toDouble,
                        unit = counter.getUnit,
                        document = counter.getDocument,
                        queryTime = counter.getQueryTime,
                        startTime = Some(counter.getStartTime),
                        lastModified = Some(counter.getLastModified),
                        valueInPerSec = Some(counter.valueInPerSec())
                      )
                    })
                }
            } ++ topicMeters(clusterInfo)
            .map {
              case (hostname, meters) =>
                hostname -> meters.groupBy(_.topicName()).flatMap {
                  case (plainName, meters) =>
                    val key = ObjectKey.ofPlain(plainName)
                    if (key.isPresent) Some(key.get() -> Metrics(meters.map { meter =>
                      Meter(
                        name = meter.catalog().name(),
                        value = meter.count().toDouble,
                        unit = s"${meter.eventType()} / ${meter.rateUnit().name()}",
                        document = meter.catalog.name(),
                        queryTime = meter.queryTime(),
                        startTime = None,
                        lastModified = None,
                        valueInPerSec = None
                      )
                    }))
                    else None
                }
            }
        }
      }
  //---------------------------[helper methods]---------------------------//

  /**
    * used to resolve the hostNames.
    * @param hostNames hostNames
    * @return hostname -> ip address
    */
  protected def resolveHostNames(hostNames: Set[String]): Map[String, String] =
    hostNames.map(hostname => hostname -> resolveHostName(hostname)).toMap

  /**
    * used to resolve the hostname.
    * @param hostname hostname
    * @return ip address or hostname (if you do nothing to it)
    */
  protected def resolveHostName(hostname: String): String = CommonUtils.address(hostname)

  /**
    * parse the status of cluster from the containers
    * @param key cluster key
    * @param containers cluster's containers
    * @return cluster status
    */
  private[agent] def toStatus(key: ObjectKey, containers: Seq[ContainerInfo]): ClusterStatus = ClusterStatus(
    group = key.group(),
    name = key.name(),
    containers = containers,
    kind = kind,
    state = toClusterState(containers),
    // TODO how could we fetch the error?...by Sam
    error = None
  )

  /**
    * Convert to actually volume name for the volume map from the containerClient
    * @param node
    * @param volumeMaps
    * @param executionContext
    * @return
    */
  protected def actuallyVolumeMap(node: Node, volumeMaps: Map[Volume, String])(
    implicit executionContext: ExecutionContext
  ): Future[Map[Volume, String]] =
    containerClient
      .volumes()
      .map { volumes =>
        volumeMaps.map[Volume, String] {
          case (key: Volume, value: String) =>
            (
              volumes
                .filter(volume => volume.name.split("-").length == 3) // ${ClusterName}-${VolumeName}-${Hash}
                .map(
                  volume =>
                    ContainerVolume(containerVolumeName(volume.name), volume.driver, volume.path, volume.nodeName)
                )
                .find(volume => volume.nodeName == node.name && prefixNameEqualsKey(volume.name, key.name))
                .map(
                  volume =>
                    Volume(
                      group = key.group,
                      name = volume.name,
                      nodeNames = key.nodeNames,
                      path = key.path,
                      state = key.state,
                      error = key.error,
                      tags = key.tags,
                      lastModified = key.lastModified
                    )
                )
                .getOrElse(throw new IllegalArgumentException(s"${key.name} volume is not found!")),
              value
            )
        }
      }

  private[this] def containerVolumeName(name: String): String = {
    val splits = name.split("-")
    s"${splits(1)}-${splits(2)}" // The name variable value is ${group}-${name}-${hash} convert to ${name}-${hash}
  }
  private[this] def prefixNameEqualsKey(name: String, key: String): Boolean = name.startsWith(key)
}

object Collie {
  /**
    * used to distinguish the cluster name and service name
    */
  private[this] val DIVIDER: String  = "-"
  private[agent] val UNKNOWN: String = "unknown"

  private[agent] val LENGTH_OF_CONTAINER_HASH: Int = 7

  /**
    * docker has limit on length of hostname.
    * The max length of hostname is 63 and we try to keep flexibility for the future.
    */
  private[agent] val LENGTH_OF_CONTAINER_HOSTNAME: Int = 20

  /**
    * generate unique name for the container.
    * It can be used in setting container's hostname and name
    * @param group cluster group
    * @param clusterName cluster name
    * @param kind the service type name for current cluster
    * @return a formatted string. form: {prefixKey}-{group}-{clusterName}-{service}-{index}
    */
  def containerName(group: String, clusterName: String, kind: ClusterKind): String = {
    def rejectDivider(s: String): String =
      if (s.contains(DIVIDER))
        throw new IllegalArgumentException(s"$DIVIDER is protected word!!! input:$s")
      else s

    Seq(
      rejectDivider(group),
      rejectDivider(clusterName),
      rejectDivider(kind.toString.toLowerCase()),
      CommonUtils.randomString(LENGTH_OF_CONTAINER_HASH)
    ).mkString(DIVIDER)
  }

  /**
    * generate unique host name for the container. the hostname, normally, is used internally so it is ok to generate
    * a random string. However, we all hate to see something hard to read so the hostname is similar to container name.
    * The main difference is that the length of hostname is shorter as the limit of hostname.
    * @param group cluster group
    * @param clusterName cluster name
    * @param kind the service type name for current cluster
    * @return a formatted string. form: {prefixKey}-{group}-{clusterName}-{service}-{index}
    */
  def containerHostName(group: String, clusterName: String, kind: ClusterKind): String = {
    val name = containerName(group, clusterName, kind)
    if (name.length > LENGTH_OF_CONTAINER_HOSTNAME) {
      val rval = name.substring(name.length - LENGTH_OF_CONTAINER_HOSTNAME)
      // avoid creating name starting with "DIVIDER"
      if (rval.startsWith(DIVIDER)) rval.substring(1)
      else rval
    } else name
  }

  /**
    * check whether the container has legal name and related to specific service
    * @param containerName container name
    * @param kind service name
    * @return true if it is legal. otherwise, false
    */
  private[agent] def matched(containerName: String, kind: ClusterKind): Boolean =
    // form: GROUP-NAME-KIND-HASH
    containerName.split(DIVIDER).length == 4 &&
      containerName.split(DIVIDER)(2).toLowerCase == kind.toString.toLowerCase()

  /**
    * a helper method to fetch the cluster key from container name.
    *
    * @param containerName the container runtime name
    */
  private[agent] def objectKeyOfContainerName(containerName: String): ObjectKey =
    // form: GROUP-NAME-KIND-HASH
    ObjectKey.of(containerName.split(DIVIDER)(0), containerName.split(DIVIDER)(1))

  /**
    * The basic creator that for cluster creation.
    * We define the "required" parameters for a cluster here, and you should fill in each parameter
    * in the individual cluster creation.
    * Note: the checking rules are moved to the api-creation level.
    */
  trait ClusterCreator extends oharastream.ohara.common.pattern.Creator[Future[Unit]] with ClusterRequest {
    protected var executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    /**
      * set the thread pool used to create cluster by async call
      * @param executionContext thread pool
      * @return this creator
      */
    @Optional("default pool is scala.concurrent.ExecutionContext.Implicits.global")
    def threadPool(executionContext: ExecutionContext): ClusterCreator.this.type = {
      this.executionContext = Objects.requireNonNull(executionContext)
      this
    }

    /**
      * submit a creation progress in background. the implementation should avoid creating duplicate containers on the
      * same nodes. If the pass nodes already have containers, this creation should be viewed as "adding" than creation.
      * for example, the cluster-A exists and it is running on node-01. When user pass a creation to run cluster-A on
      * node-02, the creation progress should be aware of that user tries to add a new node (node-02) to the cluster-A.
      */
    override def create(): Future[Unit]
  }
}
