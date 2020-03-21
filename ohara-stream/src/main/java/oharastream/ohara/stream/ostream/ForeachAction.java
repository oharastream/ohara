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

/** Loop all the {@code Row} data in this stream */
public interface ForeachAction {

  void foreachAction(final Row row);

  class TrueForeachAction implements org.apache.kafka.streams.kstream.ForeachAction<Row, byte[]> {

    private final ForeachAction trueForeachAction;

    TrueForeachAction(ForeachAction foreachAction) {
      this.trueForeachAction = foreachAction;
    }

    @Override
    public void apply(Row key, byte[] value) {
      // We only concern the key part
      this.trueForeachAction.foreachAction(key);
    }
  }
}
