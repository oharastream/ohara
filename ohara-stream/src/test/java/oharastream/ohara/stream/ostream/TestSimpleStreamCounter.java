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

import java.time.Duration;
import java.util.Collections;
import java.util.stream.Collectors;
import oharastream.ohara.common.data.Cell;
import oharastream.ohara.common.data.Pair;
import oharastream.ohara.common.data.Row;
import oharastream.ohara.common.data.Serializer;
import oharastream.ohara.common.setting.TopicKey;
import oharastream.ohara.common.util.CommonUtils;
import oharastream.ohara.kafka.Consumer;
import oharastream.ohara.kafka.Producer;
import oharastream.ohara.kafka.TopicAdmin;
import oharastream.ohara.metrics.BeanChannel;
import oharastream.ohara.stream.OStream;
import oharastream.ohara.stream.Stream;
import oharastream.ohara.stream.config.StreamDefUtils;
import oharastream.ohara.stream.config.StreamSetting;
import oharastream.ohara.stream.metric.MetricFactory;
import oharastream.ohara.testing.WithBroker;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

// TODO: the stream requires many arguments from env variables.
// This tests do not care the rules required by stream.
// Fortunately (or unfortunately), stream lacks of enough checks to variables so the
// non-completed settings to stream works well in this test ... by chia
public class TestSimpleStreamCounter extends WithBroker {

  private static Duration timeout = Duration.ofSeconds(10);
  private static TopicKey fromKey = TopicKey.of(CommonUtils.randomString(), "metric-from");
  private static TopicKey toKey = TopicKey.of(CommonUtils.randomString(), "metric-to");

  private final TopicAdmin client = TopicAdmin.of(testUtil().brokersConnProps());
  private final Producer<Row, byte[]> producer =
      Producer.<Row, byte[]>builder()
          .connectionProps(client.connectionProps())
          .keySerializer(Serializer.ROW)
          .valueSerializer(Serializer.BYTES)
          .build();
  private final Consumer<Row, byte[]> consumer =
      Consumer.<Row, byte[]>builder()
          .topicName(toKey.topicNameOnKafka())
          .connectionProps(client.connectionProps())
          .keySerializer(Serializer.ROW)
          .valueSerializer(Serializer.BYTES)
          .build();

  @Before
  public void setup() {
    int partitions = 1;
    short replications = 1;
    client
        .topicCreator()
        .numberOfPartitions(partitions)
        .numberOfReplications(replications)
        .topicName(fromKey.topicNameOnKafka())
        .create();

    try {
      producer
          .sender()
          .key(Row.of(Cell.of("bar", "foo")))
          .value(new byte[0])
          .topicName(fromKey.topicNameOnKafka())
          .send()
          .get();
      producer
          .sender()
          .key(Row.of(Cell.of("hello", "world")))
          .value(new byte[0])
          .topicName(fromKey.topicNameOnKafka())
          .send()
          .get();
    } catch (Exception e) {
      Assert.fail();
    }
  }

  @Test
  public void testMetrics() {
    DirectWriteStream app = new DirectWriteStream();
    Stream.execute(
        app.getClass(),
        java.util.stream.Stream.of(
                Pair.of(StreamDefUtils.NAME_DEFINITION.key(), "metric-test"),
                Pair.of(StreamDefUtils.BROKER_DEFINITION.key(), client.connectionProps()),
                Pair.of(
                    StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key(),
                    TopicKey.toJsonString(Collections.singletonList(fromKey))),
                Pair.of(
                    StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key(),
                    TopicKey.toJsonString(Collections.singletonList(toKey))))
            .collect(Collectors.toMap(Pair::left, Pair::right)));

    // wait until topic has data
    CommonUtils.await(() -> consumer.poll(timeout).size() > 0, Duration.ofSeconds(30));

    // there should be two counter bean (in_topic, to_topic)
    Assert.assertEquals(2, BeanChannel.local().counterMBeans().size());

    BeanChannel.local()
        .counterMBeans()
        .forEach(
            bean -> {
              if (bean.name().equals(MetricFactory.IOType.TOPIC_IN.name()))
                // input counter bean should have exactly two record size
                Assert.assertEquals(2, Math.toIntExact(bean.getValue()));
              else
                // output counter bean should have exactly one record size (after filter)
                Assert.assertEquals(1, Math.toIntExact(bean.getValue()));
            });
  }

  public static class DirectWriteStream extends Stream {

    @Override
    public void start(OStream<Row> ostream, StreamSetting streamSetting) {

      ostream.filter(row -> row.names().contains("bar")).map(row -> row).start();
    }
  }
}
