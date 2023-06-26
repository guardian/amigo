package event

import java.util.UUID

import models.{BakeLog, AmiId, BakeId}
import play.api.libs.EventSource.{EventIdExtractor, EventDataExtractor}
import play.api.libs.json.{JsNumber, JsString, JsObject}

sealed abstract class BakeEvent(
    val bakeId: BakeId,
    val eventId: String = UUID.randomUUID().toString
)

object BakeEvent {

  case class Log(override val bakeId: BakeId, bakeLog: BakeLog)
      extends BakeEvent(bakeId)
  case class AmiCreated(override val bakeId: BakeId, amiId: AmiId)
      extends BakeEvent(bakeId)
  case class PackerProcessExited(override val bakeId: BakeId, exitCode: Int)
      extends BakeEvent(bakeId)

  def unapply(event: BakeEvent): Option[BakeId] = Some(event.bakeId)

  implicit val eventIdExtractor: EventIdExtractor[BakeEvent] =
    EventIdExtractor[BakeEvent](e => Some(e.eventId))

  implicit val eventDataExtractor: EventDataExtractor[BakeEvent] =
    EventDataExtractor[BakeEvent]({ event =>
      val json = event match {
        case Log(_, log) =>
          JsObject(
            Seq(
              "eventType" -> JsString("log"),
              "timestamp" -> JsString(
                log.timestamp.toString("YYYY-MM-dd HH:mm:ss")
              ),
              "log" -> JsObject(
                Seq(
                  "level" -> JsString(log.logLevel),
                  "number" -> JsNumber(log.logNumber),
                  "messageHtml" -> JsString(log.messageHtml.toString)
                )
              )
            )
          )
        case AmiCreated(_, amiId) =>
          JsObject(
            Seq(
              "eventType" -> JsString("ami-created"),
              "amiId" -> JsString(amiId.value)
            )
          )
        case PackerProcessExited(_, exitCode) =>
          JsObject(
            Seq(
              "eventType" -> JsString("packer-process-exited"),
              "exitCode" -> JsNumber(exitCode)
            )
          )
      }
      json.toString()
    })

}
