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

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
import oharastream.ohara.stream.OStream;
import oharastream.ohara.stream.Stream;
import oharastream.ohara.stream.config.StreamDefUtils;
import oharastream.ohara.stream.config.StreamSetting;
import oharastream.ohara.testing.With3Brokers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"rawtypes"})
public class TestPurchaseAnalysis extends With3Brokers {
  private static final Logger LOG = LoggerFactory.getLogger(TestPurchaseAnalysis.class);
  private static final String appid = "test-purchase-analysis";
  private static final TopicKey resultTopic =
      TopicKey.of(CommonUtils.randomString(), "gender-amount");
  private static final TopicKey itemTopic = TopicKey.of(CommonUtils.randomString(), "items");
  private static final TopicKey orderTopic = TopicKey.of(CommonUtils.randomString(), "orders");
  private static final TopicKey userTopic = TopicKey.of(CommonUtils.randomString(), "users");
  private final TopicAdmin client = TopicAdmin.of(testUtil().brokersConnProps());
  private final Producer<Row, byte[]> producer =
      Producer.<Row, byte[]>builder()
          .connectionProps(client.connectionProps())
          .keySerializer(Serializer.ROW)
          .valueSerializer(Serializer.BYTES)
          .build();

  @Before
  public void setup() {
    int partitions = 3;
    short replications = 1;
    try {
      client
          .topicCreator()
          .numberOfPartitions(partitions)
          .numberOfReplications(replications)
          .topicName(orderTopic.topicNameOnKafka())
          .create();
      client
          .topicCreator()
          .numberOfPartitions(partitions)
          .numberOfReplications(replications)
          .topicName(itemTopic.topicNameOnKafka())
          .create();
      client
          .topicCreator()
          .numberOfPartitions(partitions)
          .numberOfReplications(replications)
          .topicName(userTopic.topicNameOnKafka())
          .create();
      client
          .topicCreator()
          .numberOfPartitions(partitions)
          .numberOfReplications(replications)
          .topicName(resultTopic.topicNameOnKafka())
          .create();
    } catch (Exception e) {
      LOG.error(e.getMessage());
    }
  }

  @Test
  public void testStream() throws InterruptedException {
    // write items.csv to kafka broker
    produceData("items.csv", itemTopic.topicNameOnKafka());

    // write users.csv to kafka broker
    produceData("users.csv", userTopic.topicNameOnKafka());

    // we make sure the join topic has data already
    assertResult(client, itemTopic.topicNameOnKafka(), 4);
    assertResult(client, userTopic.topicNameOnKafka(), 4);
    TimeUnit.SECONDS.sleep(1);
    // write orders.csv to kafka broker
    produceData("orders.csv", orderTopic.topicNameOnKafka());
    assertResult(client, orderTopic.topicNameOnKafka(), 16);

    RunStream app = new RunStream();
    Stream.execute(
        app.getClass(),
        java.util.stream.Stream.of(
                Pair.of(StreamDefUtils.NAME_DEFINITION.key(), appid),
                Pair.of(StreamDefUtils.BROKER_DEFINITION.key(), client.connectionProps()),
                Pair.of(
                    StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key(),
                    TopicKey.toJsonString(Collections.singletonList(orderTopic))),
                Pair.of(
                    StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key(),
                    TopicKey.toJsonString(Collections.singletonList(resultTopic))))
            .collect(Collectors.toMap(Pair::left, Pair::right)));
  }

  @After
  public void cleanUp() {
    producer.close();
    client.close();
  }

  /** Stream Main Entry */
  public static class RunStream extends Stream {

