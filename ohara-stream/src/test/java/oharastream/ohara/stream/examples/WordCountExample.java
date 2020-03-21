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
import oharastream.ohara.common.data.Row;
import oharastream.ohara.stream.OStream;
import oharastream.ohara.stream.Stream;
import oharastream.ohara.stream.config.StreamSetting;

/**
 * A simple example to illustrate how to implement the "hello world of big data" - WordCount In this
 * example, we use the input topic named "text-input" which has the format :
 *
 * <table border='1'>
 *   <th>word</th>
 *   <tr><td>hello</td></tr>
 *   <tr><td>ohara</td></tr>
 *   <tr><td>stream</td></tr>
 *   <tr><td>world</td></tr>
 *   <tr><td>of</td></tr>
 *   <tr><td>stream</td></tr>
 * </table>
 *
 * and write the count of words to output topic named "count-output" which has the format:
 *
 * <table border='1'>
 *   <th>word</th><th>count</th>
 *   <tr><td>hello</td><td>1</td></tr>
 *   <tr><td>ohara</td><td>1</td></tr>
 *   <tr><td>stream</td><td>2</td></tr>
 *   <tr><td>world</td><td>1</td></tr>
 *   <tr><td>of</td><td>1</td></tr>
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
 * <p>3. Crete two topic : <b>text-input</b> and <b>count-output</b> topic from Ohara Stream Manager
 *
 * <p>4. Use connector to write data into "text-input" with the column header: (word)
 *
 * <p>5. Start the stream. The result data will be wrote to the output topic "count-output" with the
 * column header: (word, count)
 */
public class WordCountExample extends Stream {
  @Override
  public void start(OStream<Row> ostream, StreamSetting streamSetting) {
    ostream
        // group the row with header: word
        .groupByKey(Collections.singletonList("word"))
        // count the word
        .count()
        // start this OStream
        .start();
  }
}
