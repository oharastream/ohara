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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import oharastream.ohara.common.data.Cell;
import oharastream.ohara.common.data.Row;
import oharastream.ohara.stream.OStream;
import oharastream.ohara.stream.Stream;
import oharastream.ohara.stream.config.StreamSetting;

/**
 * A simple example to illustrate how to use `reduce` to sum <i>odd numbers</i>. We use the input
 * topic named "number-input" which has the format :
 *
 * <table border='1'>
 *   <th>number</th>
 *   <tr><td>1</td></tr>
 *   <tr><td>2</td></tr>
 *   <tr><td>14</td></tr>
 *   <tr><td>17</td></tr>
 *   <tr><td>36</td></tr>
 *   <tr><td>99</td></tr>
 * </table>
 *
 * and write the count of words to output topic named "sum-output" which has the format: (Here the
 * dummy column is used for group the number column, so the value is not very important)
 *
 * <table border='1'>
 *   <th>dummy</th><th>number</th>
 *   <tr><td>1</td><td>1</td></tr>
 *   <tr><td>1</td><td>18</td></tr>
 *   <tr><td>1</td><td>117</td></tr>
 * </table>
 *
 * <p>Note: the result is a streaming data ; the count should be accumulated
 *
 * <p>How to run this example?
 *
 * <p>1. Package this example with your favorite build tool (maven, gradle...)
 *
 * <p>2. Upload the packaged jar to Ohara Stream Manager, Add a Stream into pipeline
 *
 * <p>3. Crete two topic : <b>number-input</b> and <b>sum-output</b> topic from Ohara Stream Manager
 *
 * <p>4. Use connector to write data into "number-input" with the column header: (number)
 *
 * <p>5. Start the stream. The result data will be wrote to the output topic "sum-output" with the
 * column header: (sum)
 */
@SuppressWarnings("rawtypes")
public class SumExample extends Stream {
  @Override
  public void start(OStream<Row> ostream, StreamSetting streamSetting) {
    ostream
        // filter out even number
        .filter(row -> Integer.parseInt(row.cell("number").value().toString()) % 2 != 0)
        // add a temp cell "dummy" for groupBy use
        .map(
            row -> {
              Cell<Integer> dummyCell = Cell.of("dummy", 1);
              List<Cell> newCells = new ArrayList<>();
              newCells.add(dummyCell);
              newCells.addAll(row.cells());
              return Row.of(newCells.toArray(new Cell[0]));
            })
        // group the row with header: dummy
        .groupByKey(Collections.singletonList("dummy"))
        // sum the result (since we assure the input value is "int" type, here we directly use the
        // int wrapped type)
        .reduce(((Integer v1, Integer v2) -> v1 + v2), "number")
        .start();
  }
}
