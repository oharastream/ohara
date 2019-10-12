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

import java.util.Objects

import akka.http.scaladsl.server
import com.island.ohara.agent._
import com.island.ohara.client.configurator.v0.MetricsApi.Metrics
import com.island.ohara.client.configurator.v0.StreamApi._
import com.island.ohara.client.kafka.TopicAdmin.TopicInfo
import com.island.ohara.common.setting.{ObjectKey, TopicKey}
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.file.FileStore
import com.island.ohara.configurator.route.hook.{HookOfAction, HookOfCreation, HookOfUpdating}
import com.island.ohara.configurator.store.{DataStore, MeterCache}
import com.island.ohara.streams.config.StreamDefUtils
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
private[configurator] object StreamRoute {

  /**
    * The group for a stream application metrics
    * Since each streamApp has it's own metrics, it is OK to use same value
    */
  private[configurator] val STREAM_APP_GROUP = StreamDefUtils.STREAMAPP_METRIC_GROUP_DEFINITION.defaultValue()

  /**
    * Assert the require streamApp properties in creation / updating
    *
    * @param streamClusterInfo streamApp data
    */
  private[this] def assertParameters(streamClusterInfo: StreamClusterInfo): StreamClusterInfo = {
    CommonUtils.requireNonEmpty(streamClusterInfo.name, () => "name fail assert")
    CommonUtils.requireConnectionPort(streamClusterInfo.jmxPort)
    // jarKey is required in properties payload
    Objects.requireNonNull(streamClusterInfo.jarKey)
    streamClusterInfo
  }

  /**
    * Assert the require streamApp properties before running
    *
    * @param streamClusterInfo streamApp data
    */
  private[this] def assertParameters(streamClusterInfo: StreamClusterInfo,
                                     topicInfos: Seq[TopicInfo]): StreamClusterInfo = {
    def checkStoppedTopics(topicKeys: Set[TopicKey], prefix: String): Unit = {
      CommonUtils.requireNonEmpty(topicKeys.asJava, () => s"$prefix topics can't be empty")
      // check the from/to topic size equals one
      // TODO: this is a workaround to avoid input multiple topics
      // TODO: please refactor this after the single from/to topic issue resolved...by Sam
      if (topicKeys.size > 1)
        throw new IllegalArgumentException(
          s"We don't allow multiple topics of $prefix field, actual: ${topicKeys.mkString(",")}")
      val stoppedFromTopics = topicKeys.filterNot(topicKey => topicInfos.exists(_.name == topicKey.topicNameOnKafka()))
      if (stoppedFromTopics.nonEmpty)
        throw new NoSuchElementException(s"topics:${stoppedFromTopics.mkString(",")} is not running")
    }
    checkStoppedTopics(streamClusterInfo.fromTopicKeys, "from")
    checkStoppedTopics(streamClusterInfo.toTopicKeys, "to")
    assertParameters(streamClusterInfo)
  }

  /**
    * fina the broker cluster for this creation. The rules are shown below.
    * 1) find the defined value for broker cluster key
    * 2) find the single broker cluster if brokerClusterKey is None
    * 3) throw exception otherwise
    */
  private[this] def pickBrokerCluster(brokerClusterKey: Option[ObjectKey])(
    implicit brokerCollie: BrokerCollie,
    executionContext: ExecutionContext): Future[ObjectKey] =
    brokerClusterKey.map(Future.successful).getOrElse(CollieUtils.singleBrokerCluster())

  /**
    * This is a temporary solution for using both nodeNames and instances
    * Decide streamApp running nodes. Rules:
    * 1) If both instances and nodeNames were not defined, return empty
    * 2) If instances was defined and nodeNames was not empty, throw exception
    * 3) If instances was not defined but nodeNames is defined
    * 3.1) If some nodes were not defined in nodeCollie, throw exception
    * 3.2) return nodeNames
    * 4) If instances is bigger than nodeCollie size, throw exception
    * 5) Random pick node name from nodeCollie of instances size
    *
    * @param nodeNamesOption node name list
    * @param instancesOption running instances
    * @param executionContext execution context
    * @param nodeCollie node collie
    * @return actual node name list
    */
  private[this] def pickNodeNames(nodeNamesOption: Option[Set[String]], instancesOption: Option[Int])(
    implicit executionContext: ExecutionContext,
    nodeCollie: NodeCollie): Future[Option[Set[String]]] =
    nodeCollie.nodes().map(n => n.map(_.name)).map { all =>
      instancesOption.fold(
        // not define instances, use nodeNames instead
        // If there were some nodes that nodeCollie doesn't contain, throw exception
        nodeNamesOption.fold[Option[Set[String]]](
          // both instances and nodeNames are not defined, return None
          None
        ) { nodeNames =>
          if (nodeNames.forall(all.contains))
            // you are fine to going use it
            Some(nodeNames)
          else
            // we find a node that is not belong to nodeCollie , throw error
            throw new IllegalArgumentException(
              s"Some nodes could not be found, expected: $nodeNames, actual: $all"
            )
        }
      ) { instances =>
        if (nodeNamesOption.isDefined && nodeNamesOption.get.nonEmpty)
          throw new IllegalArgumentException(
            s"You cannot define both nodeNames[$nodeNamesOption] and instances[$instances]")
        if (all.size < instances)
          throw new IllegalArgumentException(
            s"You cannot set instances bigger than actual node list. Expect instances: $instances, actual: ${all.size}")
        Some(Random.shuffle(all).take(instances).toSet)
      }
    }

  private[this] def hookOfCreation(implicit fileStore: FileStore,
                                   nodeCollie: NodeCollie,
                                   brokerCollie: BrokerCollie,
                                   streamCollie: StreamCollie,
                                   executionContext: ExecutionContext): HookOfCreation[Creation, StreamClusterInfo] =
    (creation: Creation) =>
      pickBrokerCluster(creation.brokerClusterKey).flatMap { bkKey =>
        //TODO remove this after #2288
        pickNodeNames(Some(creation.nodeNames), creation.instances).flatMap(
          nodes =>
            fileStore
              .fileInfo(creation.jarKey)
              .flatMap(info => streamCollie.loadDefinition(info.url).map((_, Option.empty[String])))
              .recover {
                case e: Throwable => (None, Some(e.getMessage))
              }
              .map {
                case (definition, error) =>
                  StreamClusterInfo(
                    settings = {
                      // In creation, we have to re-define the following value since they may changed:
                      // 1) broker cluster name
                      // 2) node name (This should be removed after #2288
                      val req = access.request.settings(creation.settings).brokerClusterKey(bkKey)
                      if (nodes.isDefined) req.nodeNames(nodes.get)
                      req.creation.settings
                    },
                    definition = definition,
                    aliveNodes = Set.empty,
                    state = None,
                    metrics = Metrics(Seq.empty),
                    error = error,
                    lastModified = CommonUtils.current()
                  )
              }
              .map(assertParameters))
    }

  private[this] def HookOfUpdating(
    implicit nodeCollie: NodeCollie,
    brokerCollie: BrokerCollie,
    streamCollie: StreamCollie,
    executionContext: ExecutionContext): HookOfUpdating[Creation, Updating, StreamClusterInfo] =
    (key: ObjectKey, update: Updating, previousOption: Option[StreamClusterInfo]) =>
      streamCollie.clusters
        .flatMap { clusters =>
          if (clusters.keys.filter(_.key == key).exists(_.state.nonEmpty))
            throw new RuntimeException(s"You cannot update property on non-stopped StreamApp cluster: $key")
          pickBrokerCluster(update.brokerClusterKey.orElse(previousOption.map(_.brokerClusterKey))).flatMap(
            bkKey =>
              //TODO remove this after #2288
              pickNodeNames(update.nodeNames, update.instances).map { nodes =>
                var extra_settings =
                  Map[String, JsValue](
                    StreamDefUtils.BROKER_CLUSTER_KEY_DEFINITION.key() -> ObjectKey.toJsonString(bkKey).parseJson)
                if (nodes.isDefined)
                  extra_settings += StreamDefUtils.NODE_NAMES_DEFINITION.key() -> JsArray(
                    nodes.get.map(JsString(_)).toVector)
                new Updating(update.settings ++ extra_settings)
            }
          )
        }
        .map { update =>
          StreamClusterInfo(
            // 1) fill the previous settings (if exists)
            // 2) overwrite previous settings by updated settings
            // 3) fill the ignored settings by creation
            settings = access.request
              .settings(previousOption.map(_.settings).getOrElse(Map.empty))
              .settings(update.settings)
              // the key is not in update's settings so we have to add it to settings
              .name(key.name)
              .group(key.group)
              .creation
              .settings,
            definition = previousOption.flatMap(_.definition),
            // this cluster is not running so we don't need to keep the dead nodes in the updated cluster.
            aliveNodes = Set.empty,
            state = None,
            metrics = Metrics.EMPTY,
            error = None,
            lastModified = CommonUtils.current()
          )
        }
        .map(assertParameters)

  private[this] def hookOfStart(implicit store: DataStore,
                                meterCache: MeterCache,
                                fileStore: FileStore,
                                streamCollie: StreamCollie,
                                cleaner: AdminCleaner,
                                brokerCollie: BrokerCollie,
                                executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store
        .value[StreamClusterInfo](key)
        .map { info =>
          // check the values by definition
          //TODO move this to RouteUtils in #2191
          info.definition.fold(throw new IllegalArgumentException("definition could not be empty")) { definition =>
            var copy = info.settings
            definition.definitions.foreach(
              settingDef =>
                // add the (key, defaultValue) to settings if absent
                if (!copy.contains(settingDef.key()) && !CommonUtils.isEmpty(settingDef.defaultValue()))
                  copy += settingDef.key() -> JsString(settingDef.defaultValue()))
            info.settings
              .map {
                case (k, v) =>
                  k -> (v match {
                    case JsString(s) => s
                    case _           => v.toString
                  })
              }
              .foreach {
                case (k, v) =>
                  definition.definitions
                    .find(_.key() == k)
                    .fold(throw new IllegalArgumentException(s"$k not found in definition")) { settingDef =>
                      settingDef.checker().accept(v)
                    }
              }
            info.copy(settings = copy)
          }
        }
        .flatMap { streamClusterInfo =>
          CollieUtils
            .topicAdmin(streamClusterInfo.brokerClusterKey)
            .flatMap {
              case (brokerClusterInfo, topicAdmin) =>
                topicAdmin.topics().map { topicInfos =>
                  try brokerClusterInfo -> topicInfos
                  finally topicAdmin.close()
                }
            }
            .map {
              case (brokerClusterInfo, topicInfos) =>
                assertParameters(streamClusterInfo, topicInfos)
                brokerClusterInfo
            }
            .flatMap { brokerClusterInfo =>
              fileStore.fileInfo(streamClusterInfo.jarKey).flatMap { fileInfo =>
                streamCollie.creator
                // these settings will send to container environment
                // we convert all value to string for convenient
                  .settings(streamClusterInfo.settings)
                  .name(streamClusterInfo.name)
                  .group(streamClusterInfo.group)
                  .imageName(streamClusterInfo.imageName)
                  .nodeNames(streamClusterInfo.nodeNames)
                  .jarInfo(fileInfo)
                  .brokerClusterKey(brokerClusterInfo.key)
                  .connectionProps(brokerClusterInfo.connectionProps)
                  // This is a temporary solution for "enable exactly once",
                  // but we should change the behavior to not just "true or false"...by Sam
                  .setting(StreamDefUtils.EXACTLY_ONCE_DEFINITION.key(),
                           JsString(streamClusterInfo.exactlyOnce.toString))
                  .threadPool(executionContext)
                  .create()
              }
            }
        }
        .map(_ => Unit)

  private[this] def hookBeforeStop: HookOfAction = (_, _, _) => Future.unit

  def apply(implicit store: DataStore,
            nodeCollie: NodeCollie,
            zookeeperCollie: ZookeeperCollie,
            brokerCollie: BrokerCollie,
            workerCollie: WorkerCollie,
            streamCollie: StreamCollie,
            serviceCollie: ServiceCollie,
            cleaner: AdminCleaner,
            fileStore: FileStore,
            meterCache: MeterCache,
            executionContext: ExecutionContext): server.Route =
    clusterRoute[StreamClusterInfo, StreamClusterStatus, Creation, Updating](
      root = STREAM_PREFIX_PATH,
      metricsKey = Some(STREAM_APP_GROUP),
      hookOfCreation = hookOfCreation,
      HookOfUpdating = HookOfUpdating,
      hookOfStart = hookOfStart,
      hookBeforeStop = hookBeforeStop
    )
}
