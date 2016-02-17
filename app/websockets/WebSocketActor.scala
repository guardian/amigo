package websockets

import akka.actor.{ Actor, ActorRef }
import models.BakeId
import org.joda.time.DateTime
import play.api.libs.json.{ JsNumber, JsString, JsObject }

class WebSocketActor(bakeId: BakeId, master: ActorRef, out: ActorRef) extends Actor {

  override def preStart(): Unit = {
    master ! RegisterWebSocketActor(bakeId, self)
  }

  override def postStop(): Unit = {
    master ! UnregisterWebSocketActor(bakeId, self)
  }

  def receive = {
    case PackerOutput(_, line) => out ! JsObject(Seq(
      "eventType" -> JsString("packer-output"),
      "timestamp" -> JsString(DateTime.now().toString("YYYY-MM-dd HH:mm:ss")),
      "line" -> JsString(line)
    ))
    case AmiCreated(_, amiId) => out ! JsObject(Seq(
      "eventType" -> JsString("ami-created"),
      "amiId" -> JsString(amiId.value)
    ))
    case PackerProcessExited(_, exitCode) => out ! JsObject(Seq(
      "eventType" -> JsString("packer-process-exited"),
      "exitCode" -> JsNumber(exitCode)
    ))
  }

}
