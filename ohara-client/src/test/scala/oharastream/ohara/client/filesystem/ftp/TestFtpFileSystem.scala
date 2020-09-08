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

package oharastream.ohara.client.filesystem.ftp

import java.io.{BufferedWriter, OutputStreamWriter}
import java.nio.file.Paths
import oharastream.ohara.client.filesystem.{FileSystem, FileSystemTestBase}
import oharastream.ohara.common.util.CommonUtils
import oharastream.ohara.testing.service.FtpServer
import org.junit.Test
import org.scalatest.matchers.should.Matchers._

import scala.jdk.CollectionConverters._

class TestFtpFileSystem extends FileSystemTestBase {
  private[this] val ftpServer = FtpServer.builder().controlPort(0).dataPorts(java.util.Arrays.asList(0, 0, 0)).build()
  private[this] val hostname  = ftpServer.hostname
  private[this] val port      = ftpServer.port
  private[this] val user      = ftpServer.user
  private[this] val password  = ftpServer.password

  override protected val fileSystem: FileSystem =
    FileSystem.ftpBuilder.hostname(hostname).port(port).user(user).password(password).build

  override protected val rootDir: String = "/root"

  @Test
  def testCloseNonEofInputStream(): Unit = {
    val inputFolder = s"/${CommonUtils.randomString(10)}"
    val header      = "column1,column2,column3,column4,column5,column6,column7,column8,column9,column10"
    val data = (1 to 100000)
      .map(i => Seq(s"a-$i", s"b-$i", s"c-$i", s"d-$i", s"e-$i", s"f-$i", s"g-$i", s"h-$i", s"i-$i", s"j-$i"))
      .map(_.mkString(","))
    val writer = new BufferedWriter(
      new OutputStreamWriter(fileSystem.create(CommonUtils.path(inputFolder, s"${CommonUtils.randomString(8)}.csv")))
    )
    try {
      writer.append(header)
      writer.newLine()
      data.foreach { line =>
        writer.append(line)
        writer.newLine()
      }
    } finally writer.close()

    val files = fileSystem.listFileNames(inputFolder).asScala.toSeq
    files.size shouldBe 1
    val input = fileSystem.open(Paths.get(inputFolder, files.head).toString)
    try {
      val buffer = new Array[Byte](1024)
      input.read(buffer) should be > 0
    } finally input.close()
  }
}
