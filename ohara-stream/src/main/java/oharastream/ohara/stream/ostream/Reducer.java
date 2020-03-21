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

import oharastream.ohara.common.data.Cell;
import oharastream.ohara.common.data.Row;

/**
 * The {@code Reducer} interface for combining two values of the {@code OStream}, and return the
 * result of same type. The values of {@code OStream} is the value of a {@code Cell}, the data type
 * is stored in the {@code Cell} which in here is {@code Object}. You should convert the data type
 * before you compute it.
 *
 * @see org.apache.kafka.streams.kstream.Reducer
 */
@SuppressWarnings("unchecked")
public interface Reducer<T> {

  T reducer(T value1, T value2);

  @SuppressWarnings("unchecked")
  class TrueReducer<V> implements org.apache.kafka.streams.kstream.Reducer<Row> {

    private final Reducer<V> trueReducer;
    private final String col;

    TrueReducer(Reducer<V> reducer, String col) {
      this.trueReducer = reducer;
      this.col = col;
    }

    @Override
    public Row apply(Row value1, Row value2) {
      return Row.of(
          Cell.of(
              col,
              this.trueReducer.reducer(
                  (V) value1.cell(col).value(), (V) value2.cell(col).value())));
    }
  }
}
