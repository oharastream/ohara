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

package com.island.ohara.client.configurator.v0
import java.util.Objects
import java.util.concurrent.atomic.AtomicInteger

import com.island.ohara.client.Enum
import com.island.ohara.client.configurator.Data
import com.island.ohara.client.kafka.TopicAdmin.PartitionInfo
import com.island.ohara.common.annotations.Optional
import com.island.ohara.common.setting.SettingDef.{Reference, Type}
import com.island.ohara.common.setting.{ObjectKey, SettingDef, TopicKey}
import com.island.ohara.common.util.CommonUtils
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.record.Records
import spray.json.DefaultJsonProtocol._
import spray.json.{JsNumber, JsObject, JsString, JsValue, RootJsonFormat}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

object TopicApi {
  private[v0] val DEFAULT_NUMBER_OF_PARTITIONS: Int = 1
  private[v0] val DEFAULT_NUMBER_OF_REPLICATIONS: Short = 1
  private[ohara] val TOPICS_PREFIX_PATH: String = "topics"

  private[v0] val NUMBER_OF_PARTITIONS_KEY = "numberOfPartitions"
  private[v0] val NUMBER_OF_REPLICATIONS_KEY = "numberOfReplications"
  private[v0] val BROKER_CLUSTER_KEY_KEY = "brokerClusterKey"
  private[this] val TAGS_KEY = "tags"

  /**
    * the config with this group is mapped to kafka's custom config. Kafka divide configs into two parts.
    * 1) required configs (number of partitions and number of replications)
    * 2) custom configs (those config must be able to convert to string)
    *
    * Furthermore, kafka forbids us to put required configs to custom configs. Hence, we have to mark the custom config
    * in order to filter the custom from settings (see Creation).
    */
  private[this] val GROUP_TO_EXTRA_CONFIG = "extra"

