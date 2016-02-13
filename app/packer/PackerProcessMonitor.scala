package packer

import java.io.{ InputStreamReader, BufferedReader }

import scala.annotation.tailrec
import scala.concurrent.{ Promise, Future }

object PackerProcessMonitor {

  /**
   * Monitors the given Packer process and consumes its output stream.
   * Sends updates to the listener and returns a Future of the process's exit value
   *
   * @param process
   * @param listener
   * @return
   */
  def monitorProcess(process: Process, listener: PackerListener): Future[Int] = {
    val promise = Promise[Int]()
    val bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream))
    processNextLine(process, bufferedReader, listener, promise)
    promise.future
  }

  @tailrec
  private def processNextLine(process: Process, reader: BufferedReader, listener: PackerListener, promise: Promise[Int]): Unit = {
    // TODO catch IO exceptions -> fail the promise
    val line = reader.readLine()
    if (line == null) {
      // stream has closed, meaning that process has exited
      process.waitFor()
      val exitValue = process.exitValue()
      listener.onProcessExited(exitValue)
      promise.success(exitValue)
    } else {
      // TODO parse output, check for AMI creation, etc.
      listener.onLineOfOutput(line)
      processNextLine(process, reader, listener, promise)
    }
  }

}
