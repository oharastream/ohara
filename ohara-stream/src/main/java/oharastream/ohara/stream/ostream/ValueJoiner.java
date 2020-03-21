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
 * The {@code ValueJoiner} interface represents a row-returned joiner function. This function should
 * use to join two different row data into one.
 *
 * @see org.apache.kafka.streams.kstream.ValueJoiner
 */
public interface ValueJoiner {
  Row apply(final Row value1, final Row value2);
}