  /**
    * list the custom configs of topic. It is useful to developers who long for controlling the topic totally.
    */
  val TOPIC_DEFINITIONS: Seq[SettingDef] = {
    val coreGroup = "core"
    val count = new AtomicInteger(0)
    def toDefWithDefault(key: String, group: String, doc: String, valueType: Type, default: Any): SettingDef =
      toDef(key, group, doc, valueType, Some(default))
    def toDefWithoutDefault(key: String, group: String, doc: String, valueType: Type): SettingDef =
      toDef(key, group, doc, valueType, None)
    def toDef(key: String, group: String, doc: String, valueType: Type, default: Option[Any]): SettingDef = {
      val builder = SettingDef
        .builder()
        .key(key)
        .displayName(key)
        .documentation(doc)
        .group(group)
        .orderInGroup(count.getAndIncrement())
        .valueType(valueType)
      if (default.isDefined) builder.optional(default.get.toString) else builder.optional()
      builder.build()
    }
    Seq(
      //-----------[kafka prerequisite]-----------
      toDefWithDefault(key = GROUP_KEY,
                       group = coreGroup,
                       doc = "the group of this topic",
                       valueType = Type.STRING,
                       default = GROUP_DEFAULT),
      toDefWithoutDefault(key = NAME_KEY, group = coreGroup, doc = "the name of this topic", valueType = Type.STRING),
      toDefWithDefault(key = NUMBER_OF_PARTITIONS_KEY,
                       group = coreGroup,
                       doc = "the number of partitions",
                       valueType = Type.INT,
                       default = DEFAULT_NUMBER_OF_PARTITIONS),
      toDefWithDefault(key = NUMBER_OF_REPLICATIONS_KEY,
                       group = coreGroup,
                       doc = "the number of replications",
                       valueType = Type.SHORT,
                       default = NUMBER_OF_REPLICATIONS_KEY),
      SettingDef
        .builder()
        .key(BROKER_CLUSTER_KEY_KEY)
        .displayName(BROKER_CLUSTER_KEY_KEY)
        .documentation("the broker cluster to deploy this topic")
        .group(coreGroup)
        .orderInGroup(count.getAndIncrement())
        .valueType(Type.OBJECT_KEY)
        .reference(Reference.BROKER_CLUSTER)
        .build(),
      toDefWithoutDefault(key = TAGS_KEY, group = coreGroup, doc = "the tags to this topic", valueType = Type.TAGS),
      //-----------[kafka custom]-----------
      toDefWithDefault(key = TopicConfig.SEGMENT_BYTES_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.SEGMENT_BYTES_DOC,
                       valueType = Type.LONG,
                       default = 1 * 1024 * 1024 * 1024),
      toDefWithDefault(key = TopicConfig.SEGMENT_MS_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.SEGMENT_MS_DOC,
                       valueType = Type.LONG,
                       default = 24 * 7 * 60 * 60 * 1000L),
      toDefWithDefault(key = TopicConfig.SEGMENT_JITTER_MS_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.SEGMENT_JITTER_MS_DOC,
                       valueType = Type.LONG,
                       default = 0 * 60 * 60 * 1000L),
      toDefWithDefault(
        key = TopicConfig.SEGMENT_INDEX_BYTES_CONFIG,
        group = GROUP_TO_EXTRA_CONFIG,
        doc = TopicConfig.SEGMENT_INDEX_BYTES_DOC,
        valueType = Type.LONG,
        default = 10 * 1024 * 1024
      ),
      toDefWithDefault(
        key = TopicConfig.FLUSH_MESSAGES_INTERVAL_CONFIG,
        group = GROUP_TO_EXTRA_CONFIG,
        doc = TopicConfig.FLUSH_MESSAGES_INTERVAL_DOC,
        valueType = Type.LONG,
        default = Long.MaxValue
      ),
      toDefWithDefault(TopicConfig.FLUSH_MS_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.FLUSH_MS_DOC,
                       valueType = Type.LONG,
                       default = Long.MaxValue),
      toDefWithDefault(key = TopicConfig.RETENTION_BYTES_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.RETENTION_BYTES_DOC,
                       valueType = Type.LONG,
                       default = -1L),
      toDefWithDefault(key = TopicConfig.RETENTION_MS_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.RETENTION_MS_DOC,
                       valueType = Type.LONG,
                       default = 24 * 7 * 60 * 60 * 1000L),
      toDefWithDefault(
        key = TopicConfig.MAX_MESSAGE_BYTES_CONFIG,
        group = GROUP_TO_EXTRA_CONFIG,
        doc = TopicConfig.MAX_MESSAGE_BYTES_DOC,
        valueType = Type.LONG,
        default = 1000000 + Records.LOG_OVERHEAD
      ),
      toDefWithDefault(key = TopicConfig.INDEX_INTERVAL_BYTES_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.INDEX_INTERVAL_BYTES_DOCS,
                       valueType = Type.INT,
                       4096),
      toDefWithDefault(TopicConfig.FILE_DELETE_DELAY_MS_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.FILE_DELETE_DELAY_MS_DOC,
                       valueType = Type.INT,
                       default = 60000),
      toDefWithDefault(
        key = TopicConfig.DELETE_RETENTION_MS_CONFIG,
        group = GROUP_TO_EXTRA_CONFIG,
        doc = TopicConfig.DELETE_RETENTION_MS_DOC,
        valueType = Type.LONG,
        default = 24 * 60 * 60 * 1000L
      ),
      toDefWithDefault(key = TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.MIN_COMPACTION_LAG_MS_DOC,
                       valueType = Type.LONG,
                       default = 0L),
      toDefWithDefault(
        key = TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG,
        group = GROUP_TO_EXTRA_CONFIG,
        doc = TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_DOC,
        valueType = Type.DOUBLE,
        default = 0.5d
      ),
      toDefWithDefault(key = TopicConfig.CLEANUP_POLICY_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.CLEANUP_POLICY_DOC,
                       valueType = Type.STRING,
                       default = "delete"),
      toDefWithDefault(
        key = TopicConfig.UNCLEAN_LEADER_ELECTION_ENABLE_CONFIG,
        group = GROUP_TO_EXTRA_CONFIG,
        doc = TopicConfig.UNCLEAN_LEADER_ELECTION_ENABLE_DOC,
        valueType = Type.BOOLEAN,
        default = false
      ),
      toDefWithDefault(key = TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.MIN_IN_SYNC_REPLICAS_DOC,
                       valueType = Type.INT,
                       default = 1),
      toDefWithDefault(TopicConfig.COMPRESSION_TYPE_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.COMPRESSION_TYPE_DOC,
                       valueType = Type.STRING,
                       default = "producer"),
      toDefWithDefault(key = TopicConfig.PREALLOCATE_CONFIG,
                       group = GROUP_TO_EXTRA_CONFIG,
                       doc = TopicConfig.PREALLOCATE_DOC,
                       valueType = Type.BOOLEAN,
                       default = false),
      // this config impacts the available APIs so we don't expose it.
      //      toDefWithDefault(TopicConfig.MESSAGE_FORMAT_VERSION_CONFIG, TopicConfig.MESSAGE_FORMAT_VERSION_DOC, ApiVersion.latestVersion.toString),
      toDefWithDefault(
        key = TopicConfig.MESSAGE_TIMESTAMP_TYPE_CONFIG,
        group = GROUP_TO_EXTRA_CONFIG,
        doc = TopicConfig.MESSAGE_TIMESTAMP_TYPE_DOC,
        valueType = Type.STRING,
        default = "CreateTime"
      ),
      toDefWithDefault(
        key = TopicConfig.MESSAGE_TIMESTAMP_DIFFERENCE_MAX_MS_CONFIG,
        group = GROUP_TO_EXTRA_CONFIG,
        doc = TopicConfig.MESSAGE_TIMESTAMP_DIFFERENCE_MAX_MS_DOC,
        valueType = Type.LONG,
        default = Long.MaxValue
      ),
      toDefWithDefault(
        key = TopicConfig.MESSAGE_DOWNCONVERSION_ENABLE_CONFIG,
        group = GROUP_TO_EXTRA_CONFIG,
        doc = TopicConfig.MESSAGE_DOWNCONVERSION_ENABLE_DOC,
        valueType = Type.BOOLEAN,
        default = true
      )
    )
  }

