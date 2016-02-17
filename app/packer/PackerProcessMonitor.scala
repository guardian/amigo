package packer

import java.io.{ InputStreamReader, BufferedReader }

import scala.annotation.tailrec
import scala.concurrent.{ Promise, Future }
import scala.util.control.NonFatal

object PackerProcessMonitor {

  /**
   * Monitors the given Packer process and consumes its output stream.
   * Sends updates to the listener and completes the promise with the process's exit value
   */
  def monitorProcess(process: Process, exitValuePromise: Promise[Int], listener: PackerListener): Unit = {
    try {
      val bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream))

      // When this returns it means the stream has closed, which means the process has exited
      processNextLine(process, bufferedReader, listener, exitValuePromise)

      process.waitFor()
      val exitValue = process.exitValue()
      listener.onProcessExited(exitValue)
      exitValuePromise.trySuccess(exitValue)
    } catch {
      case NonFatal(e) => exitValuePromise.tryFailure(e)
    }
  }

  @tailrec
  private def processNextLine(process: Process, reader: BufferedReader, listener: PackerListener, promise: Promise[Int]): Unit = {
    val line = reader.readLine()
    if (line != null) {
      PackerOutputParser.parseLine(line).foreach {
        case PackerOutputParser.UserFacingOutput(message) => listener.onLineOfOutput(message)
        case PackerOutputParser.AmiCreated(amiId) => listener.onAmiCreated(amiId)
      }
      processNextLine(process, reader, listener, promise)
    }
    // if line is null it means the stream has closed, so stop recursing and return
  }

}
