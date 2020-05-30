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
import java.lang.reflect.Modifier
import java.net.{URL, URLClassLoader}
import java.util.Objects
import java.util.concurrent.{ExecutorService, Executors}

import com.typesafe.scalalogging.Logger
import oharastream.ohara.agent.container.{ContainerClient, ContainerName}
import oharastream.ohara.agent.docker.ServiceCollieImpl
import oharastream.ohara.agent.k8s.{K8SClient, K8SServiceCollieImpl}
import oharastream.ohara.client.configurator.v0.FileInfoApi.ClassInfo
import oharastream.ohara.client.configurator.v0.InspectApi.FileContent
import oharastream.ohara.client.configurator.v0.NodeApi.{Node, Resource}
import oharastream.ohara.client.configurator.v0.VolumeApi.VolumeState
import oharastream.ohara.common.annotations.Optional
import oharastream.ohara.common.pattern.Builder
import oharastream.ohara.common.setting.{ObjectKey, WithDefinitions}
import oharastream.ohara.common.util.{CommonUtils, Releasable}
import oharastream.ohara.kafka.RowPartitioner
import oharastream.ohara.kafka.connector.{RowSinkConnector, RowSourceConnector}
import oharastream.ohara.stream.Stream
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder

import scala.jdk.CollectionConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * This is the top-of-the-range "collie". It maintains and organizes all collies.
  * Each getter should return new instance of collie since each collie has close() method.
  * However, it is ok to keep global instance of collie if they have dump close().
  * Currently, default implementation is based on ssh and docker command. It is simple but slow.
  */
abstract class ServiceCollie extends Releasable {
  /**
    * @return the internal container client
    */
  protected def containerClient: ContainerClient

  /**
    * create a collie for zookeeper cluster
    * @return zookeeper collie
    */
  def zookeeperCollie: ZookeeperCollie

  /**
    * create a collie for broker cluster
    * @return broker collie
    */
  def brokerCollie: BrokerCollie

  /**
    * create a collie for worker cluster
    * @return worker collie
    */
  def workerCollie: WorkerCollie

  /**
    * create a collie for stream cluster
    * @return stream collie
    */
  def streamCollie: StreamCollie

  def shabondiCollie: ShabondiCollie

  /**
    * the default implementation is expensive!!! Please override this method if you are a good programmer.
    * @return a collection of all clusters
    */
  def clusters()(implicit executionContext: ExecutionContext): Future[Seq[ClusterStatus]] =
    for {
      zkMap       <- zookeeperCollie.clusters()
      bkMap       <- brokerCollie.clusters()
      wkMap       <- workerCollie.clusters()
      streamMap   <- streamCollie.clusters()
      shabondiMap <- shabondiCollie.clusters()
    } yield zkMap ++ bkMap ++ wkMap ++ streamMap ++ shabondiMap

  /**
    * list the docker images hosted by nodes
    * @return the images stored by each node
    */
  def imageNames()(implicit executionContext: ExecutionContext): Future[Map[String, Seq[String]]] =
    containerClient.imageNames()

  /**
    * Verify the node are available to be used in collie.
    * @param node validated node
    * @param executionContext thread pool
    * @return succeed report in string. Or try with exception
    */
  def verifyNode(node: Node)(implicit executionContext: ExecutionContext): Future[String]

  /**
    * list all containers from the hosted nodes
    * @param executionContext thread pool
    * @return active containers
    */
  def containerNames()(implicit executionContext: ExecutionContext): Future[Seq[ContainerName]] =
    containerClient.containerNames()

  /**
    * get the log of specific container name
    * @param containerName container name
    * @param executionContext thread pool
    * @return log or NoSuchElementException
    */
  def log(containerName: String, sinceSeconds: Option[Long])(
    implicit executionContext: ExecutionContext
  ): Future[Map[ContainerName, String]] = containerClient.log(containerName, sinceSeconds)

  /**
    * Fetch the available hardware resources of hosted nodes.
    * Noted: the different collie may return different resources. The caller should NOT assume the content of the
    * resources.
    *
    * @param executionContext thread pool
    * @return hardware resources of all hosted nodes
    */
  def resources()(implicit executionContext: ExecutionContext): Future[Map[String, Seq[Resource]]] =
    containerClient.resources()

