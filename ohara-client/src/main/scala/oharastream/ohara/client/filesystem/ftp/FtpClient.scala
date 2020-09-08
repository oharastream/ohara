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

import java.io._
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Files
import java.util.Objects
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.Logger
import oharastream.ohara.common.annotations.Optional
import oharastream.ohara.common.exception.NoSuchFileException
import oharastream.ohara.common.util.{CommonUtils, Releasable}
import oharastream.ohara.kafka.connector.storage.FileType
import org.apache.commons.net.ftp.{FTP, FTPClient, FTPReply}

import scala.concurrent.duration.Duration

/**
  * A general interface from ftp file system.
  * NOTED: FtpClient doesn't extend ReleaseOnce since it is a "retryable" class which do close-and-then-reconnect
  * internally. Hence, FtpClient MAY close itself many times and re-build the connection.
  */
trait FtpClient extends Releasable {
  def listFileNames(dir: String): Seq[String]

  /**
    * open an input stream from a existent file. If file doesn't exist, an NoSuchFileException will be thrown.
    * @param path file path
    * @return input stream
    */
  def open(path: String): InputStream

  /**
    * create an new file. If file already exists, an IllegalArgumentException will be thrown.
    * @param path file path
    * @return file output stream
    */
  def create(path: String): OutputStream

  /**
    * create output stream from an existent file. If file doesn't exist, an NoSuchFileException will be thrown.
    * @param path file path
    * @return file output stream
    */
  def append(path: String): OutputStream

  def moveFile(from: String, to: String): Unit

  /**
    * create folder. It throw exception if there is already a folder
    * @param path folder path
    */
  def mkdir(path: String): Unit

  /**
    * recreate a folder. It will delete all stuff under the path.
    * @param path folder path
    */
  def reMkdir(path: String): Unit = {
    if (exist(path)) delete(path)
    mkdir(path)
  }

  def delete(path: String): Unit

  def delete(path: String, recursive: Boolean): Unit

  /**
    * append message to the end from file. If the file doesn't exist, it will create an new file.
    * @param path file path
    * @param message message
    */
  def attach(path: String, message: String): Unit = attach(path, Seq(message))

  /**
    * append messages to the end from file. If the file doesn't exist, it will create an new file.
    * @param path file path
    * @param messages messages
    */
  def attach(path: String, messages: Seq[String]): Unit = {
    val writer = new BufferedWriter(
      new OutputStreamWriter(if (exist(path)) append(path) else create(path), StandardCharsets.UTF_8),
      messages.map(_.length).sum * 2
    )
    try messages.foreach(line => {
      writer.append(line)
      writer.newLine()
    })
    finally writer.close()
  }

  /**
    * read all content from the path.
    * @param path file path
    * @param encode encode (UTF-8 is default)
    * @return an array from lines
    */
  def readLines(path: String, encode: String = "UTF-8"): Array[String] = {
    val reader = new BufferedReader(new InputStreamReader(open(path), Charset.forName(encode)))
    try Iterator.continually(reader.readLine()).takeWhile(_ != null).toArray
    finally reader.close()
  }

  def upload(path: String, file: File): Unit = upload(path, Files.readAllBytes(file.toPath))

  def upload(path: String, data: Array[Byte]): Unit = {
    val output = create(path)
    try output.write(data, 0, data.length)
    finally output.close()
  }

  def download(path: String): Array[Byte] = {
    val input = open(path)
    try {
      val output = new ByteArrayOutputStream()
      try {
        val buf = new Array[Byte](128)
        Iterator.continually(input.read(buf)).takeWhile(_ > 0).foreach(output.write(buf, 0, _))
        output.toByteArray
      } finally output.close()
    } finally input.close()
  }

  /**
    * @return temporary folder of ftp server
    */
  def tmpFolder(): String

  /**
    * @param path file path
    * @return true if path points to a existent file
    */
  def exist(path: String): Boolean

  /**
    * @param path file path
    * @return false if path points to a existent file
    */
  def nonExist(path: String): Boolean = !exist(path)

  /**
    * @param path file path
    * @return type of file
    */
  def fileType(path: String): FileType

