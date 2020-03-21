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

package oharastream.ohara.stream.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import oharastream.ohara.common.setting.SettingDef;
import oharastream.ohara.common.setting.SettingDef.Type;
import oharastream.ohara.common.util.VersionUtils;

/**
 * This is an helper class for getting / setting {@link oharastream.ohara.common.setting.SettingDef}
 * for Stream.
 */
public final class StreamDefUtils {

  private static final String CORE_GROUP = "core";

  /** This is the default configurations we will load into {@code StreamDefUtils}. */
  private static final AtomicInteger ORDER_COUNTER = new AtomicInteger(0);

  public static final SettingDef BROKER_CLUSTER_KEY_DEFINITION =
      SettingDef.builder()
          .key("brokerClusterKey")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Broker cluster key")
          .documentation("the key of broker cluster used to transfer data for this stream")
          .required(Type.OBJECT_KEY)
          .reference(SettingDef.Reference.BROKER_CLUSTER)
          .build();

  public static final SettingDef BROKER_DEFINITION =
      SettingDef.builder()
          .key("servers")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Broker list")
          .documentation("The broker list of current workspace")
          .required(Type.STRING)
          .internal()
          .build();

  public static final SettingDef IMAGE_NAME_DEFINITION =
      SettingDef.builder()
          .key("imageName")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Image name")
          .documentation("The image name of this stream running with")
          .optional("oharastream/stream:" + VersionUtils.VERSION)
          // In manager, user cannot change the image name
          .permission(SettingDef.Permission.READ_ONLY)
          .build();

  public static final SettingDef NAME_DEFINITION =
      SettingDef.builder()
          .key("name")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Stream name")
          .documentation("The unique name of this stream")
          .stringWithRandomDefault()
          .permission(SettingDef.Permission.CREATE_ONLY)
          .build();

  public static final SettingDef GROUP_DEFINITION =
      SettingDef.builder()
          .key("group")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Stream group")
          .documentation("The unique group of this stream")
          .optional("default")
          .permission(SettingDef.Permission.CREATE_ONLY)
          .build();

  public static final SettingDef JAR_KEY_DEFINITION =
      SettingDef.builder()
          .key("jarKey")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Jar primary key")
          .documentation("The jar key of this stream using")
          .required(Type.OBJECT_KEY)
          .reference(SettingDef.Reference.FILE)
          // this core setting is controlled by UI flow so we don't expose it
          .internal()
          .build();

  public static final SettingDef CLASS_NAME_DEFINITION =
      SettingDef.builder()
          .key("className")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("the stream class you want to run")
          .documentation(
              "the stream class running in all stream nodes. If you don't define it, Configurator will seek all jar files to find the available one.")
          .optional(Type.CLASS)
          .permission(SettingDef.Permission.CREATE_ONLY)
          .build();

  public static final SettingDef FROM_TOPIC_KEYS_DEFINITION =
      SettingDef.builder()
          .key("from")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .reference(SettingDef.Reference.TOPIC)
          .displayName("From topic of data consuming from")
          .documentation("The topic name of this stream should consume from")
          // we have to make this field optional since our UI needs to create stream without
          // topics...
          .optional(Type.OBJECT_KEYS)
          .build();

  public static final SettingDef TO_TOPIC_KEYS_DEFINITION =
      SettingDef.builder()
          .key("to")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .reference(SettingDef.Reference.TOPIC)
          .displayName("To topic of data produce to")
          .documentation("The topic name of this stream should produce to")
          // we have to make this field optional since our UI needs to create stream without
          // topics...
          .optional(Type.OBJECT_KEYS)
          .build();

  public static final SettingDef JMX_PORT_DEFINITION =
      SettingDef.builder()
          .key("jmxPort")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("JMX export port")
          .documentation("The port of this stream using to export jmx metrics")
          .bindingPortWithRandomDefault()
          .build();

  public static final SettingDef NODE_NAMES_DEFINITION =
      SettingDef.builder()
          .key("nodeNames")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Node name list")
          .documentation("The used node name list of this stream")
          .blacklist(new HashSet<>(Arrays.asList("stop", "start", "pause", "resume")))
          // This "optional" is for our UI since it does not require users to define the node names
          // when creating pipeline. Noted that our services (zk, bk and wk) still require user to
          // define the node names in creating.
          .optional(Type.ARRAY)
          .build();

  public static final SettingDef ROUTES_DEFINITION =
      SettingDef.builder()
          // similar to oharastream.ohara.client.configurator.ROUTES_KEY
          .key("routes")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Routes")
          .documentation("the extra routes to this service")
          .optional(Type.TAGS)
          .build();

  public static final SettingDef TAGS_DEFINITION =
      SettingDef.builder()
          .key("tags")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .displayName("Tags")
          .documentation("Tags of stream")
          .optional(Type.TAGS)
          .build();

  public static final SettingDef MAX_HEAP_DEFINITION =
      SettingDef.builder()
          .key("xmx")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .documentation("maximum memory allocation (in MB)")
          .optional(1024L)
          .build();

  public static final SettingDef INIT_HEAP_DEFINITION =
      SettingDef.builder()
          .key("xms")
          .group(CORE_GROUP)
          .orderInGroup(ORDER_COUNTER.getAndIncrement())
          .documentation("initial heap size (in MB)")
          .optional(1024L)
          .build();

  // this is the stream metric group definition
  public static final String STREAM_METRICS_GROUP_DEFAULT = "stream";

  /**
   * Load configDefs from default definitions.
   *
   * <p>This field is associated to a immutable map.
   */
  public static final Map<String, SettingDef> DEFAULT =
      Arrays.stream(StreamDefUtils.class.getDeclaredFields())
          .filter(field -> field.getType().isAssignableFrom(SettingDef.class))
          .map(
              field -> {
                try {
                  return (SettingDef) field.get(new StreamDefUtils());
                } catch (IllegalAccessException e) {
                  throw new IllegalArgumentException("field is not able cast to SettingDef", e);
                }
              })
          .collect(Collectors.toMap(SettingDef::key, Function.identity()));

  // disable constructor
  private StreamDefUtils() {}
}
