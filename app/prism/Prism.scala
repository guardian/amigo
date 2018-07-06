package prism

import play.api.Logger
import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.libs.ws.WSClient
import scala.concurrent.{ ExecutionContext, Future }

class Prism(ws: WSClient, val baseUrl: String = "https://prism.gutools.co.uk")(implicit ec: ExecutionContext) {
  import Prism._

  def findAllAWSAccounts(): Future[Seq[AWSAccount]] = {
    findAll[AWSAccount]("/sources?resource=instance&origin.vendor=aws")
  }

  def findAllInstances(): Future[Seq[Instance]] = findAll[Instance]("/instances")

  def findAllLaunchConfigurations(): Future[Seq[LaunchConfiguration]] = findAll[LaunchConfiguration]("/launch-configurations")

  def findCopiedImages(): Future[Seq[Image]] = findAll[Image]("/images?tags.CopiedFromAMI!=")

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
  case class AWSAccount(accountName: String, accountNumber: String)
  case class Instance(name: String, imageId: String, awsAccount: AWSAccount)
  case class LaunchConfiguration(name: String, imageId: String, awsAccount: AWSAccount)
  case class Image(imageId: String, ownerId: String, copiedFromAMI: String, encrypted: Option[String], state: String)

  import play.api.libs.functional.syntax._
  implicit val sourceInstanceReads: Reads[AWSAccount] = (
    (JsPath \ "origin" \ "accountName").read[String] and
    (JsPath \ "origin" \ "accountNumber").read[String]
  )(AWSAccount.apply _)
  implicit val sourceInstancesReads: Reads[Seq[AWSAccount]] = dataReads[AWSAccount](dataPath = "data")
  implicit val instanceReads: Reads[Instance] = (
    (JsPath \ "instanceName").read[String] and
    (JsPath \ "specification" \ "imageId").read[String] and
    (JsPath \ "meta").read[AWSAccount]
  )(Instance.apply _)
  implicit val instancesReads: Reads[Seq[Instance]] = dataReads[Instance](dataPath = "data", "instances")
  implicit val launchConfigurationReads: Reads[LaunchConfiguration] = (
    (JsPath \ "name").read[String] and
    (JsPath \ "imageId").read[String] and
    (JsPath \ "meta").read[AWSAccount]
  )(LaunchConfiguration.apply _)
  implicit val launchConfigurationsReads: Reads[Seq[LaunchConfiguration]] = dataReads[LaunchConfiguration](dataPath = "data", "launch-configurations")
  implicit val imageReads: Reads[Image] = (
    (JsPath \ "imageId").read[String] and
    (JsPath \ "ownerId").read[String] and
    (JsPath \ "tags" \ "CopiedFromAMI").read[String] and
    (JsPath \ "tags" \ "Encrypted").readNullable[String] and
    (JsPath \ "state").read[String]
  )(Image.apply _)
  implicit val imagesReads: Reads[Seq[Image]] = dataReads[Image](dataPath = "data", "images")

  private def dataReads[T](dataPath: String*)(implicit r: Reads[T]): Reads[Seq[T]] =
    dataPath.foldLeft[JsPath](__)((path, subPath) => path \ subPath).read[Seq[T]]

  private[prism] def extractData[T](json: JsValue)(implicit r: Reads[T]): Either[Seq[(JsPath, Seq[ValidationError])], T] = {
    r.reads(json) match {
      case JsSuccess(data, _) => Right(data)
      case JsError(e) => Left(e)
    }
  }
}