  /**
    * only the custom definitions.
    */
  val TOPIC_CUSTOM_DEFINITIONS: Seq[SettingDef] = TOPIC_DEFINITIONS.filter(_.group() == GROUP_TO_EXTRA_CONFIG)

  final class Updating private[TopicApi] (val settings: Map[String, JsValue]) {
    def brokerClusterKey: Option[ObjectKey] = noJsNull(settings).get(BROKER_CLUSTER_KEY_KEY).map(_.convertTo[ObjectKey])
    private[TopicApi] def numberOfPartitions: Option[Int] =
      noJsNull(settings).get(NUMBER_OF_PARTITIONS_KEY).map(_.convertTo[Int])

    private[TopicApi] def numberOfReplications: Option[Short] =
      noJsNull(settings).get(NUMBER_OF_REPLICATIONS_KEY).map(_.convertTo[Short])

    private[TopicApi] def group: Option[String] = noJsNull(settings).get(GROUP_KEY).map(_.convertTo[String])

    private[TopicApi] def name: Option[String] = noJsNull(settings).get(NAME_KEY).map(_.convertTo[String])

    private[TopicApi] def tags: Option[Map[String, JsValue]] = noJsNull(settings).get(TAGS_KEY).map(_.asJsObject.fields)
  }

  implicit val TOPIC_UPDATING_FORMAT: RootJsonFormat[Updating] =
    JsonRefiner[Updating]
      .format(new RootJsonFormat[Updating] {
        override def read(json: JsValue): Updating = new Updating(noJsNull(json.asJsObject.fields))
        override def write(obj: Updating): JsValue = JsObject(obj.settings)
      })
      .rejectEmptyString()
      .requirePositiveNumber(NUMBER_OF_PARTITIONS_KEY)
      .requirePositiveNumber(NUMBER_OF_REPLICATIONS_KEY)
      .refine

  final class Creation private[TopicApi] (val settings: Map[String, JsValue])
      extends com.island.ohara.client.configurator.v0.BasicCreation {

    private[this] implicit def update(settings: Map[String, JsValue]): Updating = new Updating(noJsNull(settings))

    def key: TopicKey = TopicKey.of(group, name)

    def brokerClusterKey: Option[ObjectKey] = settings.brokerClusterKey

    def numberOfPartitions: Int = settings.numberOfPartitions.get
    def numberOfReplications: Short = settings.numberOfReplications.get

    override def group: String = settings.group.get

    override def name: String = settings.name.get

    override def tags: Map[String, JsValue] = settings.tags.get
  }