  private[this] def classNames(reflections: Reflections): ClassNames = {
    def fetch(clz: Class[_]): Set[String] =
      try
      // classOf[SubTypesScanner].getSimpleName is hard-code since Reflections does not expose it ...
      reflections.getStore.getAll(classOf[SubTypesScanner], clz.getName).asScala.toSet
      catch {
        case _: Throwable => Set.empty
      }
    new ClassNames(
      sources = fetch(classOf[RowSourceConnector]),
      sinks = fetch(classOf[RowSinkConnector]),
      partitioners = fetch(classOf[RowPartitioner]),
      streams = fetch(classOf[Stream])
    )
  }

  /**
    * load the definitions from input urls. Noted, the default implementation invokes an new class loader to resolve all
    * classes from input urls. If the input urls reference to non-uber jars, the CNF exception may happend and the classes
    * is skipped.
    * @param urls urls to load
    * @param executionContext thread pool
    * @return classes information
    */
  def fileContent(urls: Seq[URL])(implicit executionContext: ExecutionContext): Future[FileContent] = Future {
    val reflections =
      new Reflections(new ConfigurationBuilder().addUrls(urls.asJava).addClassLoader(new URLClassLoader(urls.toArray)))
    if (classNames(reflections).all.isEmpty) FileContent.empty
    else {
      def settingDefinitions[T <: WithDefinitions](clz: Class[T]) =
        try Some(clz.getName -> clz.getDeclaredConstructor().newInstance().settingDefinitions().values().asScala.toSeq)
        catch {
          case e: Throwable =>
            ServiceCollie.LOG.warn(s"failed to load class:${clz.getName}", e)
            None
        }

      def seek[T <: WithDefinitions](clz: Class[T]) =
        reflections
          .getSubTypesOf(clz)
          .asScala
          .filterNot(clz => Modifier.isAbstract(clz.getModifiers))
          .flatMap(settingDefinitions(_))

      FileContent(
        (seek(classOf[RowSourceConnector]) ++
          seek(classOf[RowSinkConnector]) ++
          seek(classOf[Stream]) ++
          seek(classOf[RowPartitioner])).map {
          case (name, settingDefinitions) => ClassInfo(name, settingDefinitions)
        }.toSeq
      )
    }
  }

  /**
    * generate the plain name for ohara volume
    * @param key volume key
    * @return plain volume name
    */
  private[this] def volumeName(key: ObjectKey): String = s"${key.toPlain}-${CommonUtils.randomString(5)}"

  /**
    * parse the plain string to ohara key. If the volume is not controlled by ohara, the key is EMPTY.
    * @param plain plain volume name
    * @return ohara key or nothing
    */
  private[this] def volumeKey(plain: String): Option[ObjectKey] = {
    val last = plain.lastIndexOf("-")
    if (last < 0 || last >= plain.length) None
    else ObjectKey.ofPlain(plain.substring(0, last)).asScala
  }

  /**
    * create volumes on specify nodes. If the node has volume already, it is no-op.
    * @param key volume key
    * @param nodeNames node names
    * @param executionContext thread pool
    * @return async call with unit. Otherwise, a exception is in the call
    */
  final def createLocalVolumes(key: ObjectKey, path: String, nodeNames: Set[String])(
    implicit executionContext: ExecutionContext
  ): Future[Unit] =
    Future
      .traverse(nodeNames)(
        nodeName =>
          containerClient.volumeCreator
            .nodeName(nodeName)
            .path(path)
            .name(volumeName(key))
            .create()
      )
      .map(_ => ())

  /**
    * list all volumes
    * @param executionContext thread pool
    * @return volumes
    */
  final def volumes()(implicit executionContext: ExecutionContext): Future[Seq[ClusterVolume]] =
    containerClient
      .volumes()
      .map(_.flatMap { volume =>
        volumeKey(volume.name) match {
          case None => None
          case Some(key) =>
            Some(
              ClusterVolume(
                group = key.group(),
                name = key.name(),
                path = volume.path,
                driver = volume.driver,
                state = None,
                error = None,
                nodeNames = Set(volume.nodeName)
              )
            )
        }
      })
      .map(_.groupBy(_.key).map {
        case (key, volumes) =>
          val drivers = volumes.map(_.driver).toSet
          val paths   = volumes.map(_.driver).toSet
          val error =
            if (drivers.size != 1)
              Some(s"the driver is not consistent. ${volumes.map(v => s"${v.key} has driver:${v.driver}")}")
            else if (paths.size != 1)
              Some(s"the path is not consistent. ${volumes.map(v => s"${v.key} has path:${v.path}")}")
            else None
          ClusterVolume(
            group = key.group(),
            name = key.name(),
            path = paths.head,
            driver = drivers.head,
            state = if (error.isEmpty) Some(VolumeState.RUNNING) else None,
            error = error,
            nodeNames = volumes.flatMap(_.nodeNames).toSet
          )
      }.toSeq)

