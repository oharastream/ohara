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

package oharastream.ohara.client.configurator

import oharastream.ohara.client.configurator.BrokerApi.BrokerClusterInfo
import oharastream.ohara.client.configurator.ShabondiApi.ShabondiClusterInfo
import oharastream.ohara.client.configurator.StreamApi.StreamClusterInfo
import oharastream.ohara.client.configurator.WorkerApi.WorkerClusterInfo
import oharastream.ohara.client.configurator.ZookeeperApi.ZookeeperClusterInfo
import oharastream.ohara.common.setting.ObjectKey
import spray.json.DefaultJsonProtocol._
import spray.json.JsValue

/**
  * There are many kinds of cluster hosted by ohara. We extract an interface to define "what" information should be included by a "cluster
  * information".
  */
trait ClusterInfo extends Data {
  /**
    * @return the settings to set up this cluster. This is the raw data of settings.
    */
  def settings: Map[String, JsValue]

  /**
    * @return docker image name used to build container for this cluster
    */
  def imageName: String = settings(IMAGE_NAME_KEY).convertTo[String]

  /**
    * All services hosted by ohara should use some ports, which are used to communicate.
    * We "highlight" this method since port checking is a important thing for configurator
    * @return ports used by this cluster
    */
  def ports: Set[Int]

  /**
    * the port used to expose the jmx service
    * @return jmx port
    */
  def jmxPort: Int = settings(JMX_PORT_KEY).convertTo[Int]

  /**
    * @return nodes running this cluster
    */
  def nodeNames: Set[String] = settings(NODE_NAMES_KEY).convertTo[Set[String]]

  /**
    * @return the state of this cluster. None means the cluster is not running
    */
  def state: Option[ClusterState]

  /**
    * @return the error message of the dead cluster
    */
  def error: Option[String]

  /**
    * the nodes do run the containers of cluster.
    * @return a collection of node names
    */
  def aliveNodes: Set[String]

  /**
    * List the dead nodes. the number of dead nodes is calculated since the gone container may be not leave any information
    * to us to trace. By contrast, the number of alive nodes is real since we can observe the "running" state from the
    * nodes.
    *
    * @return nothing if there is no state (normally, it means there is no containers on the nodes). otherwise, the number
    *         of dead nodes is equal to (the number of node names) - (the number of alive nodes)
    */
  def deadNodes: Set[String] = if (state.isEmpty) Set.empty else nodeNames -- aliveNodes

  /**
    * this is a small helper method used to update the node names for cluster info
    * @return updated cluster info
    */
  def newNodeNames(newNodeNames: Set[String]): ClusterInfo = this match {
    case c: ZookeeperClusterInfo =>
      c.copy(settings = ZookeeperApi.access.request.settings(settings).nodeNames(newNodeNames).creation.raw)
    case c: BrokerClusterInfo =>
      c.copy(settings = BrokerApi.access.request.settings(settings).nodeNames(newNodeNames).creation.raw)
    case c: WorkerClusterInfo =>
      c.copy(settings = WorkerApi.access.request.settings(settings).nodeNames(newNodeNames).creation.raw)
    case c: StreamClusterInfo =>
      c.copy(settings = StreamApi.access.request.settings(settings).nodeNames(newNodeNames).creation.raw)
    case c: ShabondiClusterInfo =>
      c.copy(settings = ShabondiApi.access.request.settings(settings).nodeNames(newNodeNames).creation.raw)
    case _: ClusterInfo => throw new UnsupportedOperationException("who are you?")
  }

  /**
    * @return size (in MB) of init heap
    */
  def initHeap: Int = settings(INIT_HEAP_KEY).convertTo[Int]

  /**
    * @return size (in MB) of max heap
    */
  def maxHeap: Int = settings(MAX_HEAP_KEY).convertTo[Int]

  /**
    * @return the volume object key and related container path. For example, "{"group":"a", "name": "b"}" -> "/tmp/aaa" means the
    *         object key "{"group":"a", "name": "b"}" is mapped to a volume and the volume should be mounted on
    *         container's "/tmp/aaa"
    */
  def volumeMaps: Map[ObjectKey, String] = Map.empty
}
