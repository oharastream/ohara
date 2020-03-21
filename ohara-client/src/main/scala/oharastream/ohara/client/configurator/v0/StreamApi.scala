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

package oharastream.ohara.client.configurator.v0
import java.util.Objects

import oharastream.ohara.client.configurator.QueryRequest
import oharastream.ohara.client.configurator.v0.ClusterAccess.Query
import oharastream.ohara.client.configurator.v0.MetricsApi.Metrics
import oharastream.ohara.common.annotations.{Optional, VisibleForTesting}
import oharastream.ohara.common.setting.{ObjectKey, SettingDef, TopicKey}
import oharastream.ohara.common.util.CommonUtils
import oharastream.ohara.stream.config.StreamDefUtils
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
object StreamApi {
  val KIND: String               = "stream"
  val STREAM_PREFIX_PATH: String = "streams"

  /**
    * container name is controlled by streamRoute, the service name here use six words was ok.
    */
  val STREAM_SERVICE_NAME: String = STREAM_PREFIX_PATH

  def DEFINITIONS: Map[String, SettingDef] = StreamDefUtils.DEFAULT.asScala.toMap

  /**
    * Stream Docker Image name
    */
  final val IMAGE_NAME_DEFAULT: String = StreamDefUtils.IMAGE_NAME_DEFINITION.defaultString()

  final class Creation(val settings: Map[String, JsValue]) extends ClusterCreation {
    private[this] implicit def update(settings: Map[String, JsValue]): Updating = new Updating(noJsNull(settings))

    /**
      * Convert all json value to plain string. It keeps the json format but all stuff are in string.
      */
    def plain: Map[String, String] = noJsNull(settings).map {
      case (k, v) =>
        k -> (v match {
          case JsString(value) => value
          case _               => v.toString()
        })
    }

    def brokerClusterKey: ObjectKey = settings.brokerClusterKey.get

    def className: Option[String] = settings.className

    override def ports: Set[Int] = Set(jmxPort)

    def jarKey: ObjectKey = settings.jarKey.get

    private[ohara] def connectionProps: String = settings.connectionProps.get

    def fromTopicKeys: Set[TopicKey] = settings.fromTopicKeys.get
    def toTopicKeys: Set[TopicKey]   = settings.toTopicKeys.get
  }
  implicit val CREATION_JSON_FORMAT: OharaJsonFormat[Creation] =
    rulesOfCreation[Creation](
      new RootJsonFormat[Creation] {
        override def write(obj: Creation): JsValue = JsObject(noJsNull(obj.settings))
        override def read(json: JsValue): Creation = new Creation(json.asJsObject.fields)
      },
      DEFINITIONS.values.toSeq
    )

  final class Updating(val settings: Map[String, JsValue]) extends ClusterUpdating {
    def brokerClusterKey: Option[ObjectKey] =
      noJsNull(settings).get(StreamDefUtils.BROKER_CLUSTER_KEY_DEFINITION.key()).map(_.convertTo[ObjectKey])

    def className: Option[String] =
      noJsNull(settings).get(StreamDefUtils.CLASS_NAME_DEFINITION.key()).map(_.convertTo[String])

    def jarKey: Option[ObjectKey] =
      noJsNull(settings).get(StreamDefUtils.JAR_KEY_DEFINITION.key()).map(OBJECT_KEY_FORMAT.read)

    private[StreamApi] def connectionProps: Option[String] =
      noJsNull(settings).get(StreamDefUtils.BROKER_DEFINITION.key()).map(_.convertTo[String])

    def fromTopicKeys: Option[Set[TopicKey]] =
      noJsNull(settings).get(StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key()).map(_.convertTo[Set[TopicKey]])

    def toTopicKeys: Option[Set[TopicKey]] =
      noJsNull(settings).get(StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key()).map(_.convertTo[Set[TopicKey]])
  }
  implicit val UPDATING_JSON_FORMAT: OharaJsonFormat[Updating] =
    rulesOfUpdating[Updating](
      new RootJsonFormat[Updating] {
        override def write(obj: Updating): JsValue = JsObject(noJsNull(obj.settings))
        override def read(json: JsValue): Updating = new Updating(json.asJsObject.fields)
      }
    )

  /**
    * The Stream Cluster Information stored in configurator
    *
    * @param settings stream key-value pair settings
    * @param aliveNodes alive node list of the running containers from this cluster
    * @param state the state of stream (stopped stream does not have this field)
    * @param error the error message if the state was failed to fetch
    * @param metrics the metrics bean
    * @param lastModified this data change time
    */
  final case class StreamClusterInfo(
    settings: Map[String, JsValue],
    aliveNodes: Set[String],
    state: Option[String],
    error: Option[String],
    metrics: Metrics,
    lastModified: Long
  ) extends ClusterInfo {
    /**
      * reuse the parser from Creation.
      * @param settings settings
      * @return creation
      */
    private[this] implicit def creation(settings: Map[String, JsValue]): Creation = new Creation(noJsNull(settings))
    override def kind: String                                                     = KIND
    override def ports: Set[Int]                                                  = settings.ports
    def className: String                                                         = settings.className.get

    /**
      * Return the key of explicit value. Otherwise, return the key of jar info.
      * Normally, the key should be equal to jar info
      * @return key of jar
      */
    def jarKey: ObjectKey = settings.jarKey

    def brokerClusterKey: ObjectKey  = settings.brokerClusterKey
    def fromTopicKeys: Set[TopicKey] = settings.fromTopicKeys
    def toTopicKeys: Set[TopicKey]   = settings.toTopicKeys
    def connectionProps: String      = settings.connectionProps

    override protected def raw: Map[String, JsValue] = STREAM_CLUSTER_INFO_FORMAT.write(this).asJsObject.fields
  }

