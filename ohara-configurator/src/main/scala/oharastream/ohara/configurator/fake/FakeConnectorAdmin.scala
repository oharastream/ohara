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

package oharastream.ohara.configurator.fake

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

import oharastream.ohara.client.configurator.v0.ConnectorApi.State
import oharastream.ohara.client.configurator.v0.FileInfoApi.ClassInfo
import oharastream.ohara.client.kafka.ConnectorAdmin
import oharastream.ohara.client.kafka.ConnectorAdmin.{Creator, Validator}
import oharastream.ohara.client.kafka.WorkerJson.{
  ConnectorCreationResponse,
  KafkaConnectorConfig,
  KafkaConnectorInfo,
  KafkaConnectorStatus,
  KafkaTaskStatus
}
import oharastream.ohara.common.setting.ConnectorKey
import oharastream.ohara.common.util.CommonUtils
import oharastream.ohara.configurator.ReflectionUtils
import oharastream.ohara.kafka.connector.json._
import org.apache.kafka.connect.runtime.AbstractHerder
import org.apache.kafka.connect.sink.SinkConnector
import org.apache.kafka.connect.source.SourceConnector
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

private[configurator] class FakeConnectorAdmin extends ConnectorAdmin {
  private[this] val cachedConnectors      = new ConcurrentHashMap[ConnectorKey, Map[String, String]]()
  private[this] val cachedConnectorsState = new ConcurrentHashMap[ConnectorKey, State]()

  override def connectorCreator(): Creator = new Creator(ConnectorFormatter.of()) {
    override protected def doCreate(
      executionContext: ExecutionContext,
      creation: Creation
    ): Future[ConnectorCreationResponse] =
      if (cachedConnectors.contains(ConnectorKey.requirePlain(creation.name())))
        Future.failed(new IllegalStateException(s"the connector:${creation.name()} exists!"))
      else {
        import scala.jdk.CollectionConverters._
        cachedConnectors.put(ConnectorKey.requirePlain(creation.name()), creation.configs().asScala.toMap)
        cachedConnectorsState.put(ConnectorKey.requirePlain(creation.name()), State.RUNNING)
        Future.successful(ConnectorCreationResponse(creation.name(), creation.configs().asScala.toMap, Seq.empty))
      }
  }

  override def delete(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Unit] =
    try if (cachedConnectors.remove(connectorKey) == null)
      Future.failed(new IllegalStateException(s"Connector:$connectorKey doesn't exist!"))
    else Future.successful(())
    finally cachedConnectorsState.remove(connectorKey)

  override def activeConnectors()(implicit executionContext: ExecutionContext): Future[Seq[ConnectorKey]] =
    Future.successful(cachedConnectors.keys.asScala.toSeq)

  override def connectionProps: String = "Unknown"

  override def status(
    connectorKey: ConnectorKey
  )(implicit executionContext: ExecutionContext): Future[KafkaConnectorInfo] =
    if (!cachedConnectors.containsKey(connectorKey))
      Future.failed(new IllegalArgumentException(s"Connector:$connectorKey doesn't exist"))
    else
      Future.successful(
        KafkaConnectorInfo(
          connector = KafkaConnectorStatus(cachedConnectorsState.get(connectorKey).name, "fake id", None),
          tasks = Seq(
            KafkaTaskStatus(
              id = 1,
              state = cachedConnectorsState.get(connectorKey).name,
              worker_id = CommonUtils.hostname(),
              trace = None
            )
          )
        )
      )

  override def config(
    connectorKey: ConnectorKey
  )(implicit executionContext: ExecutionContext): Future[KafkaConnectorConfig] = {
    val map = cachedConnectors.get(connectorKey)
    if (map == null)
      Future.failed(new IllegalArgumentException(s"$connectorKey doesn't exist"))
    else Future.successful(map.toJson.convertTo[KafkaConnectorConfig])
  }

  override def taskStatus(connectorKey: ConnectorKey, id: Int)(
    implicit executionContext: ExecutionContext
  ): Future[KafkaTaskStatus] =
    if (!cachedConnectors.containsKey(connectorKey))
      Future.failed(new IllegalArgumentException(s"$connectorKey doesn't exist"))
    else
      Future.successful(
        KafkaTaskStatus(
          id = 1,
          state = cachedConnectorsState.get(connectorKey).name,
          worker_id = CommonUtils.hostname(),
          trace = None
        )
      )

  override def pause(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Unit] =
    if (!cachedConnectors.containsKey(connectorKey))
      Future.failed(new IllegalArgumentException(s"$connectorKey doesn't exist"))
    else Future.successful(cachedConnectorsState.put(connectorKey, State.PAUSED))

  override def resume(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Unit] =
    if (!cachedConnectors.containsKey(connectorKey))
      Future.failed(new IllegalArgumentException(s"$connectorKey doesn't exist"))
    else Future.successful(cachedConnectorsState.put(connectorKey, State.RUNNING))

  override def connectorValidator(): Validator = new Validator(ConnectorFormatter.of()) {
    override protected def doValidate(
      executionContext: ExecutionContext,
      validation: Validation
    ): Future[SettingInfo] = {
      // TODO: this implementation use kafka private APIs ... by chia
      Future {
        val instance = Class.forName(validation.className).getDeclaredConstructor().newInstance()
        val (connectorType, configDef, values) = instance match {
          case c: SourceConnector => ("source", c.config(), c.config().validate(validation.settings))
          case c: SinkConnector   => ("sink", c.config(), c.config().validate(validation.settings))
          case _                  => throw new IllegalArgumentException(s"who are you ${validation.className} ???")
        }
        SettingInfo.of(
          AbstractHerder.generateResult(connectorType, configDef.configKeys(), values, Collections.emptyList())
        )
      }(executionContext)
    }
  }

  override def connectorDefinitions()(implicit executionContext: ExecutionContext): Future[Map[String, ClassInfo]] =
    Future.successful(
      ReflectionUtils.localConnectorDefinitions
        .map(d => d.className -> d)
        .toMap
    )
}

object FakeConnectorAdmin {
  def apply(): FakeConnectorAdmin = new FakeConnectorAdmin
}
