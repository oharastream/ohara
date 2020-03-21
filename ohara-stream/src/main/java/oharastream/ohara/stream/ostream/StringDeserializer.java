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

package oharastream.ohara.stream.ostream;

import java.io.UnsupportedEncodingException;
import java.util.Map;

// Kafka use it's own serializer to initial Serdes object, we need to implement that
public class StringDeserializer
    implements org.apache.kafka.common.serialization.Deserializer<String> {

  private String encoding = "UTF8";

  @Override
  public void configure(Map<String, ?> configs, boolean isKey) {
    String propertyName = isKey ? "key.serializer.encoding" : "value.serializer.encoding";
    Object encodingValue = configs.get(propertyName);
    if (encodingValue == null) encodingValue = configs.get("serializer.encoding");
    if (encodingValue instanceof String) encoding = (String) encodingValue;
  }

  @Override
  public String deserialize(String topic, byte[] data) {
    try {
      if (data == null) return null;
      else return new String(data, encoding);
    } catch (UnsupportedEncodingException e) {
      throw new UnsupportedOperationException(
          "Error when deSerializing byte[] to string due to unsupported encoding " + encoding);
    }
  }

  @Override
  public void close() {}
}
