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

import oharastream.ohara.common.data.Row;
import oharastream.ohara.stream.OStream;
import oharastream.ohara.stream.OTable;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KTable;

@SuppressWarnings({"rawtypes", "unchecked"})
public class OTableImpl extends AbstractStream<Row, Row> implements OTable<Row> {
  OTableImpl(OStreamBuilder ob, KTable<Row, Row> ktable, StreamsBuilder builder) {
    super(ob, ktable, builder);
  }

  @Override
  public OStream<Row> toOStream() {
    return new OStreamImpl(builder, ktable.toStream(), innerBuilder);
  }
}
