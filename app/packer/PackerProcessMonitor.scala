package packer

import java.io.{ InputStreamReader, BufferedReader }

import event.EventBus
import event.BakeEvent.{ AmiCreated, PackerOutput, PackerProcessExited }
import models.BakeId

import scala.annotation.tailrec
import scala.concurrent.Promise
import scala.util.control.NonFatal

object PackerProcessMonitor {

  /**
   * Monitors the given Packer process and consumes its output stream.
   * Sends updates to the listener and completes the promise with the process's exit value
   */
  def monitorProcess(process: Process, exitValuePromise: Promise[Int], bakeId: BakeId, eventBus: EventBus): Unit = {
    try {
      val bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream))

      // When this returns it means the stream has closed, which means the process has exited
      processNextLine(process, bufferedReader, bakeId, eventBus)

      process.waitFor()
      val exitValue = process.exitValue()
      eventBus.publish(PackerProcessExited(bakeId, exitValue))
      exitValuePromise.trySuccess(exitValue)
    } catch {
      case NonFatal(e) => exitValuePromise.tryFailure(e)
    }
  }

  @tailrec
  private def processNextLine(process: Process, reader: BufferedReader, bakeId: BakeId, eventBus: EventBus): Unit = {
    val line = reader.readLine()
    if (line != null) {
      PackerOutputParser.parseLine(line).foreach {
        case PackerOutputParser.UserFacingOutput(message) => eventBus.publish(PackerOutput(bakeId, message))
        case PackerOutputParser.AmiCreated(amiId) => eventBus.publish(AmiCreated(bakeId, amiId))
      }
      processNextLine(process, reader, bakeId, eventBus)
    }
    // if line is null it means the stream has closed, so stop recursing and return
  }

}
