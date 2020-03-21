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

import oharastream.ohara.common.data.Cell;
import oharastream.ohara.common.data.Row;
import oharastream.ohara.stream.config.StreamSetting;

public class SimpleApplicationForOharaEnv extends Stream {
  /**
   * This is a simple version of stream running in ohara environment. Please packaging this file
   * into a jar, and uploading by API or Ohara UI
   */
  @Override
  public void start(OStream<Row> ostream, StreamSetting streamSetting) {

    // A simple sample to illustrate how to use OStream
    // for example :
    // Topic-A <Row, byte[]> -> Topic-B <Row, byte[]>
    //
    // Topic-A data is
    // index,name,age
    // 1,samcho,31
    // 2,johndoe,20
    //
    // will transform and save to Topic-B
    //
    // index,name,age
    // 1,SAMCHO,31
    // 2,JOHNDOE,20
    //
    // Note : It will do nothing if there was no data in Topic-A
    ostream
        .map(
            row ->
                row.size() > 0
                    ? Row.of(
                        row.cell("index"),
                        Cell.of("name", row.cell("name").value().toString().toUpperCase()),
                        row.cell("age"))
                    : Row.EMPTY)
        .start();
  }
}
