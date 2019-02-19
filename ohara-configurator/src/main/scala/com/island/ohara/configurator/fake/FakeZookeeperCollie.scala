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

package com.island.ohara.configurator.fake

import com.island.ohara.agent.ZookeeperCollie
import com.island.ohara.client.configurator.v0.ZookeeperApi.ZookeeperClusterInfo

import scala.concurrent.Future

private[configurator] class FakeZookeeperCollie extends FakeCollie[ZookeeperClusterInfo] with ZookeeperCollie {
  override def creator(): ZookeeperCollie.ClusterCreator =
    (clusterName, imageName, clientPort, peerPort, electionPort, nodeNames) =>
      Future.successful(
        addCluster(
          FakeZookeeperClusterInfo(
            name = clusterName,
            imageName = imageName,
            clientPort = clientPort,
            peerPort = peerPort,
            electionPort = electionPort,
            nodeNames = nodeNames
          )))

  override def removeNode(clusterName: String, nodeName: String): Future[ZookeeperClusterInfo] =
    Future.failed(
      new UnsupportedOperationException("zookeeper collie doesn't support to remove node from a running cluster"))

  override def addNode(clusterName: String, nodeName: String): Future[ZookeeperClusterInfo] =
    Future.failed(
      new UnsupportedOperationException("zookeeper collie doesn't support to remove node from a running cluster"))
}