  private[ohara] implicit val STREAM_CLUSTER_INFO_FORMAT: OharaJsonFormat[StreamClusterInfo] =
    JsonRefiner[StreamClusterInfo]
      .format(new RootJsonFormat[StreamClusterInfo] {
        private[this] val format                            = jsonFormat6(StreamClusterInfo)
        override def read(json: JsValue): StreamClusterInfo = format.read(extractSetting(json.asJsObject))
        override def write(obj: StreamClusterInfo): JsValue = flattenSettings(format.write(obj).asJsObject)
      })
      .refine

  /**
    * used to generate the payload and url for POST/PUT request.
    * this request is extended by collie also so it is public than sealed.
    */
  trait Request extends ClusterRequest {
    @Optional("if you don't define the class, configurator will seek all files to find available one")
    def className(className: String): Request.this.type =
      setting(StreamDefUtils.CLASS_NAME_DEFINITION.key(), JsString(className))
    def jarKey(jarKey: ObjectKey): Request.this.type =
      setting(StreamDefUtils.JAR_KEY_DEFINITION.key(), ObjectKey.toJsonString(jarKey).parseJson)
    def fromTopicKey(fromTopicKey: TopicKey): Request.this.type = fromTopicKeys(Set(fromTopicKey))
    def fromTopicKeys(fromTopicKeys: Set[TopicKey]): Request.this.type =
      setting(
        StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key(),
        JsArray(fromTopicKeys.map(TOPIC_KEY_FORMAT.write).toVector)
      )
    def toTopicKey(toTopicKey: TopicKey): Request.this.type = toTopicKeys(Set(toTopicKey))
    def toTopicKeys(toTopicKeys: Set[TopicKey]): Request.this.type =
      setting(StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key(), JsArray(toTopicKeys.map(TOPIC_KEY_FORMAT.write).toVector))
    @Optional("server picks up a broker cluster for you if broker cluster name is empty")
    def brokerClusterKey(brokerClusterKey: ObjectKey): Request.this.type =
      setting(
        StreamDefUtils.BROKER_CLUSTER_KEY_DEFINITION.key(),
        OBJECT_KEY_FORMAT.write(Objects.requireNonNull(brokerClusterKey))
      )
    @Optional("the default port is random")
    def jmxPort(jmxPort: Int): Request.this.type =
      setting(StreamDefUtils.JMX_PORT_DEFINITION.key(), JsNumber(CommonUtils.requireConnectionPort(jmxPort)))

    @Optional("default value is empty array in creation and None in update")
    def tags(tags: Map[String, JsValue]): Request.this.type =
      setting(StreamDefUtils.TAGS_DEFINITION.key(), JsObject(tags))

    @Optional("default connection props is generated by broker cluster name")
    def connectionProps(connectionProps: String): Request.this.type =
      setting(StreamDefUtils.BROKER_DEFINITION.key(), JsString(connectionProps))

    /**
      * stream app accept empty nodes, and the basic request reject the empty array.
      * Overriding this setter avoids the exception.
      */
    override def nodeNames(nodeNames: Set[String]): Request.this.type =
      setting(StreamDefUtils.NODE_NAMES_DEFINITION.key(), JsArray(nodeNames.map(JsString(_)).toVector))

    /**
      * Creation instance includes many useful parsers for custom settings so we open it to code with a view to reusing
      * those convenient parsers.
      * @return the payload of creation
      */
    final def creation: Creation =
      // auto-complete the creation via our refiner
      CREATION_JSON_FORMAT.read(CREATION_JSON_FORMAT.write(new Creation(noJsNull(settings.toMap))))

    /**
      * for testing only
      * @return the payload of update
      */
    @VisibleForTesting
    private[v0] final def updating: Updating =
      // auto-complete the update via our refiner
      UPDATING_JSON_FORMAT.read(UPDATING_JSON_FORMAT.write(new Updating(noJsNull(settings.toMap))))
  }

  /**
    * similar to Request but it has execution methods.
    *
    */
  sealed trait ExecutableRequest extends Request {
    def create()(implicit executionContext: ExecutionContext): Future[StreamClusterInfo]
    def update()(implicit executionContext: ExecutionContext): Future[StreamClusterInfo]
  }

  final class Access private[StreamApi]
      extends ClusterAccess[Creation, Updating, StreamClusterInfo](STREAM_PREFIX_PATH) {
    override def query: Query[StreamClusterInfo] = new Query[StreamClusterInfo] {
      override protected def doExecute(request: QueryRequest)(
        implicit executionContext: ExecutionContext
      ): Future[Seq[StreamClusterInfo]] = list(request)
    }

    def request: ExecutableRequest = new ExecutableRequest {
      override def create()(implicit executionContext: ExecutionContext): Future[StreamClusterInfo] = post(creation)

      override def update()(implicit executionContext: ExecutionContext): Future[StreamClusterInfo] =
        put(key, updating)
    }
  }

  def access: Access = new Access
}