  implicit val TOPIC_CREATION_FORMAT: OharaJsonFormat[Creation] =
    // this object is open to user define the (group, name) in UI, we need to handle the key rules
    basicRulesOfKey[Creation]
      .format(new RootJsonFormat[Creation] {
        override def read(json: JsValue): Creation = new Creation(noJsNull(json.asJsObject.fields))
        override def write(obj: Creation): JsValue = JsObject(obj.settings)
      })
      .nullToInt(NUMBER_OF_PARTITIONS_KEY, DEFAULT_NUMBER_OF_PARTITIONS)
      .nullToInt(NUMBER_OF_REPLICATIONS_KEY, DEFAULT_NUMBER_OF_REPLICATIONS)
      .rejectEmptyString()
      .nullToEmptyObject(TAGS_KEY)
      .requirePositiveNumber(NUMBER_OF_PARTITIONS_KEY)
      .requirePositiveNumber(NUMBER_OF_REPLICATIONS_KEY)
      .refine

  import MetricsApi._

  abstract sealed class TopicState(val name: String) extends Serializable
  object TopicState extends Enum[TopicState] {
    case object RUNNING extends TopicState("RUNNING")
  }

  implicit val TOPIC_STATE_FORMAT: RootJsonFormat[TopicState] = new RootJsonFormat[TopicState] {
    override def read(json: JsValue): TopicState = TopicState.forName(json.convertTo[String].toUpperCase)
    override def write(obj: TopicState): JsValue = JsString(obj.name)
  }

  implicit val TOPIC_PARTITION_FORMAT: RootJsonFormat[PartitionInfo] = jsonFormat6(PartitionInfo)

  case class TopicInfo(settings: Map[String, JsValue],
                       partitionInfos: Seq[PartitionInfo],
                       metrics: Metrics,
                       state: Option[TopicState],
                       lastModified: Long)
      extends Data {

    private[this] implicit def creation(settings: Map[String, JsValue]): Creation = new Creation(settings)

    override def key: TopicKey = TopicKey.of(group, name)
    override def kind: String = "topic"

    /**
      * kafka topic does not support to group topic so we salt the group with name.
      * @return topic name for kafka
      */
    def topicNameOnKafka: String = key.topicNameOnKafka

    override def group: String = settings.group

    override def name: String = settings.name

    override def tags: Map[String, JsValue] = settings.tags

    def brokerClusterKey: ObjectKey = settings.brokerClusterKey.get
    def numberOfPartitions: Int = settings.numberOfPartitions

    def numberOfReplications: Short = settings.numberOfReplications

    /**
      * @return the custom configs. the core configs are not included
      */
    def configs: Map[String, String] = noJsNull(settings)
      .filter {
        case (key, value) =>
          TOPIC_DEFINITIONS.filter(_.group() == GROUP_TO_EXTRA_CONFIG).exists(_.key() == key) &&
            value.isInstanceOf[JsString]
      }
      .map {
        case (key, value) => key -> value.convertTo[String]
      }

  }

  implicit val TOPIC_INFO_FORMAT: RootJsonFormat[TopicInfo] = new RootJsonFormat[TopicInfo] {
    private[this] val format = jsonFormat5(TopicInfo)
    override def read(json: JsValue): TopicInfo = format.read(json)

    override def write(obj: TopicInfo): JsValue = JsObject(
      format.write(obj).asJsObject.fields ++
        // TODO: remove this stale fields ... by chia
        Map(
          GROUP_KEY -> JsString(obj.group),
          NAME_KEY -> JsString(obj.name),
          NUMBER_OF_PARTITIONS_KEY -> JsNumber(obj.numberOfPartitions),
          NUMBER_OF_REPLICATIONS_KEY -> JsNumber(obj.numberOfReplications),
          TAGS_KEY -> JsObject(obj.tags)
        ))
  }