  /**
    * @return ftp server's status
    */
  def status(): String

  /**
    * Get the working folder of account. An exception will be thrown if it fails to get working folder.
    * @return current working folder
    */
  def workingFolder(): String
}

object FtpClient {
  private[this] val LOG = Logger(classOf[FtpClient])
  def builder: Builder  = new Builder

  class Builder private[FtpClient] extends oharastream.ohara.common.pattern.Builder[FtpClient] {
    private[this] var hostname: String = _

    /**
      * port 21 is used by ftp as default
      */
    private[this] var port: Int              = 21
    private[this] var user: String           = _
    private[this] var password: String       = _
    private[this] var retryTimeout: Duration = Duration(0, TimeUnit.SECONDS)
    private[this] var retryBackoff: Duration = Duration(1, TimeUnit.SECONDS)

    /**
      * ftp server's hostname
      * @return this builder
      */
    def hostname(hostname: String): Builder = {
      this.hostname = CommonUtils.requireNonEmpty(hostname)
      this
    }

    /**
      * ftp server's port
      * @return this builder
      */
    @Optional("default value is 21")
    def port(port: Int): Builder = {
      this.port = CommonUtils.requireConnectionPort(port)
      this
    }

    /**
      * a accessible user
      * @return this builder
      */
    def user(user: String): Builder = {
      this.user = CommonUtils.requireNonEmpty(user)
      this
    }

    /**
      * a accessible password
      * @return this builder
      */
    def password(password: String): Builder = {
      this.password = CommonUtils.requireNonEmpty(password)
      this
    }

    /**
      * disable the retry in connecting to ftp server.
      * @return this builder
      */
    def disableRetry(): Builder = retryTimeout(Duration(0, TimeUnit.SECONDS))

    /**
      * timeout of retrying connection to ftp
      * @param retryTimeout retry timeout
      * @return this builder
      */
    @Optional("default value is zero")
    def retryTimeout(retryTimeout: Duration): Builder = {
      this.retryTimeout = Objects.requireNonNull(retryTimeout)
      this
    }

    /**
      * the time to sleep before retrying
      * @param retryBackoff time to backoff
      * @return this builder
      */
    @Optional("default value is 1 second")
    def retryBackoff(retryBackoff: Duration): Builder = {
      this.retryBackoff = Objects.requireNonNull(retryBackoff)
      this
    }

    override def build: FtpClient = {
      new FtpClient {
        private[this] val hostname =
          CommonUtils.requireNonEmpty(Builder.this.hostname, () => "hostname can't be null or empty")
        private[this] val port = CommonUtils.requireConnectionPort(Builder.this.port)
        private[this] val user =
          CommonUtils.requireNonEmpty(Builder.this.user, () => "user can't be null or empty")
        private[this] val password =
          CommonUtils.requireNonEmpty(Builder.this.password, () => "password can't be null or empty")
        private[this] val retryTimeout       = Objects.requireNonNull(Builder.this.retryTimeout)
        private[this] val retryBackoff       = Objects.requireNonNull(Builder.this.retryBackoff)
        private[this] var _client: FtpClient = _
        private[this] def client(): FtpClient = {
          if (_client == null)
            _client = new FtpClientImpl(
              hostname = hostname,
              port = port,
              user = user,
              password = password
            )
          _client
        }
        private[this] def retry[T](function: () => T): T = {
          var lastException: Throwable = null
          val endTime                  = CommonUtils.current() + retryTimeout.toMillis
          do {
            try return function()
            catch {
              case e: Throwable =>
                LOG.info(s"failed to execute ftp command. timeout of retry: $retryTimeout. backoff:$retryBackoff", e)
                lastException = e
                TimeUnit.MILLISECONDS.sleep(retryBackoff.toMillis)
            }
          } while (endTime >= CommonUtils.current())
          Releasable.close(_client)
          _client = null
          if (lastException != null) throw lastException
          else throw new IllegalArgumentException("still fail...but there is no root cause ...")
        }
        override def listFileNames(dir: String): Seq[String]        = retry(() => client().listFileNames(dir))
        override def open(path: String): InputStream                = retry(() => client().open(path))
        override def create(path: String): OutputStream             = retry(() => client().create(path))
        override def append(path: String): OutputStream             = retry(() => client().append(path))
        override def moveFile(from: String, to: String): Unit       = retry(() => client().moveFile(from, to))
        override def mkdir(path: String): Unit                      = retry(() => client().mkdir(path))
        override def delete(path: String): Unit                     = retry(() => client().delete(path))
        override def delete(path: String, recursive: Boolean): Unit = retry(() => client().delete(path, recursive))
        override def tmpFolder(): String                            = client().tmpFolder()
        override def exist(path: String): Boolean                   = retry(() => client().exist(path))
        override def fileType(path: String): FileType               = retry(() => client().fileType(path))
        override def status(): String                               = retry(() => client().status())
        override def workingFolder(): String                        = retry(() => client().workingFolder())
        override def close(): Unit                                  = client().close()
      }
    }

