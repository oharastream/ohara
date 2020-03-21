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

package oharastream.ohara.stream;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import oharastream.ohara.common.data.Row;
import oharastream.ohara.common.exception.ExceptionHandler;
import oharastream.ohara.common.exception.OharaException;
import oharastream.ohara.common.setting.SettingDef;
import oharastream.ohara.common.setting.TopicKey;
import oharastream.ohara.common.setting.WithDefinitions;
import oharastream.ohara.common.util.CommonUtils;
import oharastream.ohara.stream.config.StreamDefUtils;
import oharastream.ohara.stream.config.StreamSetting;

@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class Stream implements WithDefinitions {
  // Exception handler
  private static ExceptionHandler handler =
      ExceptionHandler.builder()
          .with(IOException.class, OharaException::new)
          .with(MalformedURLException.class, OharaException::new)
          .with(ClassNotFoundException.class, OharaException::new)
          .build();

  /**
   * Running a standalone stream. This method is usually called from the main method. It must not be
   * called more than once otherwise exception will be thrown.
   *
   * @param clz the stream class that is constructed and extends from {@link Stream}
   * @param configs the raw configs passed from command-line arguments
   */
  public static void execute(Class<? extends Stream> clz, Map<String, String> configs) {
    ExceptionHandler.DEFAULT.handle(
        () -> {
          Constructor<? extends Stream> cons = clz.getConstructor();
          final Stream theApp = cons.newInstance();
          StreamSetting streamSetting =
              StreamSetting.of(theApp.settingDefinitions().values(), configs);

          OStream<Row> ostream =
              OStream.builder()
                  .appId(streamSetting.name())
                  .bootstrapServers(streamSetting.brokerConnectionProps())
                  // TODO: Currently, the number of from topics must be 1
                  // https://github.com/oharastream/ohara/issues/688
                  .fromTopic(
                      streamSetting.fromTopicKeys().stream()
                          .map(TopicKey::topicNameOnKafka)
                          .findFirst()
                          .orElse(null))
                  // TODO: Currently, the number of to topics must be 1
                  // https://github.com/oharastream/ohara/issues/688
                  .toTopic(
                      streamSetting.toTopicKeys().stream()
                          .map(TopicKey::topicNameOnKafka)
                          .findFirst()
                          .orElse(null))
                  .build();
          theApp.init();
          theApp.start(ostream, streamSetting);
          return null;
        });
  }

  /**
   * find main entry of jar in ohara environment container
   *
   * @param lines arguments
   * @throws OharaException exception
   */
  public static void main(String[] lines) throws OharaException {
    Map<String, String> args = CommonUtils.parse(Arrays.asList(lines));
    String className = args.get(StreamDefUtils.CLASS_NAME_DEFINITION.key());
    if (CommonUtils.isEmpty(className))
      throw new RuntimeException(
          "Where is the value of " + StreamDefUtils.CLASS_NAME_DEFINITION.key());
    Class clz = handler.handle(() -> Class.forName(className));
    if (Stream.class.isAssignableFrom(clz)) execute(clz, args);
    else
      throw new RuntimeException(
          "Error: " + clz + " is not a subclass of " + Stream.class.getName());
  }

  // ------------------------[public APIs]------------------------//

  /** Constructor */
  public Stream() {}

  /**
   * Use to define settings for stream usage. Default will load the required configurations only.
   *
   * @return the defined settings
   */
  protected Map<String, SettingDef> customSettingDefinitions() {
    return Collections.emptyMap();
  }

  @Override
  public final Map<String, SettingDef> settingDefinitions() {
    return WithDefinitions.merge(this, StreamDefUtils.DEFAULT, customSettingDefinitions());
  }

  /** User defined initialization before running stream */
  public void init() {}

  /**
   * Entry function. <b>Usage:</b>
   *
   * <pre>
   *   ostream
   *    .filter()
   *    .map()
   *    ...
   * </pre>
   *
   * @param ostream the entry object to define logic
   * @param streamSetting configuration object
   */
  public abstract void start(OStream<Row> ostream, StreamSetting streamSetting);
}
