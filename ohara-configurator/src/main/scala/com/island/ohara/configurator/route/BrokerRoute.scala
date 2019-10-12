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

package com.island.ohara.configurator.route

import akka.http.scaladsl.server
import com.island.ohara.agent._
import com.island.ohara.client.configurator.v0.BrokerApi.{Creation, _}
import com.island.ohara.client.configurator.v0.{BrokerApi, TopicApi}
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.route.hook.{HookOfAction, HookOfCreation, HookOfUpdating}
import com.island.ohara.configurator.store.{DataStore, MeterCache}

import scala.concurrent.{ExecutionContext, Future}
object BrokerRoute {

  private[this] def hookOfCreation(implicit zookeeperCollie: ZookeeperCollie,
                                   executionContext: ExecutionContext): HookOfCreation[Creation, BrokerClusterInfo] =
    (creation: Creation) =>
      creation.zookeeperClusterKey.map(Future.successful).getOrElse(CollieUtils.singleZookeeperCluster()).map { zkKey =>
        BrokerClusterInfo(
          settings = BrokerApi.access.request.settings(creation.settings).zookeeperClusterKey(zkKey).creation.settings,
          aliveNodes = Set.empty,
          state = None,
          error = None,
          lastModified = CommonUtils.current(),
          topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS
        )
    }

  private[this] def HookOfUpdating(
    implicit zookeeperCollie: ZookeeperCollie,
    serviceCollie: ServiceCollie,
    executionContext: ExecutionContext): HookOfUpdating[Creation, Updating, BrokerClusterInfo] =
    (key: ObjectKey, update: Updating, previousOption: Option[BrokerClusterInfo]) =>
      serviceCollie.brokerCollie
        .clusters()
        .flatMap { clusters =>
          if (clusters.keys.filter(_.key == key).exists(_.state.nonEmpty))
            throw new RuntimeException(s"You cannot update property on non-stopped broker cluster: $key")
          update.zookeeperClusterKey
            .orElse(previousOption.map(_.zookeeperClusterKey))
            .map(Future.successful)
            .getOrElse(CollieUtils.singleZookeeperCluster())
        }
        .map { zkKey =>
          // 1) fill the previous settings (if exists)
          // 2) overwrite previous settings by updated settings
          // 3) fill the ignored settings by creation
          BrokerClusterInfo(
            settings = BrokerApi.access.request
              .settings(previousOption.map(_.settings).getOrElse(Map.empty))
              .settings(update.settings)
              .zookeeperClusterKey(zkKey)
              // the key is not in update's settings so we have to add it to settings
              .name(key.name)
              .group(key.group)
              .creation
              .settings,
            aliveNodes = Set.empty,
            state = None,
            error = None,
            lastModified = CommonUtils.current(),
            topicSettingDefinitions = TopicApi.TOPIC_DEFINITIONS
          )
      }

  private[this] def hookOfStart(implicit store: DataStore,
                                meterCache: MeterCache,
                                brokerCollie: BrokerCollie,
                                serviceCollie: ServiceCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      (for {
        bkInfo <- store.value[BrokerClusterInfo](key)
        bks <- runningBrokerClusters()
      } yield (bkInfo, bks))
        .flatMap {
          case (brokerClusterInfo, runningBrokerClusters) =>
            val conflictBrokerClusters =
              runningBrokerClusters.filter(_.zookeeperClusterKey == brokerClusterInfo.zookeeperClusterKey)
            if (conflictBrokerClusters.nonEmpty)
              throw new IllegalArgumentException(
                s"zk cluster:${brokerClusterInfo.zookeeperClusterKey} is already used by broker cluster:${conflictBrokerClusters.head.name}")
            serviceCollie.brokerCollie.creator
              .settings(brokerClusterInfo.settings)
              .name(brokerClusterInfo.name)
              .group(brokerClusterInfo.group)
              .clientPort(brokerClusterInfo.clientPort)
              .exporterPort(brokerClusterInfo.exporterPort)
              .jmxPort(brokerClusterInfo.jmxPort)
              .zookeeperClusterKey(brokerClusterInfo.zookeeperClusterKey)
              .imageName(brokerClusterInfo.imageName)
              .nodeNames(brokerClusterInfo.nodeNames)
              .threadPool(executionContext)
              .create()
        }
        .map(_ => Unit)

  private[this] def hookBeforeStop(implicit store: DataStore,
                                   meterCache: MeterCache,
                                   workerCollie: WorkerCollie,
                                   streamCollie: StreamCollie,
                                   executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _: String, _: Map[String, String]) =>
      (for {
        bkInfo <- store.value[BrokerClusterInfo](key)
        wks <- runningWorkerClusters()
        sts <- runningStreamClusters()
      } yield (bkInfo, wks, sts)).map {
        case (brokerClusterInfo, runningWorkerClusters, runningStreamClusters) =>
          val conflictWks = runningWorkerClusters.filter(_.brokerClusterKey == brokerClusterInfo.key)
          if (conflictWks.nonEmpty)
            throw new IllegalArgumentException(
              s"you can't remove broker cluster:$key since it is used by worker cluster:${conflictWks.mkString(",")}")
          val conflictStreams = runningStreamClusters.filter(_.brokerClusterKey == brokerClusterInfo.key)
          if (conflictStreams.nonEmpty)
            throw new IllegalArgumentException(
              s"you can't remove broker cluster:$key since it is used by stream cluster:${conflictStreams.mkString(",")}")
    }

  def apply(implicit store: DataStore,
            meterCache: MeterCache,
            zookeeperCollie: ZookeeperCollie,
            brokerCollie: BrokerCollie,
            workerCollie: WorkerCollie,
            streamCollie: StreamCollie,
            serviceCollie: ServiceCollie,
            nodeCollie: NodeCollie,
            executionContext: ExecutionContext): server.Route =
    clusterRoute[BrokerClusterInfo, BrokerClusterStatus, Creation, Updating](
      root = BROKER_PREFIX_PATH,
      metricsKey = None,
      hookOfCreation = hookOfCreation,
      HookOfUpdating = HookOfUpdating,
      hookOfStart = hookOfStart,
      hookBeforeStop = hookBeforeStop
    )
}
