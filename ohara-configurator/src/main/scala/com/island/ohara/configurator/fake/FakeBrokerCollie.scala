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

package com.island.ohara.configurator.fake

import java.util.concurrent.ConcurrentSkipListMap

import com.island.ohara.agent.{BrokerCollie, ServiceState, NoSuchClusterException, NodeCollie}
import com.island.ohara.client.configurator.v0.BrokerApi.{BrokerClusterInfo, BrokerClusterStatus}
import com.island.ohara.client.configurator.v0.ContainerApi.ContainerInfo
import com.island.ohara.client.configurator.v0.{NodeApi, TopicApi}
import com.island.ohara.client.kafka.TopicAdmin
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.metrics.BeanChannel
import com.island.ohara.metrics.kafka.TopicMeter

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

private[configurator] class FakeBrokerCollie(node: NodeCollie, bkConnectionProps: String)
    extends FakeCollie[BrokerClusterStatus](node)
    with BrokerCollie {

  override def topicMeters(cluster: BrokerClusterInfo): Seq[TopicMeter] =
    // we don't care for the fake mode since both fake mode and embedded mode are run on local jvm
    BeanChannel.local().topicMeters().asScala

  /**
    * cache all topics info in-memory so we should keep instance for each fake cluster.
    */
  private[this] val fakeAdminCache = new ConcurrentSkipListMap[BrokerClusterInfo, FakeTopicAdmin](
    (o1: BrokerClusterInfo, o2: BrokerClusterInfo) => o1.key.compareTo(o2.key))

  override def creator: BrokerCollie.ClusterCreator = (_, creation) =>
    Future.successful(
      addCluster(
        new BrokerClusterStatus(
          group = creation.group,
          name = creation.name,
          // TODO: we should check the supported arguments by the running broker images
          topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS,
          aliveNodes = creation.nodeNames ++ clusterCache.asScala
            .find(_._1.key == creation.key)
            .map(_._2.map(_.nodeName))
            .getOrElse(Set.empty),
          // In fake mode, we need to assign a state in creation for "GET" method to act like real case
          state = Some(ServiceState.RUNNING.name),
          error = None
        ),
        creation.imageName,
        creation.nodeNames,
        creation.ports
      ))

  override def topicAdmin(cluster: BrokerClusterInfo)(implicit executionContext: ExecutionContext): Future[TopicAdmin] =
    if (bkConnectionProps == null) Future.successful {
      if (!clusterCache.containsKey(cluster))
        throw new NoSuchClusterException(s"cluster:${cluster.name} is not running")
      val fake = new FakeTopicAdmin
      val r = fakeAdminCache.putIfAbsent(cluster, fake)
      if (r == null) fake else r
    } else Future.successful(TopicAdmin(bkConnectionProps))

  override protected def doCreator(executionContext: ExecutionContext,
                                   containerName: String,
                                   containerInfo: ContainerInfo,
                                   node: NodeApi.Node,
                                   route: Map[String, String]): Future[Unit] =
    throw new UnsupportedOperationException("Fake broker collie doesn't support doCreator function")

  protected override def zookeeperContainers(zkClusterKey: ObjectKey)(
    implicit executionContext: ExecutionContext): Future[Seq[ContainerInfo]] =
    throw new UnsupportedOperationException("Fake broker doesn't support zookeeperCluster function")

  /**
    * Please setting nodeCollie to implement class
    *
    * @return
    */
  override protected def nodeCollie: NodeCollie = node

  /**
    * Implement prefix name for the platform
    *
    * @return
    */
  override protected def prefixKey: String = "fakebroker"
}
