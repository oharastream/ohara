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

package com.island.ohara.connector.jio

import com.island.ohara.common.data.{Cell, Row}
import spray.json.{DeserializationException, JsBoolean, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}
class JioData private (val raw: Map[String, JsValue]) {

  /**
    * convert this jio data to row.
    * @return row
    */
  def row: Row = {
    val cells: Seq[Cell[_]] = raw.map {
      case (k, v) =>
        v match {
          case JsNumber(i) =>
            Cell.of(
              k,
              // we prefer java.BigDecimal as the public code to connector developers is "java" rather than "scala".
              // Returning scala.BigDecimal may obstruct java developers from using JIO connectors.
              i.bigDecimal
            )
          case JsString(s)  => Cell.of(k, s)
          case JsBoolean(b) => Cell.of(k, b)
          // in construction we have rejected the unsupported types
          case _ => throw new IllegalArgumentException(s"${v.getClass.getName} is unsupported!!!")
        }
    }.toSeq
    Row.of(cells: _*)
  }

  override def equals(o: Any): Boolean = o match {
    case other: JioData => other.raw == raw
    case _              => false
  }

  override def hashCode(): Int = raw.hashCode()
  override def toString: String = raw.toString()
}

object JioData {

  /**
    * return input or throw DeserializationException if the input has unsupported types
    * @param raw data
    * @return input data
    */
  private[this] def check(raw: Map[String, JsValue]): Map[String, JsValue] = raw.map {
    case (k, v) =>
      k -> (v match {
        case JsNumber(_)  => v
        case JsString(_)  => v
        case JsBoolean(_) => v
        case _            => throw DeserializationException(s"${v.getClass.getName} is unsupported", fieldNames = List(k))
      })
  }

  def apply(raw: Map[String, JsValue]): JioData = new JioData(check(raw))

  def apply(row: Row): JioData = JioData {
    import scala.collection.JavaConverters._
    row
      .cells()
      .asScala
      .map { cell =>
        cell.name() -> (cell.value() match {
          case b: Boolean => JsBoolean(b)
          case s: String  => JsString(s)
          case i: Short   => JsNumber(i)
          case i: Int     => JsNumber(i)
          case i: Long    => JsNumber(i)
          case i: Float   => JsNumber(i)
          case i: Double  => JsNumber(i)

          /**
            * the BigDecimal used by JsonIn is java implementation.
            */
          case i: java.math.BigDecimal => JsNumber(i)

          /**
            * the is the channel of communication between java and scala :)
            */
          case i: BigDecimal => JsNumber(i)
          case _             => throw new IllegalArgumentException(s"${cell.value().getClass.getName} is unsupported!!!")
        })
      }
      .toMap
  }
  implicit val JIO_DATA_FORMAT: RootJsonFormat[JioData] = new RootJsonFormat[JioData] {

    override def write(obj: JioData): JsValue = JsObject(check(obj.raw))
    override def read(json: JsValue): JioData = JioData(check(json.asJsObject.fields))
  }
}
