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

package com.island.ohara.client.kafka

import java.net.HttpRetryException
import java.util.Objects
import java.util.concurrent.TimeUnit

import akka.stream.StreamTcpException
import com.island.ohara.client.HttpExecutor
import com.island.ohara.client.configurator.v0.Definition
import com.island.ohara.client.kafka.WorkerJson._
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.data.Column
import com.island.ohara.common.setting.{ConnectorKey, ObjectKey, SettingDef, TopicKey}
import com.island.ohara.common.util.CommonUtils
import com.island.ohara.kafka.connector.json.{ConverterType, _}
import com.typesafe.scalalogging.Logger
import org.apache.kafka.connect.runtime.rest.entities.ConfigInfos
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

/**
  * a helper class used to send the rest request to kafka worker.
  *
  * Noted: the input key is a specific form across whole project and it is NOT supported by kafka. Hence, the response
  * of this class is "pure" kafka response and a astounding fact is the "connector name" in response is a salt name composed by
  * group and name from input key. For example, the input key is ("g0", "n0") and a salt connector name called "g0-n0"
  * ensues from response.
  */
trait WorkerClient {

  /**
    * start a process to create source/sink connector
    * @return connector creator
    */
  def connectorCreator(): WorkerClient.Creator

  /**
    * start a process to verify the connector
    * @return connector validator
    */
  def connectorValidator(): WorkerClient.Validator

  /**
    * delete a connector from worker cluster
    * @param connectorKey connector's key
    * @return async future containing nothing
    */
  def delete(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Unit] = delete(
    connectorKey.connectorNameOnKafka())

  /**
    * delete a connector from worker cluster. Make sure you do know the real name!
    * @param connectorName connector's name
    * @return async future containing nothing
    */
  def delete(connectorName: String)(implicit executionContext: ExecutionContext): Future[Unit]

  /**
    * pause a running connector
    * @param connectorKey connector's key
    * @return async future containing nothing
    */
  def pause(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Unit]

  /**
    * resume a paused connector
    * @param connectorKey connector's key
    * @return async future containing nothing
    */
  def resume(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Unit]

  /**
    * list available plugins.
    * This main difference between plugins() and connectors() is that plugins() spend only one request
    * to remote server. In contrast, connectors() needs multi-requests to fetch all details from
    * remote server. Furthermore, connectors list only sub class from ohara's connectors
    * @return async future containing connector details
    */
  def plugins()(implicit executionContext: ExecutionContext): Future[Seq[Plugin]]

  /**
    * list ohara's connector.
    * NOTED: the plugins which are not sub class of ohara connector are not included.
    * @return async future containing connector details
    */
  def connectorDefinitions()(implicit executionContext: ExecutionContext): Future[Seq[Definition]]

  /**
    * get the definitions for specific connector
    * @param className connector's class name
    * @param executionContext thread pool
    * @return definition
    */
  def connectorDefinition(className: String)(implicit executionContext: ExecutionContext): Future[Definition] =
    connectorDefinitions().map(
      _.find(_.className == className).getOrElse(throw new NoSuchElementException(s"$className does not exist")))

  /**
    * list available plugin's names
    * @return async future containing connector's names
    */
  def activeConnectors()(implicit executionContext: ExecutionContext): Future[Seq[String]]

  /**
    * @return worker's connection props
    */
  def connectionProps: String

