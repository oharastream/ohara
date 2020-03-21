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

package oharastream.ohara.stream.examples;

import java.util.Collections;
import java.util.Map;
import oharastream.ohara.common.data.Cell;
import oharastream.ohara.common.data.Pair;
import oharastream.ohara.common.data.Row;
import oharastream.ohara.common.setting.SettingDef;
import oharastream.ohara.stream.OStream;
import oharastream.ohara.stream.Stream;
import oharastream.ohara.stream.config.StreamSetting;
import oharastream.ohara.stream.ostream.Conditions;

/**
 * A simple example to illustrate how to do a join between an {@code OStream} and {@code OTable}. We
 * join a data of page views with the input topic named "page-views" which has following format :
 *
 * <table border='1'>
 *   <th>user</th><th>page</th>
 *   <tr><td>francesca</td><td>http://example.com/#bell</td></tr>
 *   <tr><td>eden</td><td>https://baseball.example.com/</td></tr>
 *   <tr><td>abbie</td><td>https://www.example.com/</td></tr>
 *   <tr><td>aisha</td><td>http://www.example.net/beginner/brother</td></tr>
 *   <tr><td>eden</td><td>http://www.example.net/</td></tr>
 *   <tr><td>tommy</td><td>https://attack.example.org/amount</td></tr>
 *   <tr><td>aisha</td><td>http://www.example.org/afterthought.html?addition=base&angle=art</td></tr>
 *   <tr><td>elsa</td><td>https://belief.example.com/</td></tr>
 *   <tr><td>abbie</td><td>https://example.com/blade.php?berry=bike&action=boot#airplane</td></tr>
 *   <tr><td>elsa</td><td>http://example.com/</td></tr>
 *   <tr><td>eden</td><td>http://example.com/</td></tr>
 *   <tr><td>tommy</td><td>http://example.com/</td></tr>
 *   <tr><td>aisha</td><td>http://www.example.com/bead</td></tr>
 *   <tr><td>tommy</td><td>http://angle.example.com/</td></tr>
 *   <tr><td>tiffany</td><td>http://example.com/birds.html</td></tr>
 *   <tr><td>abbie</td><td>http://www.example.org/bubble/aunt.html</td></tr>
 *   <tr><td>elsa</td><td>https://example.com/?baseball=bat&birds=beef</td></tr>
 *   <tr><td>tiffany</td><td>http://amusement.example.com/?behavior=believe&brass=ball#basket</td></tr>
 *   <tr><td>abbie</td><td>https://www.example.net/afternoon/balance.php?beef=blow&bee=advertisement</td></tr>
 *   <tr><td>francesca</td><td>http://www.example.com/?bike=ants&airplane=action</td></tr>
 * </table>
 *
 * <br>
 * And the user profile data topic named "user-profiles" which has following format :
 *
 * <table border='1'>
 *   <th>user</th><th>region</th>
 *   <tr><td>abbie</td><td>Russian</td></tr>
 *   <tr><td>tommy</td><td>Jordan</td></tr>
 *   <tr><td>francesca</td><td>Belize</td></tr>
 *   <tr><td>eden</td><td>Russian</td></tr>
 *   <tr><td>tiffany</td><td>Jordan</td></tr>
 *   <tr><td>aisha</td><td>Russian</td></tr>
 *   <tr><td>elsa</td><td>Cuba</td></tr>
 * </table>
 *
 * <br>
 * We want to calculate the click counts by region and write data to the output topic named
 * "view-by-region" which has following format:
 *
 * <table border='1'>
 *   <th>region</th><th>count</th>
 *   <tr><td>Belize</td><td>2</td></tr>
 *   <tr><td>Russian</td><td>10</td></tr>
 *   <tr><td>Jordan</td><td>5</td></tr>
 *   <tr><td>Cuba</td><td>3</td></tr>
 * </table>
 *
 * <p>Note: the result is a streaming data ; the count should be accumulated
 *
 * <p>How to run this example?
 *
 * <p>1. Package this example with your favorite build tool (maven, gradle...)
 *
 * <p>2. Upload the packaged jar to Ohara Stream Manager, Add a stream into pipeline
 *
 * <p>3. Crete two topic : <b>page-views</b> and <b>user-profiles</b> topic from Ohara Stream
 * Manager
 *
 * <p>4. Use connector to write data into "page-views" and "user-profiles" with the above format
 *
 * <p>5. Start the stream. The result data will be wrote to the output topic "view-by-region" with
 * the above format
 */
public class PageViewRegionExample extends Stream {
  public static final String joinTopicKey = "joinTopicKey";

  @Override
  protected Map<String, SettingDef> customSettingDefinitions() {
    return Collections.singletonMap(
        joinTopicKey, SettingDef.builder().key(joinTopicKey).group("default").build());
  }

  @Override
  public void start(OStream<Row> ostream, StreamSetting streamSetting) {
    ostream
        .leftJoin(
            streamSetting
                .string(joinTopicKey)
                .orElseThrow(() -> new RuntimeException("joinTopicKey not found")),
            Conditions.create().add(Collections.singletonList(Pair.of("user", "user"))),
            (r1, r2) ->
                Row.of(
                    r1.cell("user"),
                    r1.cell("page"),
                    // since we do "left join", the right hand table may be null after join
                    r2 == null ? Cell.of("region", "") : r2.cell("region")))
        .groupByKey(Collections.singletonList("region"))
        .count()
        .start();
  }
}
