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

package oharastream.ohara.agent.docker

import oharastream.ohara.agent.{ClusterStatus, Collie, DataCollie}
import oharastream.ohara.client.configurator.ClusterState
import oharastream.ohara.client.configurator.ContainerApi.ContainerInfo
import oharastream.ohara.client.configurator.NodeApi.Node
import oharastream.ohara.client.configurator.VolumeApi.Volume

import scala.concurrent.{ExecutionContext, Future}
private abstract class BasicCollieImpl(
  val dataCollie: DataCollie,
  val containerClient: DockerClient,
  clusterCache: ServiceCache
) extends Collie {
  final override def clusters()(implicit executionContext: ExecutionContext): Future[Seq[ClusterStatus]] =
    Future.successful(clusterCache.snapshot.filter(_.kind == kind))

  protected def updateRoute(existentNodes: Map[Node, ContainerInfo], routes: Map[String, String])(
    implicit executionContext: ExecutionContext
  ): Future[Unit] =
    Future
      .traverse(existentNodes.values.map(_.name))(
        name =>
          containerClient.containerInspector
            .name(name)
            .asRoot()
            .append("/etc/hosts", routes.map {
              case (hostname, ip) => s"$ip $hostname"
            }.toSeq)
      )
      .map(_ => ())

  override protected def doForceRemove(clusterInfo: ClusterStatus, beRemovedContainers: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext
  ): Future[Unit] =
    remove(clusterInfo, beRemovedContainers, true)

  override protected def doRemove(clusterInfo: ClusterStatus, beRemovedContainers: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext
  ): Future[Unit] =
    remove(clusterInfo, beRemovedContainers, false)

  private[this] def remove(clusterInfo: ClusterStatus, beRemovedContainers: Seq[ContainerInfo], force: Boolean)(
    implicit executionContext: ExecutionContext
  ): Future[Unit] =
    Future
      .traverse(beRemovedContainers)(
        containerInfo =>
          if (force) containerClient.forceRemove(containerInfo.name)
          else containerClient.remove(containerInfo.name)
      )
      .map { _ =>
        val newContainers =
          clusterInfo.containers.filterNot(container => beRemovedContainers.exists(_.name == container.name))
        if (newContainers.isEmpty) clusterCache.remove(clusterInfo)
        else clusterCache.put(clusterInfo.copy(containers = newContainers))
      }

  override protected def toClusterState(containers: Seq[ContainerInfo]): Option[ClusterState] =
    if (containers.isEmpty) None
    else {
      // one of the containers in pending state means cluster pending
      if (containers.exists(_.state == ContainerState.CREATED.name)) Some(ClusterState.PENDING)
      // not pending, if one of the containers in running state means cluster running (even other containers are in
      // restarting, paused, exited or dead state
      else if (containers.exists(_.state == ContainerState.RUNNING.name)) Some(ClusterState.RUNNING)
      // since cluster(collie) is a collection of long running containers,
      // we could assume cluster failed if containers are run into "exited" or "dead" state
      else if (containers.forall(c => c.state == ContainerState.EXITED.name || c.state == ContainerState.DEAD.name))
        Some(ClusterState.FAILED)
      // we set failed state is ok here
      // since there are too many cases that we could not handle for now, we should open the door for whitelist only
      else Some(ClusterState.FAILED)
    }

  //----------------------------[override helper methods]----------------------------//
  override protected def doCreator(
    executionContext: ExecutionContext,
    containerInfo: ContainerInfo,
    node: Node,
    routes: Map[String, String],
    arguments: Seq[String],
    volumeMaps: Map[Volume, String]
  ): Future[Unit] = {
    implicit val pool: ExecutionContext = executionContext
    actuallyVolumeMap(node, volumeMaps).flatMap { actuallyVolume =>
      containerClient.containerCreator
        .imageName(containerInfo.imageName)
        .portMappings(
          containerInfo.portMappings.map(portMapping => portMapping.hostPort -> portMapping.containerPort).toMap
        )
        .hostname(containerInfo.hostname)
        .envs(containerInfo.environments)
        .name(containerInfo.name)
        .routes(routes)
        .arguments(arguments)
        .nodeName(node.hostname)
        .threadPool(executionContext)
        .volumeMaps(actuallyVolume.map(e => e._1.key.toPlain -> e._2))
        .create()
    }
  }

  override protected def postCreate(
    clusterStatus: ClusterStatus,
    existentNodes: Map[Node, ContainerInfo],
    routes: Map[String, String],
    volumeMaps: Map[Volume, String]
  )(implicit executionContext: ExecutionContext): Future[Unit] =
    updateRoute(existentNodes, routes)
      .map { _ =>
        clusterCache.put(clusterStatus)
        ()
      }
}