  /**
    * remove volumes from nodes. If the node does not have volume, it is no-op.
    * @param key volume key
    * @param executionContext thread pool
    * @return async call with unit. Otherwise, a exception is in the call
    */
  final def removeVolumes(key: ObjectKey)(implicit executionContext: ExecutionContext): Future[Unit] =
    containerClient
      .volumes()
      .map(
        _.filter(
          v =>
            volumeKey(v.name) match {
              case None            => false
              case Some(volumeKey) => volumeKey == key
            }
        )
      )
      .flatMap(Future.traverse(_)(v => containerClient.removeVolumes(v.name)))
      .map(_ => ())
}

object ServiceCollie {
  val LOG: Logger = Logger(classOf[ServiceCollie])

  /**
    * the default implementation uses ssh and docker command to manage all clusters.
    * Each node running the service has name "{clusterName}-{service}-{index}".
    * For example, there is a worker cluster called "workercluster" and it is run on 3 nodes.
    * node-0 => workercluster-worker-0
    * node-1 => workercluster-worker-1
    * node-2 => workercluster-worker-2
    */
  def dockerModeBuilder: DockerModeBuilder = new DockerModeBuilder

  import scala.concurrent.duration._

  class DockerModeBuilder private[ServiceCollie] extends Builder[ServiceCollie] {
    private[this] var dataCollie: DataCollie           = _
    private[this] var cacheTimeout: Duration           = 3 seconds
    private[this] var cacheThreadPool: ExecutorService = _

    def dataCollie(dataCollie: DataCollie): DockerModeBuilder = {
      this.dataCollie = Objects.requireNonNull(dataCollie)
      this
    }

    @Optional("default is 3 seconds")
    def cacheTimeout(cacheTimeout: Duration): DockerModeBuilder = {
      this.cacheTimeout = Objects.requireNonNull(cacheTimeout)
      this
    }

    @Optional("The initial size of default pool is equal with number of cores")
    def cacheThreadPool(cacheThreadPool: ExecutorService): DockerModeBuilder = {
      this.cacheThreadPool = Objects.requireNonNull(cacheThreadPool)
      this
    }

    /**
      * We don't return ServiceCollieImpl since it is a private implementation
      * @return
      */
    override def build: ServiceCollie = new ServiceCollieImpl(
      cacheTimeout = Objects.requireNonNull(cacheTimeout),
      dataCollie = Objects.requireNonNull(dataCollie),
      cacheThreadPool =
        if (cacheThreadPool == null) Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors())
        else cacheThreadPool
    )
  }

  /**
    * Create a builder for instantiating k8s collie.
    * Currently, the nodes in node collie must be equal to nodes which is controllable to k8s client.
    * @return builder for k8s implementation
    */
  def k8sModeBuilder: K8sModeBuilder = new K8sModeBuilder

  class K8sModeBuilder private[ServiceCollie] extends Builder[ServiceCollie] {
    private[this] var dataCollie: DataCollie = _
    private[this] var k8sClient: K8SClient   = _

    def dataCollie(dataCollie: DataCollie): K8sModeBuilder = {
      this.dataCollie = Objects.requireNonNull(dataCollie)
      this
    }

    def k8sClient(k8sClient: K8SClient): K8sModeBuilder = {
      this.k8sClient = Objects.requireNonNull(k8sClient)
      this
    }

    /**
      * We don't return ServiceCollieImpl since it is a private implementation
      * @return
      */
    override def build: ServiceCollie = new K8SServiceCollieImpl(
      dataCollie = Objects.requireNonNull(dataCollie),
      containerClient = Objects.requireNonNull(k8sClient)
    )
  }
}
