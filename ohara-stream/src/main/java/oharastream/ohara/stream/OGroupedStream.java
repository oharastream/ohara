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

import oharastream.ohara.common.data.Row;
import oharastream.ohara.stream.ostream.Reducer;

/**
 * {@code OGroupedStream} is a <i>grouped stream</i> by key.
 *
 * @param <T> Type of the value
 */
public interface OGroupedStream<T extends Row> {

  /**
   * Count the number of records in this {@code OGroupedStream} and return the count value in a new
   * {@code Row} with the {@code Cell} format of combination {key_row} + ("count", count_value)
   *
   * @return {@code OStream}
   * @see org.apache.kafka.streams.kstream.KGroupedStream#count()
   */
  OStream<T> count();

  /**
   * Combine the values of each record in this {@code OGroupedStream} by the grouped key. This
   * operation will return the reduce value of specific column, and result a new {@code Row} with
   * the group {@code Cell} and the reduce column {@code Cell}
   *
   * @param reducer a {@link Reducer} that computes a new aggregate result.
   * @param reduceColumn the column that computing reduce function
   * @param <V> the type of value of reducer
   * @return {@code OStream}
   * @see
   *     org.apache.kafka.streams.kstream.KGroupedStream#reduce(org.apache.kafka.streams.kstream.Reducer)
   */
  <V> OStream<T> reduce(final Reducer<V> reducer, final String reduceColumn);
}
