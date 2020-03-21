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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;

class StreamsConfig {
  static final String BOOTSTRAP_SERVERS =
      org.apache.kafka.streams.StreamsConfig.BOOTSTRAP_SERVERS_CONFIG;
  static final String APP_ID = org.apache.kafka.streams.StreamsConfig.APPLICATION_ID_CONFIG;
  static final String CLIENT_ID = org.apache.kafka.streams.StreamsConfig.CLIENT_ID_CONFIG;
  static final String STATE_DIR = org.apache.kafka.streams.StreamsConfig.STATE_DIR_CONFIG;
  static final String DEFAULT_KEY_SERDE =
      org.apache.kafka.streams.StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG;
  static final String DEFAULT_VALUE_SERDE =
      org.apache.kafka.streams.StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG;
  static final String TIMESTAMP_EXTRACTOR =
      org.apache.kafka.streams.StreamsConfig.DEFAULT_TIMESTAMP_EXTRACTOR_CLASS_CONFIG;
  static final String AUTO_OFFSET = ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
  static final String CACHE_BUFFER =
      org.apache.kafka.streams.StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG;
  static final String COMMIT_INTERVAL =
      org.apache.kafka.streams.StreamsConfig.COMMIT_INTERVAL_MS_CONFIG;
  static final String THREADS = org.apache.kafka.streams.StreamsConfig.NUM_STREAM_THREADS_CONFIG;
  static final String GUARANTEE =
      org.apache.kafka.streams.StreamsConfig.PROCESSING_GUARANTEE_CONFIG;
  static final String ACKS = ProducerConfig.ACKS_CONFIG;
  static final String TASK_IDLE_MS = org.apache.kafka.streams.StreamsConfig.MAX_TASK_IDLE_MS_CONFIG;

  enum GUARANTEES {
    EXACTLY_ONCE(org.apache.kafka.streams.StreamsConfig.EXACTLY_ONCE),
    AT_LEAST_ONCE(org.apache.kafka.streams.StreamsConfig.AT_LEAST_ONCE);

    private final String actualName;

    GUARANTEES(String actualName) {
      this.actualName = actualName;
    }

    public String getName() {
      return this.actualName;
    }
  }

  // Internal topic settings
  static final String INTERNAL_RETENTION_BYTES =
      org.apache.kafka.streams.StreamsConfig.TOPIC_PREFIX + TopicConfig.RETENTION_BYTES_CONFIG;
  static final String INTERNAL_INDEX_BYTES =
      org.apache.kafka.streams.StreamsConfig.TOPIC_PREFIX + TopicConfig.SEGMENT_INDEX_BYTES_CONFIG;
  static final String INTERNAL_INDEX_INTERVAL_BYTES =
      org.apache.kafka.streams.StreamsConfig.TOPIC_PREFIX + TopicConfig.INDEX_INTERVAL_BYTES_CONFIG;
  static final String INTERNAL_CLEANUP_POLICY =
      org.apache.kafka.streams.StreamsConfig.TOPIC_PREFIX + TopicConfig.CLEANUP_POLICY_CONFIG;
  static final String INTERNAL_CLEANABLE_RATIO =
      org.apache.kafka.streams.StreamsConfig.TOPIC_PREFIX
          + TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG;
}
