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

package oharastream.ohara.it.stream
import oharastream.ohara.common.data.Row
import oharastream.ohara.stream.config.StreamSetting
import oharastream.ohara.stream.{OStream, Stream}

/**
  * This is a sample stream that will do nothing but write data to output topic
  * It is not placed at test scope since we need jar when tests manually.
  */
class DumbStream extends Stream {
  override def start(ostream: OStream[Row], configs: StreamSetting): Unit = {
    // do nothing but only start stream and write exactly data to output topic
    ostream.start()
  }
}
