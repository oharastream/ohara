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

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import oharastream.ohara.common.data.Cell;
import oharastream.ohara.common.data.Row;
import oharastream.ohara.common.data.Serializer;
import oharastream.ohara.common.setting.TopicKey;
import oharastream.ohara.common.util.CommonUtils;
import oharastream.ohara.kafka.Producer;
import oharastream.ohara.kafka.TopicAdmin;
import oharastream.ohara.stream.config.StreamDefUtils;
import oharastream.ohara.stream.examples.PageViewRegionExample;
import oharastream.ohara.testing.WithBroker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestPageViewRegionExample extends WithBroker {

  private final TopicAdmin client = TopicAdmin.of(testUtil().brokersConnProps());
  private final Producer<Row, byte[]> producer =
      Producer.builder()
          .connectionProps(client.connectionProps())
          .keySerializer(Serializer.ROW)
          .valueSerializer(Serializer.BYTES)
          .build();
  private final TopicKey fromTopic = TopicKey.of("default", "page-views");
  private final TopicKey toTopic = TopicKey.of("default", "view-by-region");
  private final String joinTableTopic = "user-profiles";

  // prepare data
  private final List<Row> views =
      java.util.stream.Stream.of(
              Row.of(Cell.of("user", "francesca"), Cell.of("page", "http://example.com/#bell")),
              Row.of(Cell.of("user", "eden"), Cell.of("page", "https://baseball.example.com/")),
              Row.of(Cell.of("user", "abbie"), Cell.of("page", "https://www.example.com/")),
              Row.of(
                  Cell.of("user", "aisha"),
                  Cell.of("page", "http://www.example.net/beginner/brother")),
              Row.of(Cell.of("user", "eden"), Cell.of("page", "http://www.example.net/")),
              Row.of(
                  Cell.of("user", "tommy"), Cell.of("page", "https://attack.example.org/amount")),
              Row.of(
                  Cell.of("user", "aisha"),
                  Cell.of(
                      "page", "http://www.example.org/afterthought.html?addition=base&angle=art")),
              Row.of(Cell.of("user", "elsa"), Cell.of("page", "https://belief.example.com/")),
              Row.of(
                  Cell.of("user", "abbie"),
                  Cell.of("page", "https://example.com/blade.php?berry=bike&action=boot#airplane")),
              Row.of(Cell.of("user", "elsa"), Cell.of("page", "http://example.com/")),
              Row.of(Cell.of("user", "eden"), Cell.of("page", "http://example.com/")),
              Row.of(Cell.of("user", "tommy"), Cell.of("page", "http://example.com/")),
              Row.of(Cell.of("user", "aisha"), Cell.of("page", "http://www.example.com/bead")),
              Row.of(Cell.of("user", "tommy"), Cell.of("page", "http://angle.example.com/")),
              Row.of(Cell.of("user", "tiffany"), Cell.of("page", "http://example.com/birds.html")),
              Row.of(
                  Cell.of("user", "abbie"),
                  Cell.of("page", "http://www.example.org/bubble/aunt.html")),
              Row.of(
                  Cell.of("user", "elsa"),
                  Cell.of("page", "https://example.com/?baseball=bat&birds=beef")),
              Row.of(
                  Cell.of("user", "tiffany"),
                  Cell.of(
                      "page", "http://amusement.example.com/?behavior=believe&brass=ball#basket")),
              Row.of(
                  Cell.of("user", "abbie"),
                  Cell.of(
                      "page",
                      "https://www.example.net/afternoon/balance.php?beef=blow&bee=advertisement")),
              Row.of(
                  Cell.of("user", "francesca"),
                  Cell.of("page", "http://www.example.com/?bike=ants&airplane=action")))
          .collect(Collectors.toList());

  private final List<Row> profiles =
      java.util.stream.Stream.of(
              Row.of(Cell.of("user", "abbie"), Cell.of("region", "Russian")),
              Row.of(Cell.of("user", "tommy"), Cell.of("region", "Jordan")),
              Row.of(Cell.of("user", "francesca"), Cell.of("region", "Belize")),
              Row.of(Cell.of("user", "eden"), Cell.of("region", "Russian")),
              Row.of(Cell.of("user", "tiffany"), Cell.of("region", "Jordan")),
              Row.of(Cell.of("user", "aisha"), Cell.of("region", "Russian")),
              Row.of(Cell.of("user", "elsa"), Cell.of("region", "Cuba")))
          .collect(Collectors.toList());

  private final Map<String, String> configs =
      ImmutableMap.of(
          StreamDefUtils.BROKER_DEFINITION.key(),
          client.connectionProps(),
          StreamDefUtils.NAME_DEFINITION.key(),
          CommonUtils.randomString(),
          StreamDefUtils.FROM_TOPIC_KEYS_DEFINITION.key(),
          "[" + TopicKey.toJsonString(fromTopic) + "]",
          StreamDefUtils.TO_TOPIC_KEYS_DEFINITION.key(),
          "[" + TopicKey.toJsonString(toTopic) + "]",
          PageViewRegionExample.joinTopicKey,
          joinTableTopic);

  @Before
  public void setup() {
    final int partitions = 1;
    final short replications = 1;
    StreamTestUtils.createTopic(client, fromTopic.topicNameOnKafka(), partitions, replications);
    StreamTestUtils.createTopic(client, joinTableTopic, partitions, replications);
    StreamTestUtils.createTopic(client, toTopic.topicNameOnKafka(), partitions, replications);
  }

  @Test
  public void testCase() throws InterruptedException {
    // run example
    PageViewRegionExample app = new PageViewRegionExample();
    Stream.execute(app.getClass(), configs);

    StreamTestUtils.produceData(producer, profiles, joinTableTopic);
    // the default commit.interval.ms=30 seconds, which should make sure join table ready
    TimeUnit.SECONDS.sleep(30);
    StreamTestUtils.produceData(producer, views, fromTopic.topicNameOnKafka());

    // Assert the result
    List<Row> expected =
        java.util.stream.Stream.of(
                Row.of(Cell.of("region", "Belize"), Cell.of("count", 2L)),
                Row.of(Cell.of("region", "Russian"), Cell.of("count", 10L)),
                Row.of(Cell.of("region", "Jordan"), Cell.of("count", 5L)),
                Row.of(Cell.of("region", "Cuba"), Cell.of("count", 3L)))
            .collect(Collectors.toList());
    StreamTestUtils.assertResult(client, toTopic.topicNameOnKafka(), expected, 20);
  }

  @After
  public void cleanUp() {
    producer.close();
    client.close();
  }
}
