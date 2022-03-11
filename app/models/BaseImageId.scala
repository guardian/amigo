package models

import org.scanamo.DynamoFormat
import play.api.mvc.PathBindable

case class BaseImageId(value: String) extends AnyVal with StringId

object BaseImageId {

  implicit val pathBindable: PathBindable[BaseImageId] = implicitly[PathBindable[String]].transform(BaseImageId(_), _.value)

  implicit val dynamoFormat: DynamoFormat[BaseImageId] =
    DynamoFormat.iso[BaseImageId, String](BaseImageId(_), _.value)

}
