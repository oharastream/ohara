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
import oharastream.ohara.agent.{ServiceCollie, ShabondiCollie}
import oharastream.ohara.client.configurator.ShabondiApi
import oharastream.ohara.common.setting.ObjectKey
import oharastream.ohara.common.util.CommonUtils
import oharastream.ohara.configurator.route.hook._
import oharastream.ohara.configurator.store.{DataStore, MetricsCache}
import oharastream.ohara.shabondi.ShabondiDefinitions
import spray.json.JsString

import scala.concurrent.{ExecutionContext, Future}

private[configurator] object ShabondiRoute {
  import ShabondiApi._

  private def updateEndpointSetting(creation: Creation): Creation = {
    if (creation.nodeNames.isEmpty) {
      creation
    } else {
      val nodeName   = creation.nodeNames.head
      val clientPort = creation.clientPort
      val value = creation.shabondiClass match {
        case SHABONDI_SOURCE_CLASS_NAME => s"http://$nodeName:$clientPort/"
        case SHABONDI_SINK_CLASS_NAME   => s"http://$nodeName:$clientPort/groups/" + "${groupName}"
        case _                          => throw new UnsupportedOperationException(s"${creation.shabondiClass} is unsupported")
      }
      val endpointItem = (ShabondiDefinitions.ENDPOINT_DEFINITION.key, JsString(value))
      new Creation(creation.raw + endpointItem)
    }
  }

  private[this] def creationToClusterInfo(creation: Creation)(
    implicit objectChecker: DataChecker,
    executionContext: ExecutionContext
  ): Future[ShabondiClusterInfo] = {
    objectChecker.checkList
      .nodeNames(creation.nodeNames)
      .brokerCluster(creation.brokerClusterKey)
      .references(creation.raw, creation.definitions)
      .check()
      .map { _ =>
        val refinedCreation = SHABONDI_CLUSTER_CREATION_FORMAT.toBuilder
          .definitions(
            (creation.shabondiClass match {
              case ShabondiApi.SHABONDI_SOURCE_CLASS_NAME => ShabondiDefinitions.sourceDefinitions
              case ShabondiApi.SHABONDI_SINK_CLASS_NAME   => ShabondiDefinitions.sinkDefinitions
              case _                                      => throw new UnsupportedOperationException(s"${creation.shabondiClass} is unsupported")
            })
            // we should add definition having default value to complete Creation request but
            // TODO: we should check all definitions in Creation phase
            // https://github.com/oharastream/ohara/issues/4506
              .filter(_.hasDefault)
          )
          .build()
          .refine(creation)

        ShabondiClusterInfo(
          settings = updateEndpointSetting(refinedCreation).raw,
          aliveNodes = Set.empty,
          state = None,
          nodeMetrics = Map.empty,
          error = None,
          lastModified = CommonUtils.current()
        )
      }
  }

  private[this] def hookOfCreation(
    implicit objectChecker: DataChecker,
    executionContext: ExecutionContext
  ): HookOfCreation[Creation, ShabondiClusterInfo] =
    creationToClusterInfo(_)

  private[this] def hookOfUpdating(
    implicit objectChecker: DataChecker,
    executionContext: ExecutionContext
  ): HookOfUpdating[Updating, ShabondiClusterInfo] =
    (key: ObjectKey, updating: Updating, previousOption: Option[ShabondiClusterInfo]) =>
      previousOption match {
        case None =>
          val creation = ShabondiApi.access.request
            .settings(updating.raw)
            .key(key)
            .creation
          creationToClusterInfo(creation)
        case Some(previous) =>
          objectChecker.checkList
            .check()
            .flatMap { _ =>
              val creation = ShabondiApi.access.request
                .settings(previous.settings)
                .settings {
                  previous.shabondiClass match {
                    case ShabondiApi.SHABONDI_SOURCE_CLASS_NAME =>
                      keepEditableFields(updating.raw, ShabondiApi.SOURCE_ALL_DEFINITIONS)
                    case ShabondiApi.SHABONDI_SINK_CLASS_NAME =>
                      keepEditableFields(updating.raw, ShabondiApi.SINK_ALL_DEFINITIONS)
                    case _ => throw new UnsupportedOperationException(s"${previous.shabondiClass} is unsupported")
                  }
                }
                .key(key)
                .creation
              creationToClusterInfo(creation)
            }
      }

  private[this] def hookOfStart(
    implicit objectChecker: DataChecker,
    shabondiCollie: ShabondiCollie,
    executionContext: ExecutionContext
  ): HookOfAction[ShabondiClusterInfo] =
    (clusterInfo: ShabondiClusterInfo, _: String, _: Map[String, String]) => {
      val checkTopics = clusterInfo.shabondiClass match {
        case ShabondiApi.SHABONDI_SOURCE_CLASS_NAME => clusterInfo.sourceToTopics
        case ShabondiApi.SHABONDI_SINK_CLASS_NAME   => clusterInfo.sinkFromTopics
        case _                                      => throw new UnsupportedOperationException(s"${clusterInfo.shabondiClass} is unsupported")
      }
      if (checkTopics.isEmpty) {
        val key = clusterInfo.shabondiClass match {
          case ShabondiApi.SHABONDI_SOURCE_CLASS_NAME => ShabondiDefinitions.SOURCE_TO_TOPICS_DEFINITION.key
          case ShabondiApi.SHABONDI_SINK_CLASS_NAME   => ShabondiDefinitions.SINK_FROM_TOPICS_DEFINITION.key
          case _                                      => throw new UnsupportedOperationException(s"${clusterInfo.shabondiClass} is unsupported")
        }
        throw new IllegalArgumentException(s"$key cannot be empty.")
      }
      // TODO: support multiple nodes deployment, currently only support one node
      if (clusterInfo.nodeNames.size != 1) {
        val key = ShabondiDefinitions.NODE_NAMES_DEFINITION.key
        throw new IllegalArgumentException(s"$key only support one node currently.")
      }
      objectChecker.checkList
        .shabondi(clusterInfo.key)
        .brokerCluster(clusterInfo.brokerClusterKey, DataCondition.RUNNING)
        .topics(checkTopics, DataCondition.RUNNING)
        .check()
        .flatMap { objInfo =>
          val condition = objInfo.shabondiClusterInfos.head._2
          condition match {
            case DataCondition.RUNNING => Future.unit
            case DataCondition.STOPPED =>
              val brokerClusterInfo = objInfo.brokerClusterInfos.head._1
              shabondiCollie.creator
                .settings(clusterInfo.settings)
                .name(clusterInfo.name)
                .group(clusterInfo.group)
                .nodeNames(clusterInfo.nodeNames)
                .brokerClusterKey(brokerClusterInfo.key)
                .brokers(brokerClusterInfo.connectionProps)
                .threadPool(executionContext)
                .create()
          }
        }
    }

  private[this] def hookBeforeStop: HookOfAction[ShabondiClusterInfo] = (_, _, _) => Future.unit

  private[this] def hookBeforeDelete: HookBeforeDelete = _ => Future.unit

  def apply(
    implicit store: DataStore,
    objectChecker: DataChecker,
    shabondiCollie: ShabondiCollie,
    serviceCollie: ServiceCollie,
    meterCache: MetricsCache,
    executionContext: ExecutionContext
  ): server.Route = {
    clusterRoute[ShabondiClusterInfo, Creation, Updating](
      root = PREFIX,
      hookOfCreation = hookOfCreation,
      hookOfUpdating = hookOfUpdating,
      hookOfStart = hookOfStart,
      hookBeforeStop = hookBeforeStop,
      hookBeforeDelete = hookBeforeDelete
    )
  }
}
