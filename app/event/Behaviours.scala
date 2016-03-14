package event

import akka.typed._
import akka.typed.ScalaDSL._
import data.{ BakeLogs, Dynamo }
import event.BakeEvent._
import play.api.Logger
import play.api.libs.iteratee.Concurrent.Channel

object Behaviours {

  /**
   * Initialises the child actors for each event listener
   * and then switches behaviour to `broadcastEvents`
   */
  def guardian(eventListenerProps: Map[String, Props[BakeEvent]]): Behavior[BakeEvent] = Full {
    case Sig(ctx, PreStart) =>
      val eventListeners = eventListenerProps.map {
        case (name, props) => ctx.spawn(props, name)
      }
      broadcastEvents(eventListeners)
  }

  /**
   * Broadcasts all incoming events to all event listeners
   * and handles child failures by restarting the child
   */
  def broadcastEvents(eventListeners: Iterable[ActorRef[BakeEvent]]): Behavior[BakeEvent] = Full {
    case Msg(_, bakeEvent) =>
      for (listener <- eventListeners)
        listener ! bakeEvent
      Same
    case Sig(_, f @ Failed(cause, child)) =>
      f.decide(Failed.Restart)
      Same
  }

  /**
   * Forwards all events to a Channel for sending as Server Sent Events
   */
  def sendToChannel(channel: Channel[BakeEvent]): Behavior[BakeEvent] = Static {
    case e: BakeEvent => channel.push(e)
  }

  val writeToLog: Behavior[BakeEvent] = Static {
    case Log(bakeId, line) => Logger.info(s"PACKER: $line")
    case AmiCreated(bakeId, amiId) => Logger.info(s"Packer created an AMI! AMI id = ${amiId.value}")
    case PackerProcessExited(bakeId, exitCode) => Logger.info(s"Packer process completed with exit code $exitCode")
  }

  /**
    * Writes updates to the appropriate Dynamo records
    */
  def writeToDynamo(implicit dynamo: Dynamo): Behavior[BakeEvent] = Static {
    case Log(bakeId, bakeLog) => BakeLogs.save(bakeLog)
    case AmiCreated(bakeId, amiId) => // TODO
    case PackerProcessExited(bakeId, exitCode) => // TODO
  }

}