    @Override
    public void start(OStream<Row> stream, StreamSetting streamSetting) {
      // We initial a new OStream object to test functionality
      OStream<Row> ostream =
          OStream.builder()
              .appId(streamSetting.name())
              .bootstrapServers(streamSetting.brokerConnectionProps())
              .fromTopic(
                  streamSetting.fromTopicKeys().stream()
                      .map(TopicKey::topicNameOnKafka)
                      .findFirst()
                      .orElse(null))
              .toTopic(
                  streamSetting.toTopicKeys().stream()
                      .map(TopicKey::topicNameOnKafka)
                      .findFirst()
                      .orElse(null))
              .cleanStart()
              .timestampExtractor(MyExtractor.class)
              .enableExactlyOnce()
              .build();

      ostream
          .leftJoin(
              userTopic.topicNameOnKafka(),
              Conditions.create().add(Collections.singletonList(Pair.of("userName", "name"))),
              (row1, row2) ->
                  Row.of(
                      row1.cell("userName"),
                      row1.cell("itemName"),
                      row1.cell("quantity"),
                      row2 == null ? Cell.of("address", "") : row2.cell("address"),
                      row2 == null ? Cell.of("gender", "") : row2.cell("gender")))
          .filter(row -> row.cell("address").value() != null)
          .leftJoin(
              itemTopic.topicNameOnKafka(),
              Conditions.create().add(Collections.singletonList(Pair.of("itemName", "itemName"))),
              (row1, row2) ->
                  Row.of(
                      row1.cell("userName"),
                      row1.cell("itemName"),
                      row1.cell("quantity"),
                      Cell.of("useraddress", row1.cell("address").value()),
                      row1.cell("gender"),
                      row2 == null
                          ? Cell.of("itemaddress", "")
                          : Cell.of("itemaddress", row2.cell("address").value()),
                      row2 == null ? Cell.of("type", "") : row2.cell("type"),
                      row2 == null ? Cell.of("price", "") : row2.cell("price")))
          .filter(
              row ->
                  row.cell("useraddress")
                      .value()
                      .toString()
                      .equals(row.cell("itemaddress").value().toString()))
          .map(
              row ->
                  Row.of(
                      row.cell("gender"),
                      Cell.of(
                          "amount",
                          Double.parseDouble(row.cell("quantity").value().toString())
                              * Double.parseDouble(row.cell("price").value().toString()))))
          .groupByKey(Collections.singletonList("gender"))
          .reduce((Double r1, Double r2) -> r1 + r2, "amount")
          .start();

      Consumer<Row, byte[]> consumer =
          Consumer.<Row, byte[]>builder()
              .topicName(resultTopic.topicNameOnKafka())
              .connectionProps(streamSetting.brokerConnectionProps())
              .groupId("group-" + resultTopic.topicNameOnKafka())
              .offsetFromBegin()
              .keySerializer(Serializer.ROW)
              .valueSerializer(Serializer.BYTES)
              .build();

      List<Consumer.Record<Row, byte[]>> records = consumer.poll(Duration.ofSeconds(30), 4);
      records.forEach(
          row ->
              LOG.debug(
                  "final result : " + (row.key().isPresent() ? row.key().get().toString() : null)));
      Assert.assertEquals(
          "the result will get \"accumulation\" ; hence we will get 4 records.", 4, records.size());

      Map<String, Double[]> actualResultMap = new HashMap<>();
      actualResultMap.put("male", new Double[] {9000D, 60000D, 69000D});
      actualResultMap.put("female", new Double[] {15000D, 30000D, 45000D});
      final double THRESHOLD = 0.0001;

      records.forEach(
          record -> {
            if (record.key().isPresent()) {
              Optional<Double> amount =
                  record.key().get().cells().stream()
                      .filter(cell -> cell.name().equals("amount"))
                      .map(cell -> Double.valueOf(cell.value().toString()))
                      .findFirst();
              Assert.assertTrue(
                  "the result should be contain in actualResultMap",
                  actualResultMap.containsKey(record.key().get().cell("gender").value().toString())
                      && actualResultMap.values().stream()
                          .flatMap(Arrays::stream)
                          .anyMatch(d -> Math.abs(d - amount.orElse(-999.0)) < THRESHOLD));
            }
          });

      consumer.close();
      ostream.stop();
    }
  }

  public static class MyExtractor implements TimestampExtractor {

    @Override
    public long extract(
        org.apache.kafka.clients.consumer.ConsumerRecord<Object, Object> record,
        long previousTimestamp) {
      LOG.debug(
          String.format(
              "timeExtract : topic[%s], value[%s], partition[%s], time[%s]",
              record.topic(), record.key().toString(), record.partition(), record.timestamp()));
      Object value = record.key();
      if (value instanceof Row) {
        Row row = (Row) value;
        // orders
        if (row.names().contains("transactionDate"))
          return LocalDateTime.of(2019, 2, 2, 2, 2, 2).toEpochSecond(ZoneOffset.UTC) * 1000;
        // items
        else if (row.names().contains("price"))
          return LocalDateTime.of(2019, 1, 1, 1, 1, 1).toEpochSecond(ZoneOffset.UTC) * 1000;
        // users
        else if (row.names().contains("gender"))
          return LocalDateTime.of(2019, 1, 1, 1, 1, 1).toEpochSecond(ZoneOffset.UTC) * 1000;
        // other
        else
          throw new RuntimeException(
              "the headers of this row are not expected :" + String.join(",", row.names()));
      } else {
        throw new RuntimeException("who are you? :" + value.getClass().getName());
      }
    }
  }

  private void produceData(String filename, String topicName) {
    try {
      List<?> dataList = DataUtils.readData(filename);
      dataList.stream()
          .map(
              object -> {
                try {
                  List<Cell> cells = new ArrayList<>();
                  LOG.debug("Class Name : " + object.getClass().getName());
                  for (Field f : object.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    Cell cell = Cell.of(f.getName(), f.get(object));
                    cells.add(cell);
                    LOG.debug("--" + f.getName() + ":" + f.get(object));
                  }
                  return new AbstractMap.SimpleEntry<>(
                      Row.of(cells.toArray(new Cell[0])), new byte[0]);
                } catch (Exception e) {
                  LOG.debug(e.getMessage());
                  return new AbstractMap.SimpleEntry<>(Row.EMPTY, new byte[0]);
                }
              })
          .forEach(
              entry ->
                  producer
                      .sender()
                      .key(entry.getKey())
                      .value(entry.getValue())
                      .topicName(topicName)
                      .send());
    } catch (Exception e) {
      LOG.debug(e.getMessage());
    }
  }

  private void assertResult(TopicAdmin client, String topic, int expectedSize) {
    Consumer<Row, byte[]> consumer =
        Consumer.<Row, byte[]>builder()
            .topicName(topic)
            .connectionProps(client.connectionProps())
            .groupId("group-" + CommonUtils.randomString(5))
            .offsetFromBegin()
            .keySerializer(Serializer.ROW)
            .valueSerializer(Serializer.BYTES)
            .build();

    List<Consumer.Record<Row, byte[]>> records =
        consumer.poll(Duration.ofSeconds(30), expectedSize);
    Assert.assertEquals(expectedSize, records.size());
    consumer.close();
  }
}
