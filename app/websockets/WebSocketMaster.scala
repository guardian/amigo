package websockets

import akka.actor.{ ActorRef, Actor }
import models.BakeId

/**
 * Keeps track of which actors are interested in which bakes,
 * and forwards messages from the Play controller to the interested actors.
 */
class WebSocketMaster extends Actor {

  private var webSocketActors = Vector.empty[(BakeId, ActorRef)]

  def receive = {
    case RegisterWebSocketActor(bakeId, actor) =>
      webSocketActors = webSocketActors :+ (bakeId, actor)
    case UnregisterWebSocketActor(bakeId, actor) =>
      webSocketActors = webSocketActors.filterNot(_ == (bakeId, actor))

    case msg @ DataMessage(bakeId) =>
      val interestedActors = webSocketActors collect { case (`bakeId`, actor) => actor }
      interestedActors.foreach(_ ! msg)
  }

}
