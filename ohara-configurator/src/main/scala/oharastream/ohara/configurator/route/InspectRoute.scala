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

import java.nio.charset.StandardCharsets

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives.{entity, _}
import oharastream.ohara.agent.{BrokerCollie, ServiceCollie, WorkerCollie}
import oharastream.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import oharastream.ohara.client.configurator.v0.FileInfoApi.{ClassInfo, FileInfo}
import oharastream.ohara.client.configurator.v0.InspectApi._
import oharastream.ohara.client.configurator.v0.StreamApi.StreamClusterInfo
import oharastream.ohara.client.configurator.v0.WorkerApi.WorkerClusterInfo
import oharastream.ohara.client.configurator.v0.ZookeeperApi.ZookeeperClusterInfo
import oharastream.ohara.client.configurator.v0.{
  BrokerApi,
  ErrorApi,
  InspectApi,
  StreamApi,
  TopicApi,
  WorkerApi,
  ZookeeperApi
}
import oharastream.ohara.client.database.DatabaseClient
import oharastream.ohara.client.kafka.ConnectorAdmin
import oharastream.ohara.common.data.{Row, Serializer}
import oharastream.ohara.common.setting.{ConnectorKey, ObjectKey, TopicKey}
import oharastream.ohara.common.util.{CommonUtils, Releasable, VersionUtils}
import oharastream.ohara.configurator.Configurator.Mode
import oharastream.ohara.configurator.fake.FakeConnectorAdmin
import oharastream.ohara.configurator.route.ObjectChecker.Condition.RUNNING
import oharastream.ohara.configurator.store.DataStore
import oharastream.ohara.kafka.Consumer.Record
import oharastream.ohara.kafka.{TopicAdmin, Consumer, Header}
import oharastream.ohara.stream.config.StreamDefUtils
import com.typesafe.scalalogging.Logger
import spray.json.{DeserializationException, JsNull, JsObject}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

