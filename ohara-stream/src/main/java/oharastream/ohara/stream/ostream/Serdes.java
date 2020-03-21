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

import java.util.Map;
import oharastream.ohara.common.data.Row;

public class Serdes {

  public static Serde<String> STRING = StringSerde.get();
  public static final Serde<Row> ROW = RowSerde.get();
  public static Serde<Double> DOUBLE = DoubleSerde.get();
  public static final Serde<byte[]> BYTES = BytesSerde.get();

  protected static class WrapperSerde<T> implements Serde<T> {

    private final org.apache.kafka.common.serialization.Serializer<T> serializer;
    private final org.apache.kafka.common.serialization.Deserializer<T> deserializer;

    WrapperSerde(
        org.apache.kafka.common.serialization.Serializer<T> serializer,
        org.apache.kafka.common.serialization.Deserializer<T> deserializer) {
      this.serializer = serializer;
      this.deserializer = deserializer;
    }

    public void configure(Map<String, ?> configs, boolean isKey) {
      serializer.configure(configs, isKey);
      deserializer.configure(configs, isKey);
    }

    public void close() {
      serializer.close();
      deserializer.close();
    }

    public org.apache.kafka.common.serialization.Serializer<T> serializer() {
      return serializer;
    }

    public org.apache.kafka.common.serialization.Deserializer<T> deserializer() {
      return deserializer;
    }
  }

  public static final class StringSerde extends WrapperSerde<String> {
    public StringSerde() {
      super(new StringSerializer(), new StringDeserializer());
    }

    static Serde<String> get() {
      return new WrapperSerde<>(new StringSerializer(), new StringDeserializer());
    }
  }

  public static final class RowSerde extends WrapperSerde<Row> {
    public RowSerde() {
      super(new RowSerializer(), new RowDeserializer());
    }

    static Serde<Row> get() {
      return new WrapperSerde<>(new RowSerializer(), new RowDeserializer());
    }
  }

  public static final class DoubleSerde extends WrapperSerde<Double> {
    public DoubleSerde() {
      super(new DoubleSerializer(), new DoubleDeserializer());
    }

    static Serde<Double> get() {
      return new WrapperSerde<>(new DoubleSerializer(), new DoubleDeserializer());
    }
  }

  public static final class BytesSerde extends WrapperSerde<byte[]> {
    public BytesSerde() {
      super(new BytesSerializer(), new BytesDeserializer());
    }

    static Serde<byte[]> get() {
      return new WrapperSerde<>(new BytesSerializer(), new BytesDeserializer());
    }
  }
}
