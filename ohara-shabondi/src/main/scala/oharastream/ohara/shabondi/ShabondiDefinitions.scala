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

package oharastream.ohara.shabondi

import java.time.{Duration => JDuration}
import java.util.concurrent.atomic.AtomicInteger

import oharastream.ohara.common.setting.SettingDef.Type
import oharastream.ohara.common.setting.{SettingDef, WithDefinitions}
import oharastream.ohara.common.util.VersionUtils

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object ShabondiDefinitions {
  private[this] val basicDefinitionMap  = mutable.Map.empty[String, SettingDef]
  private[this] val sourceDefinitionMap = mutable.Map.empty[String, SettingDef]
  private[this] val sinkDefinitionMap   = mutable.Map.empty[String, SettingDef]
  private[this] val orderCounter        = new AtomicInteger(0)
  private[this] def orderInGroup(): Int = orderCounter.getAndIncrement

  val CORE_GROUP                        = "core"
  val IMAGE_NAME_DEFAULT: String        = s"oharastream/shabondi:${VersionUtils.VERSION}"
  def basicDefinitions: Seq[SettingDef] = basicDefinitionMap.values.toList
  def sourceDefinitions: Seq[SettingDef] =
    WithDefinitions
      .merge(classOf[ShabondiSource], basicDefinitionMap.asJava, sourceDefinitionMap.asJava)
      .asScala
      .values
      .toSeq
  def sinkDefinitions: Seq[SettingDef] =
    WithDefinitions
      .merge(classOf[ShabondiSink], basicDefinitionMap.asJava, sinkDefinitionMap.asJava)
      .asScala
      .values
      .toSeq

  val GROUP_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("group")
    .orderInGroup(orderInGroup())
    .displayName("Shabondi group")
    .documentation("The unique group of this Shabondi")
    .optional("default")
    .permission(SettingDef.Permission.CREATE_ONLY)
    .build
    .registerTo(basicDefinitionMap)

  val NAME_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("name")
    .orderInGroup(orderInGroup())
    .displayName("Shabondi name")
    .documentation("The unique name of this Shabondi")
    .stringWithRandomDefault
    .permission(SettingDef.Permission.CREATE_ONLY)
    .build
    .registerTo(basicDefinitionMap)

  val IMAGE_NAME_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("imageName")
    .orderInGroup(orderInGroup())
    .displayName("Image name")
    .documentation("The image name of this Shabondi running with")
    .optional(IMAGE_NAME_DEFAULT)
    // Currently, in manager, user cannot change the image name after shabondi creation
    .permission(SettingDef.Permission.CREATE_ONLY)
    .build
    .registerTo(basicDefinitionMap)

  val SHABONDI_CLASS_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("shabondi.class")
    .orderInGroup(orderInGroup())
    .required(Set(classOf[ShabondiSource].getName, classOf[ShabondiSink].getName).asJava)
    .documentation("the class name of Shabondi service")
    .permission(SettingDef.Permission.CREATE_ONLY)
    .build
    .registerTo(basicDefinitionMap)

  val CLIENT_PORT_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("shabondi.client.port")
    .orderInGroup(orderInGroup())
    .required(Type.BINDING_PORT)
    .displayName("The port used to expose Shabondi service")
    .build
    .registerTo(basicDefinitionMap)

  val ENDPOINT_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("shabondi.endpoint")
    .orderInGroup(orderInGroup())
    .optional(Type.STRING)
    .displayName("Endpoint")
    .documentation("The endpoint of this Shabondi service. After the service is started, the endpoint will be shown.")
    .permission(SettingDef.Permission.READ_ONLY)
    .build
    .registerTo(basicDefinitionMap)

  val JMX_PORT_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("jmxPort")
    .orderInGroup(orderInGroup())
    .bindingPortWithRandomDefault()
    .displayName("JMX export port")
    .documentation("The port of this Shabondi service using to export jmx metrics")
    .build
    .registerTo(basicDefinitionMap)

  val BROKER_CLUSTER_KEY_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("brokerClusterKey")
    .orderInGroup(orderInGroup())
    .required(Type.OBJECT_KEY)
    .reference(SettingDef.Reference.BROKER)
    .displayName("Broker cluster key")
    .documentation("the key of broker cluster used to transfer data for this Shabondi")
    .build
    .registerTo(basicDefinitionMap)

  val BROKERS_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("shabondi.brokers")
    .orderInGroup(orderInGroup())
    .required(Type.STRING)
    .displayName("Broker list")
    .documentation("The broker list of current workspace")
    .internal
    .build
    .registerTo(basicDefinitionMap)

  val NODE_NAMES_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("nodeNames")
    .orderInGroup(orderInGroup())
    .denyList(Set("stop", "start", "pause", "resume").asJava)
    .optional(Type.ARRAY)
    .reference(SettingDef.Reference.NODE)
    .displayName("Node name list")
    .documentation("The node which Shabondi deployed. Only one node supported currently.")
    .build
    .registerTo(basicDefinitionMap)

  val ROUTES_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("routes")
    .orderInGroup(orderInGroup())
    .optional(Type.TAGS)
    .displayName("Routes")
    .documentation("the extra routes to this service")
    .build
    .registerTo(basicDefinitionMap)

  val TAGS_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("tags")
    .orderInGroup(orderInGroup())
    .optional(Type.TAGS)
    .displayName("Tags")
    .documentation("Tags of Shabondi")
    .build
    .registerTo(basicDefinitionMap)

  val MAX_HEAP_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("xmx")
    .orderInGroup(orderInGroup())
    .positiveNumber(1024L)
    .documentation("maximum memory allocation (in MB)")
    .build
    .registerTo(basicDefinitionMap)

  val INIT_HEAP_DEFINITION = SettingDef.builder
    .group(CORE_GROUP)
    .key("xms")
    .orderInGroup(orderInGroup())
    .positiveNumber(1024L)
    .documentation("initial heap size (in MB)")
    .build
    .registerTo(basicDefinitionMap)

  val VERSION_DEFINITION: SettingDef = WithDefinitions
    .versionDefinition(VersionUtils.VERSION)
    .registerTo(basicDefinitionMap)

  val REVISION_DEFINITION: SettingDef = WithDefinitions
    .revisionDefinition(VersionUtils.REVISION)
    .registerTo(basicDefinitionMap)

  val AUTHOR_DEFINITION: SettingDef = WithDefinitions
    .authorDefinition(VersionUtils.USER)
    .registerTo(basicDefinitionMap)

  implicit private class RegisterSettingDef(settingDef: SettingDef) {
    def registerTo(groups: mutable.Map[String, SettingDef]*): SettingDef = {
      groups.foreach { group =>
        group += (settingDef.key -> settingDef)
      }
      settingDef
    }
  }

  //-------------- Definitions of Shabondi Source -----------------

  val SOURCE_TO_TOPICS_DEFINITION = SettingDef.builder
    .key("shabondi.source.toTopics")
    .group(CORE_GROUP)
    .orderInGroup(orderInGroup())
    .reference(SettingDef.Reference.TOPIC)
    .displayName("Target topic")
    .documentation("The topic that Shabondi(source) will push rows into")
    .optional(Type.OBJECT_KEYS)
    .build
    .registerTo(sourceDefinitionMap)

  //-------------- Definitions of Shabondi Sink -----------------

  val SINK_FROM_TOPICS_DEFINITION = SettingDef.builder
    .key("shabondi.sink.fromTopics")
    .group(CORE_GROUP)
    .orderInGroup(orderInGroup())
    .reference(SettingDef.Reference.TOPIC)
    .displayName("Source topic")
    .documentation("The topic that Shabondi(sink) will pull rows from")
    .optional(Type.OBJECT_KEYS)
    .build
    .registerTo(sinkDefinitionMap)

  val SINK_POLL_TIMEOUT_DEFINITION = SettingDef.builder
    .key("shabondi.sink.poll.timeout")
    .group(CORE_GROUP)
    .orderInGroup(orderInGroup())
    .optional(JDuration.ofMillis(1500))
    .displayName("Poll timeout")
    .documentation("The timeout value(milliseconds) that each poll from topic")
    .build
    .registerTo(sinkDefinitionMap)

  val SINK_GROUP_IDLETIME = SettingDef.builder
    .key("shabondi.sink.group.idletime")
    .group(CORE_GROUP)
    .orderInGroup(orderInGroup())
    .optional(JDuration.ofMinutes(3))
    .displayName("Data group idle time")
    .documentation("The resource will be released automatically if the data group is not used more than idle time.")
    .build
    .registerTo(sinkDefinitionMap)
}
