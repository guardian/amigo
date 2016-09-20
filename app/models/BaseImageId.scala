package models

import cats.data.Xor
import com.gu.scanamo.DynamoFormat
import play.api.mvc.PathBindable

case class BaseImageId(value: String) extends AnyVal with StringId

object BaseImageId {

  implicit val pathBindable: PathBindable[BaseImageId] = implicitly[PathBindable[String]].transform(BaseImageId(_), _.value)

  implicit val dynamoFormat: DynamoFormat[BaseImageId] =
    DynamoFormat.xmap[BaseImageId, String](value => Xor.right(BaseImageId(value)))(_.value)

}
