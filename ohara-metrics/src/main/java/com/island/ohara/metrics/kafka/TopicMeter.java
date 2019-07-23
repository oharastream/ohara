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

package com.island.ohara.metrics.kafka;

import com.island.ohara.common.annotations.VisibleForTesting;
import com.island.ohara.common.util.CommonUtils;
import com.island.ohara.metrics.BeanObject;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** this class represents the topic metrics recorded by kafka. */
public class TopicMeter {
  // -------------------------[property keys]-------------------------//
  private static final String DOMAIN = "kafka.server";
  private static final String TYPE_KEY = "type";
  private static final String TYPE_VALUE = "BrokerTopicMetrics";
  private static final String TOPIC_KEY = "topic";
  private static final String NAME_KEY = "name";
  // -------------------------[attribute keys]-------------------------//
  private static final String COUNT_KEY = "Count";
  private static final String EVENT_TYPE_KEY = "EventType";
  private static final String FIFTEEN_MINUTE_RATE_KEY = "FifteenMinuteRate";
  private static final String FIVE_MINUTE_RATE_KEY = "FiveMinuteRate";
  private static final String MEAN_RATE_KEY = "MeanRate";
  private static final String ONE_MINUTE_RATE_KEY = "OneMinuteRate";
  private static final String RATE_UNIT_KEY = "RateUnit";

  /** reference to kafka.server.BrokerTopicStats */
  public enum Catalog {
    MessagesInPerSec,
    BytesInPerSec,
    BytesOutPerSec,
    BytesRejectedPerSec,
    FailedProduceRequestsPerSec,
    FailedFetchRequestsPerSec,
    TotalProduceRequestsPerSec,
    TotalFetchRequestsPerSec,
    FetchMessageConversionsPerSec,
    ProduceMessageConversionsPerSec
  }

  public static boolean is(BeanObject obj) {
    return obj.domainName().equals(DOMAIN)
        && TYPE_VALUE.equals(obj.properties().get(TYPE_KEY))
        && obj.properties().containsKey(TOPIC_KEY)
        && obj.properties().containsKey(NAME_KEY)
        && Stream.of(Catalog.values())
            .anyMatch(catalog -> catalog.name().equals(obj.properties().get(NAME_KEY)));
  }

  public static TopicMeter of(BeanObject obj) {
    return new TopicMeter(
        obj.properties().get(TOPIC_KEY),
        Catalog.valueOf(obj.properties().get(NAME_KEY)),
        // the metrics of kafka topic may be not ready and we all hate null. Hence, we fill some
        // "default value" to it.
        // the default value will be replaced by true value later.
        (long) obj.attributes().getOrDefault(COUNT_KEY, 0),
        (String) obj.attributes().getOrDefault(EVENT_TYPE_KEY, "unknown event"),
        (double) obj.attributes().getOrDefault(FIFTEEN_MINUTE_RATE_KEY, 0),
        (double) obj.attributes().getOrDefault(FIVE_MINUTE_RATE_KEY, 0),
        (double) obj.attributes().getOrDefault(MEAN_RATE_KEY, 0),
        (double) obj.attributes().getOrDefault(ONE_MINUTE_RATE_KEY, 0),
        (TimeUnit) obj.attributes().getOrDefault(RATE_UNIT_KEY, TimeUnit.SECONDS),
        obj.queryTime());
  }

  private final String topicName;
  private final Catalog catalog;
  private final long count;
  private final String eventType;
  private final double fifteenMinuteRate;
  private final double fiveMinuteRate;
  private final double meanRate;
  private final double oneMinuteRate;
  private final TimeUnit rateUnit;
  private final long queryTime;

  @VisibleForTesting
  TopicMeter(
      String topicName,
      Catalog catalog,
      long count,
      String eventType,
      double fifteenMinuteRate,
      double fiveMinuteRate,
      double meanRate,
      double oneMinuteRate,
      TimeUnit rateUnit,
      long queryTime) {
    this.topicName = CommonUtils.requireNonEmpty(topicName);
    this.catalog = Objects.requireNonNull(catalog);
    this.eventType = CommonUtils.requireNonEmpty(eventType);
    this.count = count;
    this.fifteenMinuteRate = fifteenMinuteRate;
    this.fiveMinuteRate = fiveMinuteRate;
    this.meanRate = meanRate;
    this.oneMinuteRate = oneMinuteRate;
    this.rateUnit = Objects.requireNonNull(rateUnit);
    this.queryTime = queryTime;
  }

  public String topicName() {
    return topicName;
  }

  public Catalog catalog() {
    return catalog;
  }

  public long count() {
    return count;
  }

  public String eventType() {
    return eventType;
  }

  public double fifteenMinuteRate() {
    return fifteenMinuteRate;
  }

  public double fiveMinuteRate() {
    return fiveMinuteRate;
  }

  public double meanRate() {
    return meanRate;
  }

  public double oneMinuteRate() {
    return oneMinuteRate;
  }

  public TimeUnit rateUnit() {
    return rateUnit;
  }

  public long queryTime() {
    return queryTime;
  }
}