  /**
    * @param connectorKey connector's key
    * @return status of connector
    */
  def status(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[ConnectorInfo]

  /**
    * @param connectorKey connector's key
    * @return configuration of connector
    */
  def config(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[ConnectorConfig]

  /**
    * @param connectorKey connector's key
    * @param id task's id
    * @return task status
    */
  def taskStatus(connectorKey: ConnectorKey, id: Int)(implicit executionContext: ExecutionContext): Future[TaskStatus]

  /**
    * Check whether a connector name is used in creating connector (even if the connector fails to start, this method
    * still return true)
    * @param connectorKey connector key
    * @return true if connector exists
    */
  def exist(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Boolean] =
    activeConnectors().map(_.contains(connectorKey.connectorNameOnKafka()))

  def nonExist(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Boolean] =
    exist(connectorKey).map(!_)

  /**
    * list all definitions for connector.
    * This is a helper method which passing "nothing" to validate the connector and then fetch only the definitions from report
    * @param connectorClassName class name
    * @param executionContext thread pool
    * @return definition list
    */
  def definitions(connectorClassName: String)(implicit executionContext: ExecutionContext): Future[Seq[SettingDef]] =
    connectorValidator()
      .className(connectorClassName)
      // kafka 2.x requires topic names for all sink connectors so we add a random topic for this request.
      .topicKey(TopicKey.of("fake_group", "fake_name"))
      .run()
      .map(_.settings().asScala.map(_.definition()))

}

object WorkerClient {

  def apply(connectionProps: String): WorkerClient = builder.connectionProps(connectionProps).build

  def builder: Builder = new Builder

  private[this] val LOG = Logger(WorkerClient.getClass)

  class Builder private[WorkerClient] extends com.island.ohara.common.pattern.Builder[WorkerClient] {
    private[this] var _workerAddress: Seq[String] = Seq.empty
    private[this] var retryLimit: Int = 3
    private[this] var retryInternal: Duration = 3 seconds

    def connectionProps(connectionProps: String): Builder = {
      this._workerAddress = CommonUtils.requireNonEmpty(connectionProps).split(",")
      this
    }

    @Optional("default value is 3")
    def retryLimit(retryLimit: Int): Builder = {
      this.retryLimit = CommonUtils.requirePositiveInt(retryLimit)
      this
    }

    def disableRetry(): Builder = {
      this.retryLimit = 0
      this
    }

    @Optional("default value is 3 seconds")
    def retryInternal(retryInternal: Duration): Builder = {
      this.retryInternal = Objects.requireNonNull(retryInternal)
      this
    }

    /**
      * Create a default implementation of worker client.
      * NOTED: default implementation use a global akka system to handle http request/response. It means the connection
      * sent by this worker client may be influenced by other instances.
      * @return worker client
      */
    override def build: WorkerClient = new WorkerClient() {
      private[this] def workerAddress: String = _workerAddress(Random.nextInt(_workerAddress.size))

      /**
        * kafka worker has weakness of doing consistent operation so it is easy to encounter conflict error. Wrapping all operations with
        * retry can relax us... by chia
        * @param exec do request
        * @tparam T response type
        * @return response
        */
      private[this] def retry[T](exec: () => Future[T], msg: String, retryCount: Int = 0)(
        implicit executionContext: ExecutionContext): Future[T] =
        exec().recoverWith {
          case e @ (_: HttpRetryException | _: StreamTcpException) =>
            LOG.info(s"$msg $retryCount/$retryLimit", e)
            if (retryCount < retryLimit) {
              TimeUnit.MILLISECONDS.sleep(retryInternal.toMillis)
              retry(exec, msg, retryCount + 1)
            } else throw e
        }

      override def connectorCreator(): Creator = (executionContext, creation) => {
        implicit val exec: ExecutionContext = executionContext
        retry(
          () =>
            HttpExecutor.SINGLETON.post[Creation, ConnectorCreationResponse, Error](
              s"http://$workerAddress/connectors",
              creation
          ),
          "connectorCreator"
        )
      }

      override def delete(connectorName: String)(implicit executionContext: ExecutionContext): Future[Unit] =
        retry(() => HttpExecutor.SINGLETON.delete[Error](s"http://$workerAddress/connectors/$connectorName"),
              s"delete $connectorName")

      override def plugins()(implicit executionContext: ExecutionContext): Future[Seq[Plugin]] = retry(
        () => HttpExecutor.SINGLETON.get[Seq[Plugin], Error](s"http://$workerAddress/connector-plugins"),
        s"fetch plugins $workerAddress")

      override def connectorDefinitions()(implicit executionContext: ExecutionContext): Future[Seq[Definition]] =
        plugins()
          .flatMap(Future.traverse(_) { p =>
            definitions(p.className)
              .map(
                definitions =>
                  Definition(
                    className = p.className,
                    definitions = definitions
                ))
              .recover {
                // It should fail if we try to parse non-ohara connectors
                case _: IllegalArgumentException => Definition(p.className, Seq.empty)
              }
          })
          .map(_.filter(_.definitions.nonEmpty))

      override def activeConnectors()(implicit executionContext: ExecutionContext): Future[Seq[String]] = retry(
        () => HttpExecutor.SINGLETON.get[Seq[String], Error](s"http://$workerAddress/connectors"),
        "fetch active connectors")

      override def connectionProps: String = _workerAddress.mkString(",")

      override def status(connectorKey: ConnectorKey)(
        implicit executionContext: ExecutionContext): Future[ConnectorInfo] = retry(
        () =>
          HttpExecutor.SINGLETON.get[ConnectorInfo, Error](
            s"http://$workerAddress/connectors/${connectorKey.connectorNameOnKafka()}/status"),
        s"status of $connectorKey"
      )

      override def config(connectorKey: ConnectorKey)(
        implicit executionContext: ExecutionContext): Future[ConnectorConfig] = retry(
        () =>
          HttpExecutor.SINGLETON.get[ConnectorConfig, Error](
            s"http://$workerAddress/connectors/${connectorKey.connectorNameOnKafka()}/config"),
        s"config of $connectorKey"
      )

      override def taskStatus(connectorKey: ConnectorKey, id: Int)(
        implicit executionContext: ExecutionContext): Future[TaskStatus] =
        retry(
          () =>
            HttpExecutor.SINGLETON.get[TaskStatus, Error](
              s"http://$workerAddress/connectors/${connectorKey.connectorNameOnKafka()}/tasks/$id/status"),
          s"status of $connectorKey/$id"
        )
      override def pause(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Unit] =
        retry(() =>
                HttpExecutor.SINGLETON
                  .put[Error](s"http://$workerAddress/connectors/${connectorKey.connectorNameOnKafka()}/pause"),
              s"pause $connectorKey")

      override def resume(connectorKey: ConnectorKey)(implicit executionContext: ExecutionContext): Future[Unit] =
        retry(() =>
                HttpExecutor.SINGLETON
                  .put[Error](s"http://$workerAddress/connectors/${connectorKey.connectorNameOnKafka()}/resume"),
              s"resume $connectorKey")

      override def connectorValidator(): Validator =
        (executionContext, validation) => {
          implicit val exec: ExecutionContext = executionContext
          retry(
            () => {
              if (validation.topicNames().isEmpty)
                throw new IllegalArgumentException(
                  "I'm sorry for this error. However, please fill the topics" +
                    "for your validation request in order to test other settings. This prerequisite is introduced by kafka 2.x")
              HttpExecutor.SINGLETON
                .put[Validation, ConfigInfos, Error](
                  s"http://$workerAddress/connector-plugins/${validation.className()}/config/validate",
                  validation)
                .map(SettingInfo.of)
            },
            "connectorValidator"
          )
        }
    }
  }

  /**
    * This is a bridge between java and scala.
    * ConfigInfos is serialized to json by jackson so we can implement the RootJsonFormat easily.
    */
  private[this] implicit val CONFIG_INFOS_JSON_FORMAT: RootJsonFormat[ConfigInfos] = new RootJsonFormat[ConfigInfos] {
    import spray.json._
    override def write(obj: ConfigInfos): JsValue = KafkaJsonUtils.toString(obj).parseJson

    override def read(json: JsValue): ConfigInfos = KafkaJsonUtils.toConfigInfos(json.toString())
  }

  /**
    * a base class used to collect the setting from source/sink connector when creating
    */
  trait Creator extends com.island.ohara.common.pattern.Creator[Future[ConnectorCreationResponse]] {
    private[this] val connectorFormatter = ConnectorFormatter.of()
    private[this] var executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

    /**
      * set the connector key. It should be a unique key.
      *
      * @param connectorKey connector key
      * @return this one
      */
    def connectorKey(connectorKey: ConnectorKey): Creator = {
      connectorFormatter.connectorKey(connectorKey)
      this
    }

    /**
      * set the connector class. The class must be loaded in class loader otherwise it will fail to create the connector.
      *
      * @param className connector class
      * @return this one
      */
    def className(className: String): Creator = {
      connectorFormatter.className(className)
      this
    }

    /**
      * set the connector class. The class must be loaded in class loader otherwise it will fail to create the connector.
      *
      * @param clz connector class
      * @return this one
      */
    def connectorClass[T](clz: Class[T]): Creator = className(Objects.requireNonNull(clz).getName)

    /**
      * the max number from sink task you want to create
      *
      * @param numberOfTasks max number from sink task
      * @return this one
      */
    @Optional("default is 1")
    def numberOfTasks(numberOfTasks: Int): Creator = {
      connectorFormatter.numberOfTasks(numberOfTasks)
      this
    }

    @Optional("default is empty")
    def setting(key: String, value: String): Creator = {
      connectorFormatter.setting(key, value)
      this
    }

    /**
      * extra setting passed to sink connector. This setting is optional.
      *
      * @param settings setting
      * @return this one
      */
    @Optional("default is empty")
    def settings(settings: Map[String, String]): Creator = {
      connectorFormatter.settings(settings.asJava)
      this
    }

    /**
      * set the columns
      * @param columns columns
      * @return this builder
      */
    @Optional("default is all columns")
    def columns(columns: Seq[Column]): Creator = {
      connectorFormatter.columns(columns.asJava)
      this
    }

    def topicKey(topicKey: TopicKey): Creator = topicKeys((Set(topicKey)))

    def topicKeys(topicKeys: Set[TopicKey]): Creator = {
      connectorFormatter.topicKeys(topicKeys.asJava)
      this
    }

    /**
      * setting the key converter. By default there is no converter in ohara connector since it enable us to retrieve/send
      * data to connector through topic. If you wrap the data by connector, your producer/consumer have to unwrap
      * data in order to access data correctly.
      *
      * @return this one
      */
    @Optional("default key converter is ConverterType.NONE")
    def converterTypeOfKey(converterTypeOfKey: ConverterType): Creator = {
      connectorFormatter.converterTypeOfKey(converterTypeOfKey)
      this
    }

    /**
      * setting the value converter. By default there is no converter in ohara connector since it enable us to retrieve/send
      * data to connector through topic. If you wrap the data by connector, your producer/consumer have to unwrap
      * data in order to access data correctly.
      *
      * @return this one
      */
    @Optional("default key converter is ConverterType.NONE")
    def converterTypeOfValue(converterTypeOfValue: ConverterType): Creator = {
      connectorFormatter.converterTypeOfValue(converterTypeOfValue)
      this
    }

    /**
      * set the thread pool used to execute request
      * @param executionContext thread pool
      * @return this creator
      */
    @Optional("default pool is scala.concurrent.ExecutionContext.Implicits.global")
    def threadPool(executionContext: ExecutionContext): Creator = {
      this.executionContext = Objects.requireNonNull(executionContext)
      this
    }

    /**
      * send the request to create the sink connector.
      *
      * @return this one
      */
    override def create(): Future[ConnectorCreationResponse] =
      doCreate(
        executionContext = Objects.requireNonNull(executionContext),
        creation = connectorFormatter.requestOfCreation()
      )

    /**
      * send the request to kafka worker
      *
      * @return response
      */
    protected def doCreate(executionContext: ExecutionContext, creation: Creation): Future[ConnectorCreationResponse]
  }

  trait Validator {
    private[this] val formatter: ConnectorFormatter = ConnectorFormatter.of()

    def className(className: String): Validator = {
      this.formatter.className(CommonUtils.requireNonEmpty(className))
      this
    }

    def connectorClass(clz: Class[_]): Validator = className(clz.getName)

    @Optional("Default is none")
    def setting(key: String, value: String): Validator = settings(
      Map(CommonUtils.requireNonEmpty(key) -> CommonUtils.requireNonEmpty(value)))

    @Optional("Default is none")
    def settings(settings: Map[String, String]): Validator = {
      this.formatter.settings(CommonUtils.requireNonEmpty(settings.asJava))
      this
    }

    def topicKey(topicKey: TopicKey): Validator = topicKeys((Set(topicKey)))

    /**
      * set the topic in which you have interest.
      *
      * @param topicKeys topic keys
      * @return this one
      */
    @Optional("Default is none")
    def topicKeys(topicKeys: Set[TopicKey]): Validator = {
      formatter.topicKeys(topicKeys.asJava)
      this
    }

    /**
      * the max number from sink task you want to create
      *
      * @param numberOfTasks max number from sink task
      * @return this one
      */
    @Optional("Default is none")
    def numberOfTasks(numberOfTasks: Int): Validator = {
      this.formatter.numberOfTasks(CommonUtils.requirePositiveInt(numberOfTasks))
      this
    }

    /**
      * set the columns
      * @param columns columns
      * @return this builder
      */
    @Optional("Default is none")
    def columns(columns: Seq[Column]): Validator = {
      this.formatter.columns(CommonUtils.requireNonEmpty(columns.asJava))
      this
    }

    def connectorKey(connectorKey: ConnectorKey): Validator = {
      this.formatter.connectorKey(connectorKey)
      this
    }

    def workerClusterKey(clusterKey: ObjectKey): Validator = {
      this.formatter.workerClusterKey(clusterKey)
      this
    }

    def run()(implicit executionContext: ExecutionContext): Future[SettingInfo] = doValidate(
      executionContext = Objects.requireNonNull(executionContext),
      validation = formatter.requestOfValidation()
    )

    /**
      * send the request to kafka worker
      *
      * @return response
      */
    protected def doValidate(executionContext: ExecutionContext, validation: Validation): Future[SettingInfo]

  }
}
