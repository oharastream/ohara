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

package oharastream.ohara.configurator.route
import akka.http.scaladsl.server
import oharastream.ohara.agent.BrokerCollie
import oharastream.ohara.client.configurator.ConnectorApi.ConnectorInfo
import oharastream.ohara.client.configurator.ShabondiApi.ShabondiClusterInfo
import oharastream.ohara.client.configurator.StreamApi.StreamClusterInfo
import oharastream.ohara.client.configurator.TopicApi._
import oharastream.ohara.client.configurator.{ShabondiApi, TopicApi}
import oharastream.ohara.common.setting.{ObjectKey, TopicKey}
import oharastream.ohara.common.util.CommonUtils
import oharastream.ohara.configurator.route.hook._
import oharastream.ohara.configurator.store.{DataStore, MetricsCache}
import oharastream.ohara.kafka.PartitionInfo

import scala.compat.java8.FutureConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

private[configurator] object TopicRoute {
  /**
    * update the metrics for input topic
    * @param topicInfo topic info
    * @return updated topic info
    */
  private[this] def updateState(topicInfo: TopicInfo)(
    implicit meterCache: MetricsCache,
    objectChecker: DataChecker,
    brokerCollie: BrokerCollie,
    executionContext: ExecutionContext
  ): Future[TopicInfo] =
    objectChecker.checkList
      .topic(topicInfo.key)
      .check()
      .map(_.topicInfos.head._2)
      .flatMap {
        case DataCondition.STOPPED =>
          Future.successful(
            topicInfo.copy(
              partitionInfos = Seq.empty,
              nodeMetrics = Map.empty,
              state = None
            )
          )
        case DataCondition.RUNNING =>
          objectChecker.checkList
            .brokerCluster(topicInfo.brokerClusterKey, DataCondition.RUNNING)
            .check()
            .map(_.runningBrokers.head)
            .flatMap { brokerClusterInfo =>
              topicAdmin(brokerClusterInfo) { topicAdmin =>
                topicAdmin
                  .exist(topicInfo.key)
                  .toScala
                  .flatMap { existent =>
                    if (existent)
                      topicAdmin
                        .topicDescription(topicInfo.key)
                        .toScala
                        .map(_.partitionInfos.asScala -> Some(TopicState.RUNNING))
                    else Future.successful(Seq.empty  -> None)
                  }
                  .map {
                    case (partitions, state) =>
                      topicInfo.copy(
                        partitionInfos = partitions
                          .map(
                            partition =>
                              new PartitionInfo(
                                partition.id,
                                partition.leader,
                                partition.replicas,
                                partition.inSyncReplicas(),
                                partition.beginningOffset,
                                partition.endOffset
                              )
                          )
                          .toSeq,
                        state = state,
                        nodeMetrics = meterCache.meters(brokerClusterInfo, topicInfo.key)
                      )
                  }
              }
            }
      }
      .recover {
        case _: Throwable =>
          topicInfo.copy(
            partitionInfos = Seq.empty,
            nodeMetrics = Map.empty,
            state = None
          )
      }

  private[this] def hookOfGet(
    implicit meterCache: MetricsCache,
    objectChecker: DataChecker,
    brokerCollie: BrokerCollie,
    executionContext: ExecutionContext
  ): HookOfGet[TopicInfo] = (topicInfo: TopicInfo) => updateState(topicInfo)

  private[this] def hookOfList(
    implicit meterCache: MetricsCache,
    objectChecker: DataChecker,
    brokerCollie: BrokerCollie,
    executionContext: ExecutionContext
  ): HookOfList[TopicInfo] =
    (topicInfos: Seq[TopicInfo]) => Future.traverse(topicInfos)(updateState)

  private[this] def creationToTopicInfo(
    creation: Creation
  )(implicit objectChecker: DataChecker, executionContext: ExecutionContext): Future[TopicInfo] =
    objectChecker.checkList
      .brokerCluster(creation.brokerClusterKey)
      .references(creation.raw, DEFINITIONS)
      .check()
      .map { _ =>
        TopicInfo(
          settings = creation.raw,
          partitionInfos = Seq.empty,
          nodeMetrics = Map.empty,
          state = None,
          lastModified = CommonUtils.current()
        )
      }

  private[this] def hookOfCreation(
    implicit objectChecker: DataChecker,
    executionContext: ExecutionContext
  ): HookOfCreation[Creation, TopicInfo] =
    creationToTopicInfo(_)

  private[this] def hookOfUpdating(
    implicit objectChecker: DataChecker,
    executionContext: ExecutionContext
  ): HookOfUpdating[Updating, TopicInfo] =
    (key: ObjectKey, updating: Updating, previousOption: Option[TopicInfo]) =>
      previousOption match {
        case None =>
          creationToTopicInfo(
            access.request
              .settings(keepEditableFields(updating.raw, TopicApi.DEFINITIONS))
              // the key is not in update's settings so we have to add it to settings
              .name(key.name)
              .group(key.group)
              .creation
          )
        case Some(previous) =>
          objectChecker.checkList
          // we don't support to update a running topic
            .topic(previous.key, DataCondition.STOPPED)
            .check()
            .flatMap { _ =>
              // 1) fill the previous settings (if exists)
              // 2) overwrite previous settings by updated settings
              // 3) fill the ignored settings by creation
              creationToTopicInfo(
                access.request
                  .settings(previous.settings)
                  .settings(updating.raw)
                  // the key is not in update's settings so we have to add it to settings
                  .name(key.name)
                  .group(key.group)
                  .creation
              )
            }
      }

  private[this] def checkConflict(
    topicInfo: TopicInfo,
    connectorInfos: Seq[ConnectorInfo],
    streamClusterInfos: Seq[StreamClusterInfo],
    shabondiClusterInfos: Seq[ShabondiClusterInfo]
  ): Unit = {
    val conflictConnectors = connectorInfos.filter(_.topicKeys.contains(topicInfo.key))
    if (conflictConnectors.nonEmpty)
      throw new IllegalArgumentException(
        s"topic:${topicInfo.key} is used by running connectors:${conflictConnectors.map(_.key).mkString(",")}"
      )

    val conflictStreams =
      streamClusterInfos.filter(s => s.fromTopicKeys.contains(topicInfo.key) || s.toTopicKeys.contains(topicInfo.key))
    if (conflictStreams.nonEmpty)
      throw new IllegalArgumentException(
        s"topic:${topicInfo.key} is used by running streams:${conflictStreams.map(_.key).mkString(",")}"
      )

    val conflictShabondis = shabondiClusterInfos.filter({ clusterInfo =>
      clusterInfo.shabondiClass match {
        case ShabondiApi.SHABONDI_SOURCE_CLASS_NAME => clusterInfo.sourceToTopics.contains(topicInfo.key)
        case ShabondiApi.SHABONDI_SINK_CLASS_NAME   => clusterInfo.sinkFromTopics.contains(topicInfo.key)
        case _                                      => throw new UnsupportedOperationException(s"${clusterInfo.shabondiClass} is unsupported")
      }
    })
    if (conflictShabondis.nonEmpty)
      throw new IllegalArgumentException(
        s"topic:${topicInfo.key} is used by running shabondis:${conflictShabondis.map(_.key).mkString(",")}"
      )
  }

  private[this] def hookBeforeDelete(
    implicit objectChecker: DataChecker,
    executionContext: ExecutionContext
  ): HookBeforeDelete =
    (key: ObjectKey) =>
      objectChecker.checkList
        .topic(TopicKey.of(key.group(), key.name()), DataCondition.STOPPED)
        .allConnectors()
        .allStreams()
        .allShabondis()
        .check()
        .map { report =>
          checkConflict(
            report.topicInfos.head._1,
            report.connectorInfos.keys.toSeq,
            report.streamClusterInfos.keys.toSeq,
            report.shabondiClusterInfos.keys.toSeq
          )
          ()
        }
        .recover {
          // the duplicate deletes are legal to ohara
          case e: DataCheckException if e.nonexistent.contains(key) => ()
          case e: Throwable                                         => throw e
        }
        .map(_ => ())

  private[this] def hookOfStart(
    implicit objectChecker: DataChecker,
    brokerCollie: BrokerCollie,
    executionContext: ExecutionContext
  ): HookOfAction[TopicInfo] =
    (topicInfo: TopicInfo, _, _) =>
      objectChecker.checkList
        .topic(topicInfo.key)
        .brokerCluster(topicInfo.brokerClusterKey, DataCondition.RUNNING)
        .check()
        .map(report => (report.topicInfos.head._2, report.runningBrokers.head))
        .flatMap {
          case (condition, brokerClusterInfo) =>
            condition match {
              case DataCondition.RUNNING => Future.unit
              case DataCondition.STOPPED =>
                topicAdmin(brokerClusterInfo) { topicAdmin =>
                  topicAdmin.topicCreator
                    .topicKey(topicInfo.key)
                    .numberOfPartitions(topicInfo.numberOfPartitions)
                    .numberOfReplications(topicInfo.numberOfReplications)
                    .options(topicInfo.kafkaConfigs.asJava)
                    .create()
                    .toScala
                    .flatMap(_ => Future.unit)
                }
            }
        }

  private[this] def hookOfStop(
    implicit objectChecker: DataChecker,
    brokerCollie: BrokerCollie,
    executionContext: ExecutionContext
  ): HookOfAction[TopicInfo] =
    (topicInfo: TopicInfo, _, _) =>
      objectChecker.checkList
        .allConnectors()
        .allStreams()
        .allShabondis()
        .topic(topicInfo.key)
        .check()
        .map(
          report =>
            (report.topicInfos.head._2, report.runningConnectors, report.runningStreams, report.runningShabondis)
        )
        .flatMap {
          case (condition, runningConnectors, runningStreams, runningShabondis) =>
            condition match {
              case DataCondition.STOPPED => Future.unit
              case DataCondition.RUNNING =>
                checkConflict(topicInfo, runningConnectors, runningStreams, runningShabondis)
                objectChecker.checkList
                // topic is running so the related broker MUST be running
                  .brokerCluster(topicInfo.brokerClusterKey, DataCondition.RUNNING)
                  .check()
                  .map(_.runningBrokers.head)
                  .flatMap(
                    b =>
                      topicAdmin(b) { topicAdmin =>
                        topicAdmin
                          .deleteTopic(topicInfo.key)
                          .toScala
                          .flatMap(_ => Future.unit)
                      }
                  )
            }
        }

  def apply(
    implicit store: DataStore,
    objectChecker: DataChecker,
    meterCache: MetricsCache,
    brokerCollie: BrokerCollie,
    executionContext: ExecutionContext
  ): server.Route =
    RouteBuilder[Creation, Updating, TopicInfo]()
      .prefix(PREFIX)
      .hookOfCreation(hookOfCreation)
      .hookOfUpdating(hookOfUpdating)
      .hookOfGet(hookOfGet)
      .hookOfList(hookOfList)
      .hookBeforeDelete(hookBeforeDelete)
      .hookOfPutAction(START_COMMAND, hookOfStart)
      .hookOfPutAction(STOP_COMMAND, hookOfStop)
      .build()
}
