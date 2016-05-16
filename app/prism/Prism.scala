package prism

import play.api.Logger
import play.api.libs.json.{ JsValue, JsError, JsSuccess, Json }
import play.api.libs.ws.WSClient
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class Prism(ws: WSClient) {
  import Prism._

  private val PrismUrl = "http://prism.gutools.co.uk/sources?resource=instance&origin.vendor=aws"

  def findAllAWSAccountNumbers(): Future[Seq[String]] = {
    ws.url(PrismUrl).get().map { resp =>
      extractAccountNumbers(resp.json) getOrElse {
        Logger.warn(s"Failed to parse Prism response. Status code = ${resp.status}, Body = ${resp.body}")
        Nil
      }
    }
  }

}

object Prism {

  case class Origin(accountNumber: String)
  implicit val originReads = Json.reads[Origin]

  case class Instance(origin: Origin)
  implicit val instanceReads = Json.reads[Instance]

  case class PrismResponse(data: Seq[Instance])
  implicit val prismResponseReads = Json.reads[PrismResponse]

  private[prism] def extractAccountNumbers(json: JsValue): Option[Seq[String]] = {
    json.validate[PrismResponse] match {
      case JsSuccess(prismResponse, _) => Some(prismResponse.data.map(_.origin.accountNumber))
      case JsError(_) => None
    }
  }

}