private[configurator] object InspectRoute {
  private[this] lazy val LOG = Logger(InspectRoute.getClass)

  /**
    * we reuse the great conversion of JIO connectors
    * @param row row
    * @return json representation
    */
  def toJson(row: Row): JsObject = oharastream.ohara.client.configurator.v0.toJson(row)

  private[this] def topicData(records: Seq[Record[Array[Byte], Array[Byte]]]): TopicData =
    TopicData(
      records.reverse
        .filter(_.key().isPresent)
        .map(record => (record.partition(), record.offset(), record.key().get(), record.headers().asScala))
        .map {
          case (partition, offset, bytes, headers) =>
            var error: Option[String] = None
            def swallowException[T](f: => Option[T]): Option[T] =
              try f
              catch {
                case e: Throwable =>
                  error = Some(e.getMessage)
                  None
              }

            Message(
              partition = partition,
              offset = offset,
              // only Ohara source connectors have this header
              sourceClass = swallowException(
                headers.find(_.key() == Header.SOURCE_CLASS_KEY).map(h => new String(h.value(), StandardCharsets.UTF_8))
              ),
              sourceKey = swallowException(
                headers
                  .find(_.key() == Header.SOURCE_KEY_KEY)
                  .map(h => new String(h.value(), StandardCharsets.UTF_8))
                  .map(ObjectKey.toObjectKey)
              ),
              value = swallowException(Some(toJson(Serializer.ROW.from(bytes)))),
              error = error
            )
        }
    )

  private[this] val zookeeperDefinition = ServiceDefinition(
    imageName = ZookeeperApi.IMAGE_NAME_DEFAULT,
    settingDefinitions = ZookeeperApi.DEFINITIONS,
    classInfos = Seq.empty
  )

  private[this] val brokerDefinition = ServiceDefinition(
    imageName = BrokerApi.IMAGE_NAME_DEFAULT,
    settingDefinitions = BrokerApi.DEFINITIONS,
    classInfos = Seq(
      ClassInfo(
        className = "N/A",
        classType = "topic",
        settingDefinitions = TopicApi.DEFINITIONS
      )
    )
  )

  def apply(mode: Mode)(
    implicit brokerCollie: BrokerCollie,
    adminCleaner: AdminCleaner,
    dataStore: DataStore,
    serviceCollie: ServiceCollie,
    workerCollie: WorkerCollie,
    objectChecker: ObjectChecker,
    executionContext: ExecutionContext
  ): server.Route = pathPrefix(INSPECT_PREFIX_PATH) {
    path(RDB_PREFIX_PATH) {
      post {
        entity(as[RdbQuery]) { query =>
          complete(both(query.workerClusterKey).flatMap {
            case (_, topicAdmin, _, connectorAdmin) =>
              connectorAdmin match {
                case _: FakeConnectorAdmin =>
                  val client = DatabaseClient.builder.url(query.url).user(query.user).password(query.password).build
                  try Future.successful(
                    RdbInfo(
                      name = client.databaseType,
                      tables = client.tableQuery
                        .catalog(query.catalogPattern)
                        .schema(query.schemaPattern)
                        .tableName(query.tableName)
                        .execute()
                    )
                  )
                  finally client.close()
                case _ =>
                  rdbInfo(
                    connectorAdmin,
                    topicAdmin,
                    query
                  )
              }
          })
        }
      }
    } ~ path(TOPIC_PREFIX_PATH / Segment) { topicName =>
      post {
        parameters(
          (
            GROUP_KEY ? GROUP_DEFAULT,
            TOPIC_TIMEOUT_KEY.as[Long] ? TOPIC_TIMEOUT_DEFAULT.toMillis,
            TOPIC_LIMIT_KEY.as[Int] ? TOPIC_LIMIT_DEFAULT
          )
        ) { (group, timeoutMs, limit) =>
          val topicKey = TopicKey.of(group, topicName)
          if (limit <= 0)
            throw DeserializationException(
              s"the limit must be bigger than zero. actual:$limit",
              fieldNames = List(TOPIC_LIMIT_KEY)
            )
          complete(
            objectChecker.checkList
              .topic(topicKey, RUNNING)
              .check()
              .map(_.topicInfos.head._1.brokerClusterKey)
              .flatMap(
                brokerClusterKey =>
                  objectChecker.checkList
                    .brokerCluster(brokerClusterKey, RUNNING)
                    .check()
                    .map(_.runningBrokers.head.connectionProps)
              )
              .map { connectionProps =>
                val consumer = Consumer
                  .builder()
                  .connectionProps(connectionProps)
                  .topicName(topicKey.topicNameOnKafka())
                  // move the offset to first place so we can break the wait if there is no data
                  .offsetFromBegin()
                  .build()
                try {
                  val endTime = CommonUtils.current() + timeoutMs
                  // first poll: it not only fetch data but also subscribe the partitions.
                  consumer.poll(java.time.Duration.ofMillis(timeoutMs), 1)
                  consumer.endOffsets().asScala.foreach {
                    case (tp, offset) => consumer.seek(tp, offset - limit)
                  }
                  topicData(
                    consumer
                    // even if the timeout reach the limit, we still give a last try :)
                      .poll(java.time.Duration.ofMillis(Math.max(1000L, endTime - CommonUtils.current())), limit)
                      .asScala
                      .slice(0, limit)
                  )
                } finally Releasable.close(consumer)
              }
          )
        }
      }
    } ~ path(FILE_PREFIX_PATH) {
      FileInfoRoute.routeOfUploadingFile(urlMaker = _ => None, storeOption = None)
    } ~ path(CONFIGURATOR_PREFIX_PATH) {
      complete(
        ConfiguratorInfo(
          versionInfo = ConfiguratorVersion(
            version = VersionUtils.VERSION,
            branch = VersionUtils.BRANCH,
            user = VersionUtils.USER,
            revision = VersionUtils.REVISION,
            date = VersionUtils.DATE
          ),
          mode = mode.toString
        )
      )
    } ~ pathPrefix(WORKER_PREFIX_PATH) {
      path(Segment) { name =>
        parameters(GROUP_KEY ? GROUP_DEFAULT) { group =>
          complete(
            dataStore
              .value[WorkerClusterInfo](ObjectKey.of(group, name))
              .flatMap(workerCollie.connectorAdmin)
              .flatMap(_.connectorDefinitions())
              .recover {
                case _: Throwable => Seq.empty
              }
              .map { classInfos =>
                ServiceDefinition(
                  imageName = WorkerApi.IMAGE_NAME_DEFAULT,
                  settingDefinitions = WorkerApi.DEFINITIONS,
                  classInfos = classInfos
                )
              }
          )
        }
      } ~ pathEnd {
        complete(
          ServiceDefinition(
            imageName = WorkerApi.IMAGE_NAME_DEFAULT,
            settingDefinitions = WorkerApi.DEFINITIONS,
            classInfos = Seq.empty
          )
        )
      }
    } ~ pathPrefix(BROKER_PREFIX_PATH) {
      path(Segment) { name =>
        parameters(GROUP_KEY ? GROUP_DEFAULT) { group =>
          complete(
            dataStore
              .value[BrokerClusterInfo](ObjectKey.of(group, name))
              .map(_ => brokerDefinition)
          )
        }
      } ~ pathEnd {
        complete(brokerDefinition)
      }
    } ~ pathPrefix(ZOOKEEPER_PREFIX_PATH) {
      path(Segment) { name =>
        parameters(GROUP_KEY ? GROUP_DEFAULT) { group =>
          complete(
            dataStore
              .value[ZookeeperClusterInfo](ObjectKey.of(group, name))
              .map(_ => zookeeperDefinition)
          )
        }
      } ~ pathEnd {
        complete(zookeeperDefinition)
      }
    } ~ pathPrefix(STREAM_PREFIX_PATH) {
      path(Segment) { name =>
        parameters(GROUP_KEY ? GROUP_DEFAULT) { group =>
          complete(
            dataStore
              .value[StreamClusterInfo](ObjectKey.of(group, name))
              .map(_.jarKey)
              .flatMap(dataStore.value[FileInfo])
              .map(file => Seq(file.url.get))
              .flatMap(serviceCollie.fileContent)
              .recover {
                case e: Throwable =>
                  LOG.warn(s"failed to find definitions for stream:${ObjectKey.of(group, name)}", e)
                  FileContent.empty
              }
              .map(
                fileContent =>
                  ServiceDefinition(
                    imageName = StreamApi.IMAGE_NAME_DEFAULT,
                    settingDefinitions = StreamDefUtils.DEFAULT.values().asScala.toSeq,
                    classInfos = fileContent.streamClassInfos
                  )
              )
          )
        }
      } ~ pathEnd {
        complete(
          ServiceDefinition(
            imageName = StreamApi.IMAGE_NAME_DEFAULT,
            settingDefinitions = StreamDefUtils.DEFAULT.values().asScala.toSeq,
            classInfos = Seq.empty
          )
        )
      }
    }
  }

  /**
    * create a connector to query the DB.
    * Noted: we don't query the db via Configurator since the connection to DB requires specific drvier and it is not
    * available on Configurator. By contrast, the worker cluster which will be used to run Connector should have been
    * deployed the driver so we use our specific connector to query DB.
    * @return rdb information
    */
  private def rdbInfo(connectorAdmin: ConnectorAdmin, topicAdmin: TopicAdmin, request: RdbQuery)(
    implicit executionContext: ExecutionContext
  ): Future[RdbInfo] = {
    val requestId: String = CommonUtils.randomString()
    val connectorKey      = ConnectorKey.of(CommonUtils.randomString(5), s"Validator-${CommonUtils.randomString()}")
    connectorAdmin
      .connectorCreator()
      .connectorKey(connectorKey)
      .className("oharastream.ohara.connector.validation.Validator")
      .numberOfTasks(1)
      .topicKey(InspectApi.INTERNAL_TOPIC_KEY)
      .settings(
        Map(
          InspectApi.SETTINGS_KEY -> JsObject(InspectApi.RDB_QUERY_JSON_FORMAT.write(request).asJsObject.fields.filter {
            case (_, value) =>
              value match {
                case JsNull => false
                case _      => true
              }
          }).toString(),
          InspectApi.REQUEST_ID -> requestId,
          InspectApi.TARGET_KEY -> InspectApi.RDB_PREFIX_PATH
        )
      )
      .threadPool(executionContext)
      .create()
      .map { _ =>
        // TODO: receiving all messages may be expensive...by chia
        val client = Consumer
          .builder()
          .connectionProps(topicAdmin.connectionProps)
          .offsetFromBegin()
          .topicName(InspectApi.INTERNAL_TOPIC_KEY.topicNameOnKafka)
          .keySerializer(Serializer.STRING)
          .valueSerializer(Serializer.OBJECT)
          .build()

        try client
          .poll(java.time.Duration.ofMillis(30 * 1000), 1)
          .asScala
          .filter(_.key().isPresent)
          .filter(_.key().get.equals(requestId))
          .filter(_.value().isPresent)
          .map(_.value().get())
          .filter {
            case _: RdbInfo => true
            case e: ErrorApi.Error =>
              throw new IllegalArgumentException(e.message)
            case _ => false
          }
          .map(_.asInstanceOf[RdbInfo])
          .head
        finally Releasable.close(client)
      }
      .flatMap(r => connectorAdmin.delete(connectorKey).map(_ => r))
  }
}
