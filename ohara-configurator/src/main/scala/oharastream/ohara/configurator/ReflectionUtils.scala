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

package oharastream.ohara.configurator

import java.lang.reflect.Modifier

import oharastream.ohara.client.configurator.v0.FileInfoApi.ClassInfo
import oharastream.ohara.common.setting.WithDefinitions
import com.typesafe.scalalogging.Logger
import oharastream.ohara.kafka.connector.{RowSinkConnector, RowSourceConnector}
import org.reflections.Reflections

import scala.jdk.CollectionConverters._
object ReflectionUtils {
  private[this] val LOG = Logger(ReflectionUtils.getClass)

  /**
    * Dynamically instantiate local connector classes and then fetch the definitions from them.
    * @return local connector definitions
    */
  lazy val localConnectorDefinitions: Seq[ClassInfo] =
    new Reflections()
      .getSubTypesOf(classOf[WithDefinitions])
      .asScala
      .toSeq
      .filter(
        clz => classOf[RowSourceConnector].isAssignableFrom(clz) || classOf[RowSinkConnector].isAssignableFrom(clz)
      )
      // the abstract class is not instantiable.
      .filterNot(clz => Modifier.isAbstract(clz.getModifiers))
      .flatMap { clz =>
        try Some((clz.getName, clz.getDeclaredConstructor().newInstance().settingDefinitions().values().asScala.toSeq))
        catch {
          case e: Throwable =>
            LOG.error(s"failed to instantiate ${clz.getName}", e)
            None
        }
      }
      .map {
        case (className, definitions) =>
          ClassInfo(
            className = className,
            settingDefinitions = definitions
          )
      }
}
