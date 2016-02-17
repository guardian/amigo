import akka.actor.ActorRef
import models.{ AmiId, BakeId }

package object websockets {

  sealed abstract class ControlMessage
  case class RegisterWebSocketActor(bakeId: BakeId, actor: ActorRef) extends ControlMessage
  case class UnregisterWebSocketActor(bakeId: BakeId, actorRef: ActorRef) extends ControlMessage

  sealed abstract class DataMessage(val bakeId: BakeId)
  case class PackerOutput(override val bakeId: BakeId, line: String) extends DataMessage(bakeId)
  case class AmiCreated(override val bakeId: BakeId, amiId: AmiId) extends DataMessage(bakeId)
  case class PackerProcessExited(override val bakeId: BakeId, exitCode: Int) extends DataMessage(bakeId)

  object DataMessage {
    def unapply(msg: DataMessage): Option[BakeId] = Some(msg.bakeId)
  }
}
