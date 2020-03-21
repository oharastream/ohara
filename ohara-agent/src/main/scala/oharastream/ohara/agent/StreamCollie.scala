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

package oharastream.ohara.agent

import java.util.Objects

import oharastream.ohara.agent.docker.ContainerState
import oharastream.ohara.client.configurator.v0.BrokerApi.BrokerClusterInfo
import oharastream.ohara.client.configurator.v0.ClusterStatus.Kind
import oharastream.ohara.client.configurator.v0.ContainerApi.{ContainerInfo, PortMapping}
import oharastream.ohara.client.configurator.v0.FileInfoApi.FileInfo
import oharastream.ohara.client.configurator.v0.NodeApi.Node
import oharastream.ohara.client.configurator.v0.StreamApi.Creation
import oharastream.ohara.client.configurator.v0.{ClusterStatus, StreamApi}
import oharastream.ohara.common.setting.ObjectKey
import oharastream.ohara.stream.Stream
import oharastream.ohara.stream.config.StreamSetting
import com.typesafe.scalalogging.Logger
import spray.json.JsString

import scala.concurrent.{ExecutionContext, Future}

/**
  * An interface of controlling stream cluster.
  * It isolates the implementation of container manager from Configurator.
  */
trait StreamCollie extends Collie {
  protected val log = Logger(classOf[StreamCollie])
  override def creator: StreamCollie.ClusterCreator =
    (executionContext, creation) => {
      implicit val exec: ExecutionContext = executionContext

      val resolveRequiredInfos = for {
        allNodes <- dataCollie.valuesByNames[Node](creation.nodeNames)
        existentNodes <- clusters().map(_.find(_.key == creation.key)).flatMap {
          case Some(value) =>
            dataCollie
              .valuesByNames[Node](value.nodeNames)
              .map(nodes => nodes.map(node => node -> value.containers.find(_.nodeName == node.hostname).get).toMap)
          case None => Future.successful(Map.empty[Node, ContainerInfo])
        }
        brokerClusterInfo <- dataCollie.value[BrokerClusterInfo](creation.brokerClusterKey)
        fileInfo          <- dataCollie.value[FileInfo](creation.jarKey)
      } yield (
        existentNodes,
        allNodes.filterNot(node => existentNodes.exists(_._1.hostname == node.hostname)),
        brokerClusterInfo,
        fileInfo
      )

      resolveRequiredInfos
        .map {
          case (existentNodes, newNodes, brokerClusterInfo, fileInfo) =>
            if (existentNodes.nonEmpty)
              throw new UnsupportedOperationException(s"stream collie doesn't support to add node to a running cluster")
            else (newNodes, brokerClusterInfo, fileInfo)
        }
        .flatMap {
          case (newNodes, brokerClusterInfo, fileInfo) =>
            val routes = resolveHostNames(
              (newNodes.map(_.hostname)
                ++ brokerClusterInfo.nodeNames
              // make sure the stream can connect to configurator
                ++ Seq(fileInfo.url.get.getHost)).toSet
            ) ++ creation.routes
            val successfulContainersFuture =
              if (newNodes.isEmpty) Future.successful(Seq.empty)
              else {
                // ssh connection is slow so we submit request by multi-thread
                Future.sequence(newNodes.map { newNode =>
                  val containerInfo = ContainerInfo(
                    nodeName = newNode.name,
                    id = Collie.UNKNOWN,
                    imageName = creation.imageName,
                    // this fake container will be cached before refreshing cache so we make it running.
                    // other, it will be filtered later ...
                    state = ContainerState.RUNNING.name,
                    kind = Collie.UNKNOWN,
                    name = Collie.containerName(creation.group, creation.name, kind),
                    size = -1,
                    portMappings = creation.ports
                      .map(
                        port =>
                          PortMapping(
                            hostIp = Collie.UNKNOWN,
                            hostPort = port,
                            containerPort = port
                          )
                      )
                      .toSeq,
                    environments = Map(
                      "JMX_PORT"     -> creation.jmxPort.toString,
                      "JMX_HOSTNAME" -> newNode.hostname,
                      // define the urls as string list so as to simplify the script for stream
                      "STREAM_JAR_URLS" -> fileInfo.url.get.toURI.toASCIIString,
                      "OHARA_HEAP_OPTS" -> s"-Xms${creation.initHeap}M -Xmx${creation.maxHeap}M"
                    ),
                    // we should set the hostname to container name in order to avoid duplicate name with other containers
                    hostname = Collie.containerHostName(creation.group, creation.name, kind)
                  )

                  val arguments =
                    Seq(classOf[Stream].getName) ++ creation.settings
                      .map {
                        case (k, v) =>
                          k -> (v match {
                            // the string in json representation has quote in the beginning and end.
                            // we don't like the quotes since it obstruct us to cast value to pure string.
                            case JsString(s) => s
                            // save the json string for all settings
                            // StreamDefUtils offers the helper method to turn them back.
                            case _ => StreamSetting.toEnvString(v.toString)
                          })
                      }
                      .map {
                        case (k, v) => s"$k=$v"
                      }

                  doCreator(executionContext, containerInfo, newNode, routes, arguments)
                    .map(_ => Some(containerInfo))
                    .recover {
                      case e: Throwable =>
                        log.error(s"failed to create stream container on ${newNode.hostname}", e)
                        None
                    }
                })
              }

            successfulContainersFuture.map(_.flatten.toSeq).flatMap { aliveContainers =>
              postCreate(
                clusterStatus = ClusterStatus(
                  group = creation.group,
                  name = creation.name,
                  containers = aliveContainers,
                  kind = ClusterStatus.Kind.STREAM,
                  state = toClusterState(aliveContainers).map(_.name),
                  error = None
                ),
                existentNodes = Map.empty,
                routes = routes
              )
            }
        }
    }

  override protected[agent] def toStatus(key: ObjectKey, containers: Seq[ContainerInfo])(
    implicit executionContext: ExecutionContext
  ): Future[ClusterStatus] =
    Future.successful(
      ClusterStatus(
        group = key.group(),
        name = key.name(),
        containers = containers,
        kind = ClusterStatus.Kind.STREAM,
        state = toClusterState(containers).map(_.name),
        error = None
      )
    )

  override val kind: Kind = Kind.STREAM
}

object StreamCollie {
  trait ClusterCreator extends Collie.ClusterCreator with StreamApi.Request {
    override def create(): Future[Unit] = doCreate(
      executionContext = Objects.requireNonNull(executionContext),
      creation = creation
    )

    protected def doCreate(executionContext: ExecutionContext, creation: Creation): Future[Unit]
  }
}
