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

package oharastream.ohara.agent.container

/**
  * @param name volume name
  * @param driver volume driver. For example, local, nfs or tmpFs.
  * @param path the path on the driver.
  * @param nodeName the node hosting this volume
  */
case class ContainerVolume(name: String, driver: String, path: String, nodeName: String)
