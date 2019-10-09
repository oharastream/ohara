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
import com.island.ohara.agent.{BrokerCollie, StreamCollie, WorkerCollie}
import com.island.ohara.client.configurator.Data
import com.island.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import com.island.ohara.client.configurator.v0.ConnectorApi.ConnectorDescription
import com.island.ohara.client.configurator.v0.MetricsApi._
import com.island.ohara.client.configurator.v0.PipelineApi._
import com.island.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import com.island.ohara.client.configurator.v0.TopicApi
import com.island.ohara.client.configurator.v0.TopicApi.TopicInfo
import com.island.ohara.client.configurator.v0.WorkerApi.WorkerClusterInfo
import com.island.ohara.client.kafka.{TopicAdmin, WorkerClient}
import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.configurator.route.hook._
import com.island.ohara.configurator.store.{DataStore, MeterCache}
import com.island.ohara.kafka.connector.json.ConnectorDefUtils
import com.typesafe.scalalogging.Logger

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
private[configurator] object PipelineRoute {
  private[this] lazy val LOG = Logger(PipelineRoute.getClass)

  private[this] def toAbstract(data: ConnectorDescription, clusterInfo: WorkerClusterInfo, workerClient: WorkerClient)(
    implicit executionContext: ExecutionContext,
    meterCache: MeterCache): Future[ObjectAbstract] =
    workerClient
      .connectorDefinition(data.className)
      .map(
        connectorDefinition =>
          ObjectAbstract(
            group = data.group,
            name = data.name,
            kind = ConnectorDefUtils.kind(connectorDefinition.definitions.asJava),
            className = Some(data.className),
            state = None,
            error = None,
            // the group of counter is equal to connector's name (this is a part of kafka's core setting)
            // Hence, we filter the connectors having different "name" (we use name instead of name in creating connector)
            metrics = Metrics(meterCache.meters(clusterInfo).getOrElse(data.key.connectorNameOnKafka, Seq.empty)),
            lastModified = data.lastModified,
            tags = data.tags
        )
      )
      .flatMap { obj =>
        workerClient
          .status(data.key)
          .map { connectorInfo =>
            obj.copy(
              state = Some(connectorInfo.connector.state),
              error = connectorInfo.connector.trace
            )
          }
          // we don't put this recovery in the final chain since we want to keep the definitions fetched from kafka
          .recover {
            case e: Throwable =>
              LOG.debug(s"failed to fetch obj for $data", e)
              obj
          }
      }

  private[this] def toAbstract(data: TopicInfo, clusterInfo: BrokerClusterInfo, topicAdmin: TopicAdmin)(
    implicit meterCache: MeterCache,
    executionContext: ExecutionContext): Future[ObjectAbstract] = {
    topicAdmin
      .exist(data.key)
      .map(try if (_) Some(TopicApi.TopicState.RUNNING) else None
      finally topicAdmin.close())
      .map(_.map(_.name))
      .map(state =>
        ObjectAbstract(
          group = data.group,
          name = data.name,
          kind = data.kind,
          className = None,
          state = state,
          error = None,
          // noted we create a topic with name rather than name
          metrics = Metrics(meterCache.meters(clusterInfo).getOrElse(data.topicNameOnKafka, Seq.empty)),
          lastModified = data.lastModified,
          tags = data.tags
      ))
  }

  private[this] def toAbstract(data: StreamClusterInfo, clusterInfo: StreamClusterInfo)(
    implicit meterCache: MeterCache): Future[ObjectAbstract] =
    Future.successful(
      ObjectAbstract(
        group = data.group,
        name = data.name,
        kind = data.kind,
        className = data.definition.map(_.className),
        state = clusterInfo.state,
        error = None,
        metrics = Metrics(meterCache.meters(clusterInfo).getOrElse(StreamRoute.STREAM_APP_GROUP, Seq.empty)),
        lastModified = data.lastModified,
        tags = data.tags
      ))

  private[this] def toAbstract(data: Data, error: Option[String]): Future[ObjectAbstract] = Future.successful(
    ObjectAbstract(
      group = data.group,
      name = data.name,
      kind = data.kind,
      // Just cast the input data to get the correct className
      className = data match {
        case description: ConnectorDescription => Some(description.className)
        case description: StreamClusterInfo    => description.definition.map(_.className)
        case _                                 => None
      },
      state = None,
      error = error,
      metrics = Metrics.EMPTY,
      lastModified = data.lastModified,
      tags = data.tags
    ))

  private[this] def toAbstract(obj: Data)(implicit brokerCollie: BrokerCollie,
                                          workerCollie: WorkerCollie,
                                          streamCollie: StreamCollie,
                                          store: DataStore,
                                          adminCleaner: AdminCleaner,
                                          executionContext: ExecutionContext,
                                          meterCache: MeterCache): Future[ObjectAbstract] = obj match {
    case data: ConnectorDescription =>
      CollieUtils.workerClient(data.workerClusterKey).flatMap {
        case (workerClusterInfo, workerClient) => toAbstract(data, workerClusterInfo, workerClient)
      }
    case data: TopicInfo =>
      CollieUtils.topicAdmin(data.brokerClusterKey).flatMap {
        case (cluster, admin) => toAbstract(data, cluster, admin)
      }
    case data: StreamClusterInfo =>
      streamCollie.cluster(data.key).map(_._1).map(data.update).flatMap(toAbstract(data, _))
    case _ => toAbstract(obj, None)
  }

  /**
    * collect the abstract for all objects in flow. This is a expensive operation since it invokes a bunch of threads
    * to retrieve the information from many remote nodes.
    * @param pipeline pipeline
    * @param store data store
    * @param executionContext thread pool
    * @param meterCache meter cache
    * @return updated pipeline
    */
  private[this] def updateObjects(pipeline: Pipeline)(implicit brokerCollie: BrokerCollie,
                                                      workerCollie: WorkerCollie,
                                                      streamCollie: StreamCollie,
                                                      adminCleaner: AdminCleaner,
                                                      store: DataStore,
                                                      executionContext: ExecutionContext,
                                                      meterCache: MeterCache): Future[Pipeline] =
    Future
      .traverse(pipeline.flows.flatMap(flow => flow.to ++ Set(flow.from)))(store.raws)
      .map(_.flatten.toSet)
      .flatMap(Future.traverse(_) { obj =>
        toAbstract(obj).recoverWith {
          case e: Throwable => toAbstract(obj, Some(e.getMessage))
        }
      })
      .map(objects => pipeline.copy(objects = objects))

  private[this] def hookOfGet(implicit brokerCollie: BrokerCollie,
                              workerCollie: WorkerCollie,
                              streamCollie: StreamCollie,
                              adminCleaner: AdminCleaner,
                              store: DataStore,
                              executionContext: ExecutionContext,
                              meterCache: MeterCache): HookOfGet[Pipeline] = updateObjects(_)

  private[this] def hookOfList(implicit brokerCollie: BrokerCollie,
                               workerCollie: WorkerCollie,
                               streamCollie: StreamCollie,
                               adminCleaner: AdminCleaner,
                               store: DataStore,
                               executionContext: ExecutionContext,
                               meterCache: MeterCache): HookOfList[Pipeline] = Future.traverse(_)(updateObjects)

  private[this] def hookOfCreation(implicit brokerCollie: BrokerCollie,
                                   workerCollie: WorkerCollie,
                                   streamCollie: StreamCollie,
                                   adminCleaner: AdminCleaner,
                                   store: DataStore,
                                   executionContext: ExecutionContext,
                                   meterCache: MeterCache): HookOfCreation[Creation, Pipeline] =
    (creation: Creation) =>
      updateObjects(
        Pipeline(
          group = creation.group,
          name = creation.name,
          flows = creation.flows,
          objects = Set.empty,
          lastModified = CommonUtils.current(),
          tags = creation.tags
        ))

  private[this] def HookOfUpdating(implicit brokerCollie: BrokerCollie,
                                   workerCollie: WorkerCollie,
                                   streamCollie: StreamCollie,
                                   adminCleaner: AdminCleaner,
                                   store: DataStore,
                                   executionContext: ExecutionContext,
                                   meterCache: MeterCache): HookOfUpdating[Creation, Updating, Pipeline] =
    (key: ObjectKey, update: Updating, previous: Option[Pipeline]) =>
      updateObjects(
        Pipeline(
          group = key.group,
          name = key.name,
          flows = update.flows.getOrElse(previous.map(_.flows).getOrElse(Seq.empty)),
          objects = previous.map(_.objects).getOrElse(Set.empty),
          lastModified = CommonUtils.current(),
          tags = update.tags.getOrElse(previous.map(_.tags).getOrElse(Map.empty))
        ))

  private[this] def hookBeforeDelete: HookBeforeDelete = _ => Future.unit

  private[this] def hookOfRefresh(implicit store: DataStore, executionContext: ExecutionContext): HookOfAction =
    (key: ObjectKey, _, _) =>
      store.get[Pipeline](key).flatMap { pipelineOption =>
        if (pipelineOption.isEmpty) Future.unit
        else {
          val pipeline = pipelineOption.get
          val objKeys = pipeline.flows.flatMap(flow => flow.to + flow.from)
          Future
            .traverse(objKeys)(key => store.raws(key).map(objs => key -> objs.nonEmpty))
            // filter the nonexistent objKeys
            .map(_.filter(_._2).map(_._1).toSeq)
            .map(
              existedObjKeys =>
                pipeline.copy(
                  flows = pipeline.flows
                  // remove the flow if the obj in "from" is nonexistent
                    .filter(flow => existedObjKeys.contains(flow.from))
                    // remove nonexistent obj from "to"
                    .map(flow => flow.copy(to = flow.to.filter(existedObjKeys.contains)))))
            .flatMap(pipeline => store.add[Pipeline](pipeline))
            .map(_ => Unit)
        }
    }

  def apply(implicit brokerCollie: BrokerCollie,
            workerCollie: WorkerCollie,
            streamCollie: StreamCollie,
            adminCleaner: AdminCleaner,
            store: DataStore,
            executionContext: ExecutionContext,
            meterCache: MeterCache): server.Route =
    route[Creation, Updating, Pipeline](
      root = PIPELINES_PREFIX_PATH,
      hookOfCreation = hookOfCreation,
      HookOfUpdating = HookOfUpdating,
      hookOfGet = hookOfGet,
      hookOfList = hookOfList,
      hookBeforeDelete = hookBeforeDelete,
      hookOfActions = Map(REFRESH_COMMAND -> hookOfRefresh)
    )
}
