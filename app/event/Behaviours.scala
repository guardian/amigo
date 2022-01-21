package event

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior, SupervisorStrategy }
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
  def guardian(behaviours: Map[String, Behavior[BakeEvent]]): Behavior[BakeEvent] = Behaviors.setup { context =>
    val eventListeners = behaviours.map {
      case (name, behavior) => context.spawn(automaticallyRestart(behavior), name)
    }
    broadcastEvents(eventListeners)
  }

  /**
   * Broadcasts all incoming events to all event listeners
   */
  def broadcastEvents(eventListeners: Iterable[ActorRef[BakeEvent]]): Behavior[BakeEvent] = Behaviors.receiveMessage[BakeEvent] { bakeEvent =>
    for (listener <- eventListeners)
      listener ! bakeEvent
    Behaviors.same
  }

  /**
    * Ensures that child actors are restarted after encountering an exception
    * For more details, see: https://doc.akka.io/docs/akka/2.5/typed/fault-tolerance.html
    */
  def automaticallyRestart(behavior: Behavior[BakeEvent]): Behavior[BakeEvent] = Behaviors.supervise(behavior).onFailure(SupervisorStrategy.restart)

  /**
   * Forwards all events to a Channel for sending as Server Sent Events
   */
  def sendToChannel(channel: Channel[BakeEvent]): Behavior[BakeEvent] = Behaviors.receiveMessage[BakeEvent] {
    event =>
      channel.push(event)
      Behaviors.same
  }

  /**
   * Forwards all Packer-related logging to Amigo's application logs
   */
  val writeToLog: Behavior[BakeEvent] = Behaviors.receiveMessage[BakeEvent] { message =>
    message match {
      case Log(_, line) => log.info(s"PACKER: $line")
      case AmiCreated(_, amiId) => log.info(s"Packer created an AMI! AMI id = ${amiId.value}")
      case PackerProcessExited(_, exitCode) => log.info(s"Packer process completed with exit code $exitCode")
    }
    Behaviors.same
  }

  /**
   * Writes updates to the appropriate Dynamo records and triggers bake failed notifications
   */
  def persistBakeEvent(notificationConfig: Option[NotificationConfig])(implicit dynamo: Dynamo, exec: ExecutionContext): Behavior[BakeEvent] =
    Behaviors.receiveMessage[BakeEvent] { message =>
      message match {
        case Log(_, bakeLog) => BakeLogs.save(bakeLog)
        case AmiCreated(bakeId, amiId) => Bakes.updateAmiId(bakeId, amiId)
        case PackerProcessExited(bakeId, exitCode) =>
          val status = if (exitCode == 0) BakeStatus.Complete else BakeStatus.Failed
          Bake.updateStatusAndNotifyFailure(bakeId, status, notificationConfig)
      }
      Behaviors.same
    }

}

