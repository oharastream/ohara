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
import java.util.Objects

import com.island.ohara.agent.docker.ContainerState
import com.island.ohara.client.configurator.v0.BrokerApi.{BrokerClusterInfo, BrokerClusterStatus, Creation}
import com.island.ohara.client.configurator.v0.ContainerApi.{ContainerInfo, PortMapping, PortPair}
import com.island.ohara.client.configurator.v0.NodeApi.Node
import com.island.ohara.client.configurator.v0.{BrokerApi, TopicApi, ZookeeperApi}
import com.island.ohara.client.kafka.TopicAdmin
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.metrics.BeanChannel
import com.island.ohara.metrics.kafka.TopicMeter
import spray.json.JsString

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

trait BrokerCollie extends Collie[BrokerClusterStatus] {

  override val serviceName: String = BrokerApi.BROKER_SERVICE_NAME

  /**
    * This is a complicated process. We must address following issues.
    * 1) check the existence of cluster
    * 2) check the existence of nodes
    * 3) Each broker container has got to export exporter port and client port
    * 4) Each broker container should assign "docker host name/port" to advertised name/port
    * 5) add zookeeper routes to all broker containers (broker needs to connect to zookeeper cluster)
    * 6) Add broker routes to all broker containers
    * 7) update existed containers (if we are adding new node into a running cluster)
    * @return creator of broker cluster
    */
  override def creator: BrokerCollie.ClusterCreator = (executionContext, creation) => {
    implicit val exec: ExecutionContext = executionContext
    clusters().flatMap(clusters => {
      clusters
        .find(_._1.key == creation.key)
        .map(_._2)
        .map(containers =>
          nodeCollie
            .nodes(containers.map(_.nodeName).toSet)
            .map(_.map(node => node -> containers.find(_.nodeName == node.name).get).toMap))
        .getOrElse(Future.successful(Map.empty))
        .map {
          existNodes =>
            // if there is a running cluster already, we should check the consistency of configuration
            existNodes.values.foreach {
              container =>
                def checkValue(previous: String, newValue: String): Unit =
                  if (previous != newValue) throw new IllegalArgumentException(s"previous:$previous new:$newValue")

                def check(key: String, newValue: String): Unit = {
                  val previous = CommonUtils.fromEnvString(container.environments(key))
                  if (previous != newValue) throw new IllegalArgumentException(s"previous:$previous new:$newValue")
                }

                checkValue(container.imageName, creation.imageName)
                check(BrokerApi.CLIENT_PORT_KEY, creation.clientPort.toString)
                check(BrokerApi.ZOOKEEPER_CLUSTER_KEY_KEY, ObjectKey.toJsonString(creation.zookeeperClusterKey.get))
            }
            existNodes
        }
        .flatMap(existNodes =>
          nodeCollie
            .nodes(creation.nodeNames)
            .map(_.map(node => node -> Collie.format(prefixKey, creation.group, creation.name, serviceName)).toMap)
            .map((existNodes, _)))
        .map {
          case (existNodes, nodes) =>
            (existNodes,
             // find the nodes which have not run the services
             nodes.filterNot(n => existNodes.exists(_._1.hostname == n._1.hostname)),
             zookeeperContainers(creation.zookeeperClusterKey.get))
        }
        .flatMap {
          case (existNodes, newNodes, zkContainers) =>
            zkContainers
              .flatMap(zkContainers => {
                if (zkContainers.isEmpty)
                  throw new IllegalArgumentException(s"zookeeper:${creation.zookeeperClusterKey.get} does not exist")
                if (newNodes.isEmpty) Future.successful(Seq.empty)
                else {
                  val zookeepers = zkContainers
                    .map(c => s"${c.nodeName}:${c.environments(ZookeeperApi.CLIENT_PORT_KEY).toInt}")
                    .mkString(",")

                  val route = resolveHostNames((existNodes.keys.map(_.hostname) ++ newNodes.keys
                    .map(_.hostname) ++ zkContainers.map(_.nodeName)).toSet)
                  existNodes.foreach {
                    case (node, container) => hookOfNewRoute(node, container, route)
                  }

                  // the new broker node can't take used id so we find out the max id which is used by current cluster
                  val maxId: Int =
                    if (existNodes.isEmpty) 0
                    else existNodes.values.map(_.environments(BrokerApi.ID_KEY).toInt).toSet.max + 1

                  // ssh connection is slow so we submit request by multi-thread
                  Future.sequence(newNodes.zipWithIndex.map {
                    case ((node, containerName), index) =>
                      val containerInfo = ContainerInfo(
                        nodeName = node.name,
                        id = Collie.UNKNOWN,
                        imageName = creation.imageName,
                        created = Collie.UNKNOWN,
                        // this fake container will be cached before refreshing cache so we make it running.
                        // other, it will be filtered later ...
                        state = ContainerState.RUNNING.name,
                        kind = Collie.UNKNOWN,
                        name = containerName,
                        size = Collie.UNKNOWN,
                        portMappings = Seq(PortMapping(
                          hostIp = Collie.UNKNOWN,
                          portPairs = Seq(
                            PortPair(
                              hostPort = creation.clientPort,
                              containerPort = creation.clientPort
                            ),
                            PortPair(
                              hostPort = creation.exporterPort,
                              containerPort = creation.exporterPort
                            ),
                            PortPair(
                              hostPort = creation.jmxPort,
                              containerPort = creation.jmxPort
                            )
                          )
                        )),
                        environments = creation.settings.map {
                          case (k, v) =>
                            k -> (v match {
                              // the string in json representation has quote in the beginning and end.
                              // we don't like the quotes since it obstruct us to cast value to pure string.
                              case JsString(s) => s
                              // save the json string for all settings
                              case _ => CommonUtils.toEnvString(v.toString)
                            })
                        }
                        // each broker instance needs an unique id to identify
                          + (BrokerApi.ID_KEY -> (maxId + index).toString)
                        // connect to user defined zookeeper cluster
                          + (BrokerApi.ZOOKEEPERS_KEY -> zookeepers)
                        // expose the borker hostname for zookeeper to register
                          + (BrokerApi.ADVERTISED_HOSTNAME_KEY -> node.hostname)
                        // jmx exporter host name
                          + (BrokerApi.JMX_HOSTNAME_KEY -> node.hostname),
                        hostname = containerName
                      )
                      doCreator(executionContext, containerName, containerInfo, node, route)
                        .map(_ => Some(containerInfo))
                        .recover {
                          case _: Throwable =>
                            None
                        }
                  })
                }
              })
              .map(_.flatten.toSeq)
              .map {
                successfulContainers =>
                  val state = toClusterState(existNodes.values.toSeq ++ successfulContainers).map(_.name)
                  val status = new BrokerClusterStatus(
                    group = creation.group,
                    name = creation.name,
                    // TODO: we should check the supported arguments by the running broker images
                    topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS,
                    // no state means cluster is NOT running so we cleanup the dead nodes
                    aliveNodes = state
                      .map(_ => (successfulContainers.map(_.nodeName) ++ existNodes.values.map(_.hostname)).toSet)
                      .getOrElse(Set.empty),
                    state = state,
                    error = None
                  )
                  postCreate(status, successfulContainers)
              }
        }
    })
  }

