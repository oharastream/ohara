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

package com.island.ohara.it.performance

import java.util.Objects

import com.island.ohara.common.setting.ObjectKey
import com.island.ohara.common.util.CommonUtils

import scala.collection.immutable.ListMap
import scala.collection.mutable

trait PerformanceReport {
  /**
    * @return key of object having this metrics report
    */
  def key: ObjectKey

  def className: String

  /**
    * the order of key (duration) is ascending.
    * @return key is "duration" and the value is "header -> value"
    */
  def records: Map[Long, Map[String, Double]]
}

object PerformanceReport {
  def builder = new Builder

  final class Builder private[PerformanceReport] extends com.island.ohara.common.pattern.Builder[PerformanceReport] {
    private[this] var key: ObjectKey    = _
    private[this] var className: String = _
    private[this] val records           = mutable.Map[Long, Map[String, Double]]()

    def connectorKey(key: ObjectKey): Builder = {
      this.key = Objects.requireNonNull(key)
      this
    }

    def className(className: String): Builder = {
      this.className = CommonUtils.requireNonEmpty(className)
      this
    }

    def record(duration: Long, header: String, value: Double): Builder = {
      records.put(duration, records.getOrElse(duration, Map.empty) + (header -> value))
      this
    }

    override def build: PerformanceReport = new PerformanceReport {
      override val className: String = CommonUtils.requireNonEmpty(Builder.this.className)

      override val records: Map[Long, Map[String, Double]] = ListMap(
        Builder.this.records.toSeq.sortBy(_._1)((x: Long, y: Long) => y.compare(x)): _*
      )

      override def key: ObjectKey = Objects.requireNonNull(Builder.this.key)
    }
  }
}