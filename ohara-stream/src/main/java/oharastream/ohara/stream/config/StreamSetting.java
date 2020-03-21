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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import oharastream.ohara.common.annotations.VisibleForTesting;
import oharastream.ohara.common.setting.SettingDef;
import oharastream.ohara.common.setting.TopicKey;

/**
 * The entry class for define stream definitions
 *
 * <p>The data we keep in this class is use the format : List&lt;SettingDef&gt;
 */
public final class StreamSetting {

  public static StreamSetting of(
      Collection<SettingDef> settingDefinitions, Map<String, String> raw) {
    return new StreamSetting(settingDefinitions, raw);
  }

  private final Collection<SettingDef> settingDefinitions;
  private final Map<String, String> raw;

  private StreamSetting(Collection<SettingDef> settingDefinitions, Map<String, String> raw) {
    this.settingDefinitions = Collections.unmodifiableCollection(settingDefinitions);
    this.raw = Collections.unmodifiableMap(raw);
  }

  /**
   * Get all {@code Config.name} from this class.
   *
   * @return config name list
   */
  public List<String> keys() {
    return settingDefinitions.stream().map(SettingDef::key).collect(Collectors.toList());
  }

  /**
   * Get all {@code SettingDef} from this class.
   *
   * @return config object list
   */
  public Collection<SettingDef> settingDefinitions() {
    return settingDefinitions;
  }

  /**
   * Get value from specific name. Note: This is a helper method for container environment.
   *
   * @param key config key
   * @return value from container environment, {@code Optional.empty()} if absent
   */
  public Optional<String> string(String key) {
    return Optional.ofNullable(raw.get(key)).map(StreamSetting::fromEnvString);
  }

  /** @return the name of this stream */
  public String name() {
    return string(StreamDefUtils.NAME_DEFINITION.key())
        .orElseThrow(() -> new RuntimeException("NAME_DEFINITION not found in env."));
  }

  /** @return brokers' connection props */
  public String brokerConnectionProps() {
    return string(StreamDefUtils.BROKER_DEFINITION.key())
        .orElseThrow(
            () ->
                new RuntimeException(
                    StreamDefUtils.BROKER_DEFINITION.key() + " not found in env."));
  }

  /** @return the keys of from topics */
  public List<TopicKey> fromTopicKeys() {
    return TopicKey.toTopicKeys(
        string(StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key())
            .orElseThrow(
                () -> new RuntimeException("FROM_TOPIC_KEYS_DEFINITION not found in env.")));
  }

  /** @return the keys of to topics */
  public List<TopicKey> toTopicKeys() {
    return TopicKey.toTopicKeys(
        string(StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key())
            .orElseThrow(() -> new RuntimeException("TO_TOPIC_KEYS_DEFINITION not found in env.")));
  }

  // ---------------------[command-line tools]---------------------//

  /** this is a specific string used to replace the quota in the env. */
  @VisibleForTesting static String INTERNAL_STRING_FOR_ENV = "_____";

  /**
   * remove the unsupported charset - quote - and replace it by slash
   *
   * @param string string
   * @return a string is accepted by env
   */
  public static String toEnvString(String string) {
    if (string.contains(INTERNAL_STRING_FOR_ENV))
      throw new IllegalArgumentException(
          string
              + " has internal string:"
              + INTERNAL_STRING_FOR_ENV
              + " so we can't convert it to env string");
    return string.replaceAll("\"", INTERNAL_STRING_FOR_ENV);
  }

  /**
   * replace the cryptic and internal charset from the env string.
   *
   * @param string string from env
   * @return a absolutely normal string
   */
  public static String fromEnvString(String string) {
    return string.replaceAll(INTERNAL_STRING_FOR_ENV, "\"");
  }
}
