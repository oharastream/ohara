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

package oharastream.ohara.configurator.route

import java.io.File

object RouteUtils {
  /**
    * the test of Configurator is executed after building a test-purposed stream jar (see ohara-configurator/build.gradle)
    * so it is ok to assume the jar file exists
    * @return file
    */
  def streamFile: File = new File(new File(".").getCanonicalPath, "../ohara-stream/build/libs/ohara-ut-stream.jar")

  /**
    * the jar of connector has "version" but it is changed in running QA. Hence, we use seek directory to find the jar.
    * @return connector jar
    */
  def connectorFile: File =
    new File(new File(".").getCanonicalPath, s"../ohara-kafka/build/libs/")
      .listFiles()
      .filter(_.getName.endsWith("tests.jar"))
      .head

  /**
    * we take the production jar since the RowPartitioner is NOT abstract so we can use it
    * @return
    */
  def partitionerFile: File =
    new File(new File(".").getCanonicalPath, s"../ohara-kafka/build/libs/")
      .listFiles()
      .filterNot(_.getName.endsWith("tests.jar"))
      .head
}