  /**
    * Please setting nodeCollie to implement class
    * @return
    */
  protected def nodeCollie: NodeCollie

  /**
    *  Implement prefix name for the platform
    * @return
    */
  protected def prefixKey: String

  /**
    * Update exist node info
    * @param node node object
    * @param container container information
    * @param route ip-host mapping list
    */
  protected def hookUpdate(node: Node, container: ContainerInfo, route: Map[String, String]): Unit = {
    //Nothing
  }

  /**
    * Please implement this function to create the container to a different platform
    * @param executionContext execution context
    * @param containerName container name
    * @param containerInfo container information
    * @param node node object
    * @param route ip-host mapping
    */
  protected def doCreator(executionContext: ExecutionContext,
                          containerName: String,
                          containerInfo: ContainerInfo,
                          node: Node,
                          route: Map[String, String]): Future[Unit]

  /**
    * After creating the broker, need to processor other things
    * @param clusterStatus broker cluster information
    * @param successfulContainers successful created containers
    */
  protected def postCreate(clusterStatus: BrokerClusterStatus, successfulContainers: Seq[ContainerInfo]): Unit = {
    //Default Nothing
  }

  /**
    * Create a topic admin according to passed cluster.
    * Noted: the input cluster MUST be running. otherwise, a exception is returned.
    * @param brokerClusterInfo target cluster
    * @return topic admin
    */
  def topicAdmin(brokerClusterInfo: BrokerClusterInfo)(
    implicit executionContext: ExecutionContext): Future[TopicAdmin] =
    cluster(brokerClusterInfo.key).map(_ => TopicAdmin(brokerClusterInfo.connectionProps))

  /**
    * Get all meter beans from specific broker cluster
    * @param cluster cluster
    * @return meter beans
    */
  def topicMeters(cluster: BrokerClusterInfo): Seq[TopicMeter] = cluster.nodeNames.flatMap { node =>
    BeanChannel.builder().hostname(node).port(cluster.jmxPort).build().topicMeters().asScala
  }.toSeq

  override protected[agent] def toStatus(key: ObjectKey, containers: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext): Future[BrokerClusterStatus] =
    Future.successful(
      new BrokerClusterStatus(
        group = key.group(),
        name = key.name(),
        // TODO: we should check the supported arguments by the running broker images
        topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS,
        // Currently, docker and k8s has same naming rule for "Running",
        // it is ok that we use the containerState.RUNNING here.
        aliveNodes = containers.filter(_.state == ContainerState.RUNNING.name).map(_.nodeName).toSet,
        state = toClusterState(containers).map(_.name),
        // TODO how could we fetch the error?...by Sam
        error = None
      ))

  /**
    * In creation progress, broker has to check the existence of zookeeper and then fetch something important from zookeeper
    * containers.
    * @param zkClusterKey zookeeper cluster key
    * @param executionContext execution context
    * @return
    */
  protected def zookeeperContainers(zkClusterKey: ObjectKey)(
    implicit executionContext: ExecutionContext): Future[Seq[ContainerInfo]]

  /**
    * there is new route to the node. the sub class can update the running container to apply new route.
    */
  protected def hookOfNewRoute(node: Node, container: ContainerInfo, route: Map[String, String]): Unit = {
    //Nothing
  }
}

object BrokerCollie {

  trait ClusterCreator extends Collie.ClusterCreator with BrokerApi.Request {
    override def create(): Future[Unit] =
      doCreate(
        executionContext = Objects.requireNonNull(executionContext),
        creation = creation
      )

    protected def doCreate(executionContext: ExecutionContext, creation: Creation): Future[Unit]
  }
}
