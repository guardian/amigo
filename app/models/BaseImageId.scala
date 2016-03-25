package models

import cats.data.Validated.Valid
import com.gu.scanamo.DynamoFormat
import play.api.mvc.PathBindable

case class BaseImageId(value: String) extends StringId(value)

object BaseImageId {

  implicit val pathBindable: PathBindable[BaseImageId] = implicitly[PathBindable[String]].transform(BaseImageId(_), _.value)

  implicit val dynamoFormat: DynamoFormat[BaseImageId] =
    DynamoFormat.xmap(DynamoFormat.stringFormat)(value => Valid(BaseImageId(value)))(_.value)

}