  /**
    * used to generate the payload and url for POST/PUT request.
    */
  trait Request {
    private[this] val settings: mutable.Map[String, JsValue] = mutable.Map()

    /**
      * set the group and name via key
      * @param topicKey topic key
      * @return this request
      */
    def key(topicKey: TopicKey): Request = {
      group(topicKey.group())
      name(topicKey.name())
    }

    @Optional("default group is \"default\"")
    def group(group: String): Request = setting(GROUP_KEY, JsString(CommonUtils.requireNonEmpty(group)))

    @Optional("default name is a random string. But it is required in updating")
    def name(name: String): Request =
      setting(NAME_KEY, JsString(CommonUtils.requireNonEmpty(name)))

    @Optional("server will match a broker cluster for you if the bk name is ignored")
    def brokerClusterKey(brokerClusterKey: ObjectKey): Request =
      setting(BROKER_CLUSTER_KEY_KEY, OBJECT_KEY_FORMAT.write(brokerClusterKey))

    @Optional("default value is DEFAULT_NUMBER_OF_PARTITIONS")
    def numberOfPartitions(numberOfPartitions: Int): Request =
      setting(NUMBER_OF_PARTITIONS_KEY, JsNumber(CommonUtils.requirePositiveInt(numberOfPartitions)))

    @Optional("default value is DEFAULT_NUMBER_OF_REPLICATIONS")
    def numberOfReplications(numberOfReplications: Short): Request =
      setting(NUMBER_OF_REPLICATIONS_KEY, JsNumber(CommonUtils.requirePositiveShort(numberOfReplications)))

    @Optional("default configs is empty array")
    def configs(configs: Map[String, String]): Request = {
      configs.foreach {
        case (key, value) => setting(key, JsString(value))
      }
      this
    }

    @Optional("default value is empty array")
    def tags(tags: Map[String, JsValue]): Request =
      setting(TAGS_KEY, JsObject(Objects.requireNonNull(tags)))

    def setting(key: String, value: JsValue): Request = settings(Map(key -> value))

    def settings(settings: Map[String, JsValue]): Request = {
      this.settings ++= settings
      this
    }

    /**
      * Creation instance includes many useful parsers for custom settings so we open it to code with a view to reusing
      * those convenient parsers.
      * @return the payload of creation
      */
    final def creation: Creation =
      // rewrite the creation via format since the format will auto-complete the creation
      // this make the creaion is consistent to creation sent to server
      TOPIC_CREATION_FORMAT.read(TOPIC_CREATION_FORMAT.write(new Creation(noJsNull(settings.toMap))))

    private[v0] final def updating: Updating =
      // rewrite the update via format since the format will auto-complete the creation
      // this make the update is consistent to creation sent to server
      TOPIC_UPDATING_FORMAT.read(TOPIC_UPDATING_FORMAT.write(new Updating(noJsNull(settings.toMap))))

    /**
      * generate the POST request
      * @param executionContext thread pool
      * @return created data
      */
    def create()(implicit executionContext: ExecutionContext): Future[TopicInfo]

    /**
      * generate the PUT request
      * @param executionContext thread pool
      * @return updated/created data
      */
    def update()(implicit executionContext: ExecutionContext): Future[TopicInfo]
  }

  class Access private[v0] extends com.island.ohara.client.configurator.v0.Access[TopicInfo](TOPICS_PREFIX_PATH) {
    def start(key: TopicKey)(implicit executionContext: ExecutionContext): Future[Unit] = put(key, START_COMMAND)
    def stop(key: TopicKey)(implicit executionContext: ExecutionContext): Future[Unit] = put(key, STOP_COMMAND)
    def request: Request = new Request {

      override def create()(implicit executionContext: ExecutionContext): Future[TopicInfo] =
        exec.post[Creation, TopicInfo, ErrorApi.Error](
          url,
          creation
        )
      override def update()(implicit executionContext: ExecutionContext): Future[TopicInfo] =
        exec.put[Updating, TopicInfo, ErrorApi.Error](
          url(TopicKey.of(updating.group.getOrElse(GROUP_DEFAULT), updating.name.get)),
          updating
        )
    }
  }

  def access: Access = new Access
}
