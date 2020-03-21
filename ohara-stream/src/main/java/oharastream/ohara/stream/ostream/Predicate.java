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

/**
 * The {@code Predicate} interface represents a boolean-returned filter function. This function
 * should use to filter a {@code Row} data.
 *
 * @see org.apache.kafka.streams.kstream.Predicate
 */
public interface Predicate {

  boolean test(final Row value);

  final class TruePredicate implements org.apache.kafka.streams.kstream.Predicate<Row, Row> {
    final Predicate truePredicate;

    TruePredicate(Predicate predicate) {
      this.truePredicate = predicate;
    }

    // Since the only concern part is the "value", we pass the predicate of key
    @Override
    public boolean test(final Row key, final Row value) {
      return this.truePredicate.test(value);
    }
  }
}
