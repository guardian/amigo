package models

import cats.data.Validated.Valid
import com.gu.scanamo.DynamoFormat

case class BaseImageId(value: String)

object BaseImageId {

  implicit val dynamoFormat: DynamoFormat[BaseImageId] =
    DynamoFormat.xmap(DynamoFormat.stringFormat)(value => Valid(BaseImageId(value)))(_.value)

}
