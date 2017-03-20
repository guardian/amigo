package prism

import play.api.Logger
import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.libs.ws.WSClient
import scala.concurrent.{ ExecutionContext, Future }

class Prism(ws: WSClient, baseUrl: String = "http://prism.gutools.co.uk")(implicit ec: ExecutionContext) {
  import Prism._

  def findAllAWSAccountNumbers(): Future[Seq[String]] = {
    findAll[SourceInstance]("/sources?resource=instance&origin.vendor=aws")
      .map(_.map(_.accountNumber))
  }

  def findAllInstances(): Future[Seq[Instance]] = findAll[Instance]("/instances")

  def findAllLaunchConfigurations(): Future[Seq[LaunchConfiguration]] = findAll[LaunchConfiguration]("/launch-configurations")

  private def findAll[T](path: String)(implicit r: Reads[Seq[T]]): Future[Seq[T]] = {
    val url = s"$baseUrl$path"
    ws.url(url).get().map { resp =>
      extractData[Seq[T]](resp.json).fold(error => {
        Logger.warn(s"Failed to parse Prism response for GET $url. Status code = ${resp.status}, Error = $error")
        Seq.empty[T]
      }, t => t)
    }
  }

}

object Prism {

  case class SourceInstance(accountNumber: String)
  case class Instance(imageId: String)
  case class LaunchConfiguration(imageId: String)

  implicit val sourceInstanceReads: Reads[SourceInstance] = (__ \ "origin" \ "accountNumber").read[String].map(SourceInstance)
  implicit val sourceInstancesReads: Reads[Seq[SourceInstance]] = dataReads[SourceInstance](dataPath = "data")
  implicit val instanceReads: Reads[Instance] = (__ \ "specification" \ "imageId").read[String].map(Instance)
  implicit val instancesReads: Reads[Seq[Instance]] = dataReads[Instance](dataPath = "data", "instances")
  implicit val launchConfigurationReads: Reads[LaunchConfiguration] = Json.reads[LaunchConfiguration]
  implicit val launchConfigurationsReads: Reads[Seq[LaunchConfiguration]] = dataReads[LaunchConfiguration](dataPath = "data", "launch-configurations")

  private def dataReads[T](dataPath: String*)(implicit r: Reads[T]): Reads[Seq[T]] =
    dataPath.foldLeft[JsPath](__)((path, subPath) => path \ subPath).read[Seq[T]]

  private[prism] def extractData[T](json: JsValue)(implicit r: Reads[T]): Either[Seq[(JsPath, Seq[ValidationError])], T] = {
    r.reads(json) match {
      case JsSuccess(data, _) => Right(data)
      case JsError(e) => Left(e)
    }
  }
}
