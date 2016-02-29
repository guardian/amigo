package event

import java.util.UUID

import models.{ AmiId, BakeId }
import org.joda.time.DateTime
import play.api.libs.EventSource.{ EventIdExtractor, EventDataExtractor }
import play.api.libs.json.{ JsNumber, JsString, JsObject }

sealed abstract class BakeEvent(val bakeId: BakeId, val eventId: String = UUID.randomUUID().toString)

object BakeEvent {

  case class PackerOutput(override val bakeId: BakeId, line: String) extends BakeEvent(bakeId)
  case class AmiCreated(override val bakeId: BakeId, amiId: AmiId) extends BakeEvent(bakeId)
  case class PackerProcessExited(override val bakeId: BakeId, exitCode: Int) extends BakeEvent(bakeId)

  def unapply(event: BakeEvent): Option[BakeId] = Some(event.bakeId)

  implicit val eventIdExtractor = EventIdExtractor[BakeEvent](e => Some(e.eventId))

  implicit val eventDataExtractor = EventDataExtractor[BakeEvent]({ event =>
    val json = event match {
      case PackerOutput(_, line) => JsObject(Seq(
        "eventType" -> JsString("packer-output"),
        "timestamp" -> JsString(DateTime.now().toString("YYYY-MM-dd HH:mm:ss")),
        "line" -> JsString(line)
      ))
      case AmiCreated(_, amiId) => JsObject(Seq(
        "eventType" -> JsString("ami-created"),
        "amiId" -> JsString(amiId.value)
      ))
      case PackerProcessExited(_, exitCode) => JsObject(Seq(
        "eventType" -> JsString("packer-process-exited"),
        "exitCode" -> JsNumber(exitCode)
      ))
    }
    json.toString()
  })

}
