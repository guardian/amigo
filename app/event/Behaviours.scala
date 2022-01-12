package event

import akka.typed._
import akka.typed.ScalaDSL._
import data.{ BakeLogs, Bakes, Dynamo }
import event.BakeEvent._
import models.{ Bake, BakeStatus, NotificationConfig }
import play.api.libs.iteratee.Concurrent.Channel
import services.Loggable

import scala.concurrent.ExecutionContext

object Behaviours extends Loggable {

  /**
   * Initialises the child actors for each event listener
   * and then switches behaviour to `broadcastEvents`
   */
  def guardian(behaviours: Map[String, Behavior[BakeEvent]]): Behavior[BakeEvent] = Full {
    case Sig(ctx, PreStart) =>
      val eventListeners = behaviours.map {
        case (name, behavior) => ctx.spawn(behavior, name)
      }
      broadcastEvents(eventListeners)
  }

  /**
   * Broadcasts all incoming events to all event listeners
   */
  def broadcastEvents(eventListeners: Iterable[ActorRef[BakeEvent]]): Behavior[BakeEvent] = Full {
    case Msg(_, bakeEvent) =>
      for (listener <- eventListeners)
        listener ! bakeEvent
      Same
  }

  /**
   * Forwards all events to a Channel for sending as Server Sent Events
   */
  def sendToChannel(channel: Channel[BakeEvent]): Behavior[BakeEvent] = Static {
    case e: BakeEvent => channel.push(e)
  }

  val writeToLog: Behavior[BakeEvent] = Static {
    case Log(bakeId, line) => log.info(s"PACKER: $line")
    case AmiCreated(bakeId, amiId) => log.info(s"Packer created an AMI! AMI id = ${amiId.value}")
    case PackerProcessExited(bakeId, exitCode) => log.info(s"Packer process completed with exit code $exitCode")
  }

  /**
   * Writes updates to the appropriate Dynamo records and triggers bake failed notifications
   */
  def persistBakeEvent(notificationConfig: Option[NotificationConfig])(implicit dynamo: Dynamo, exec: ExecutionContext): Behavior[BakeEvent] = Static {
    case Log(bakeId, bakeLog) => BakeLogs.save(bakeLog)
    case AmiCreated(bakeId, amiId) => Bakes.updateAmiId(bakeId, amiId)
    case PackerProcessExited(bakeId, exitCode) =>
      val status = if (exitCode == 0) BakeStatus.Complete else BakeStatus.Failed
      Bake.updateStatusAndNotifyFailure(bakeId, status, notificationConfig)
  }

}