    private[this] class FtpClientImpl(hostname: String, port: Int, user: String, password: String) extends FtpClient {
      private[this] var _client: FTPClient = _

      private[this] def connectIfNeeded(): FTPClient =
        if (connected) _client
        else {
          if (_client == null) _client = new FTPClient
          _client.connect(hostname, port)
          _client.enterLocalPassiveMode()

          /**
            * apache FTPClient, by default, disallow ftp server to use different address in control and data connection.
            * However, in container world, it is easy that ftp server use another address in passive mode.
            * Hence, we disable this check by default ...
            */
          _client.setRemoteVerificationEnabled(false)
          if (!_client.login(user, password))
            throw new IllegalArgumentException(s"fail to login ftp server:$hostname by account:$user")
          _client
        }

      private[this] def connected: Boolean = _client != null && _client.isConnected

      override def listFileNames(dir: String): Seq[String] = connectIfNeeded().listFiles(dir).map(_.getName).toSeq

      override def open(path: String): InputStream = {
        val client = connectIfNeeded()
        client.setFileType(FTP.BINARY_FILE_TYPE)
        if (nonExist(path)) throw new NoSuchFileException(s"$path doesn't exist")
        val inputStream = client.retrieveFileStream(path)
        if (inputStream == null)
          throw new IllegalStateException(s"Failed to open $path because from ${client.getReplyCode}")
        new InputStream {
          override def read(): Int = inputStream.read()

          override def available(): Int = inputStream.available()

          override def close(): Unit =
            try inputStream.close()
            finally if (client != null) {
              val reply = client.getReply
              // 426 reply means the data connection is unexpectedly closed before the completion of a data transfer
              // this is a false error as closing a input stream before eof can cause this error.
              // our ftp connector has a limit of read buffer so the input stream get closed early.
              if (reply != 426 && !FTPReply.isPositiveCompletion(reply))
                throw new IllegalStateException("Failed to complete pending command")
            }

          override def mark(readlimit: Int): Unit = inputStream.mark(readlimit)

          override def markSupported(): Boolean = inputStream.markSupported()

          override def read(b: Array[Byte]): Int = inputStream.read(b)

          override def read(b: Array[Byte], off: Int, len: Int): Int = inputStream.read(b, off, len)

          override def reset(): Unit = inputStream.reset()

          override def skip(n: Long): Long = inputStream.skip(n)

          override def equals(obj: scala.Any): Boolean = inputStream.equals(obj)

          override def hashCode(): Int = inputStream.hashCode()

          override def toString: String = inputStream.toString
        }
      }

      override def moveFile(from: String, to: String): Unit =
        if (!connectIfNeeded().rename(from, to))
          throw new IllegalStateException(s"Failed to move file from $from to $to")

      override def mkdir(path: String): Unit = {
        val client = connectIfNeeded()
        if (!client.changeWorkingDirectory(path) && !client.makeDirectory(path))
          throw new IllegalStateException(s"Failed to create folder on $path")
      }

      override def close(): Unit = if (connected) {
        _client.logout()
        _client.disconnect()
        _client = null
      }

      override def create(path: String): OutputStream = {
        val client = connectIfNeeded()
        client.setFileType(FTP.BINARY_FILE_TYPE)
        if (exist(path)) throw new IllegalArgumentException(s"$path exists")
        val outputStream = client.storeFileStream(path)
        if (outputStream == null)
          throw new IllegalStateException(s"Failed to create $path because from ${client.getReplyCode}")
        new OutputStream {
          override def write(b: Int): Unit = outputStream.write(b)

          override def close(): Unit = {
            outputStream.close()
            if (!client.completePendingCommand()) throw new IllegalStateException("Failed to complete pending command")
          }

          override def flush(): Unit = outputStream.flush()

          override def write(b: Array[Byte]): Unit = outputStream.write(b)

          override def write(b: Array[Byte], off: Int, len: Int): Unit = outputStream.write(b, off, len)

          override def equals(obj: scala.Any): Boolean = outputStream.equals(obj)

          override def hashCode(): Int = outputStream.hashCode()

          override def toString: String = outputStream.toString
        }
      }

      override def append(path: String): OutputStream = {
        val client = connectIfNeeded()
        client.setFileType(FTP.BINARY_FILE_TYPE)
        if (nonExist(path)) throw new NoSuchFileException(s"$path doesn't exist")
        val outputStream = client.appendFileStream(path)
        if (outputStream == null)
          throw new IllegalStateException(s"Failed to create $path because from ${client.getReplyCode}")
        new OutputStream {
          override def write(b: Int): Unit = outputStream.write(b)

          override def close(): Unit = {
            outputStream.close()
            if (!client.completePendingCommand()) throw new IllegalStateException("Failed to complete pending command")
          }

          override def flush(): Unit = outputStream.flush()

          override def write(b: Array[Byte]): Unit = outputStream.write(b)

          override def write(b: Array[Byte], off: Int, len: Int): Unit = outputStream.write(b, off, len)

          override def equals(obj: scala.Any): Boolean = outputStream.equals(obj)

          override def hashCode(): Int = outputStream.hashCode()

          override def toString: String = outputStream.toString
        }
      }

      override def delete(path: String): Unit = fileType(path) match {
        case FileType.FILE =>
          val client = connectIfNeeded()
          if (!client.deleteFile(path))
            throw new IllegalStateException(s"failed to delete $path because from ${client.getReplyCode}")
        case FileType.FOLDER =>
          val client = connectIfNeeded()
          if (!client.removeDirectory(path))
            throw new IllegalStateException(s"failed to delete $path because from ${client.getReplyCode}")
      }

      override def delete(path: String, recursive: Boolean) = {
        if (recursive) {
          if (fileType(path) == FileType.FOLDER)
            listFileNames(path).foreach(fileName => {
              val child = CommonUtils.path(path, fileName)
              delete(child, recursive)
            })
          delete(path)
        } else delete(path)
      }

      override def tmpFolder(): String = {
        connectIfNeeded()
        "/tmp"
      }
      override def exist(path: String): Boolean = {
        val client = connectIfNeeded()
        val result = client.getStatus(path)
        // different ftp implementations have different return value...
        if (result == null) false
        else {
          // if path references to folder, some ftp servers return "212-"
          if (result.startsWith("212-")) true
          else
            result.contains(CommonUtils.name(path)) || // if path references to file, result will show the meta from files
            result.contains(CommonUtils.name(".."))    // if path references to folder, result will show meta from all files with "." and "..
        }
      }

      override def fileType(path: String): FileType =
        if (exist(path)) {
          val client = connectIfNeeded()
          // cache current working folder
          val current = client.printWorkingDirectory()
          try client.cwd(path) match {
            case 250 => FileType.FOLDER
            case _   => FileType.FILE
          } finally client.cwd(current)
        } else throw new NoSuchFileException(s"$path doesn't exist")

      override def status(): String = connectIfNeeded().getStatus

      override def workingFolder(): String =
        Option(connectIfNeeded().printWorkingDirectory())
          .getOrElse(throw new IllegalStateException(s"failed to get working folder for account:$user"))
    }
  }
}
