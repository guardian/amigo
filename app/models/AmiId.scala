package models

import cats.data.Validated.Valid
import com.gu.scanamo.DynamoFormat

case class AmiId(value: String) extends StringId(value)

object AmiId {

  implicit val dynamoFormat: DynamoFormat[AmiId] =
    DynamoFormat.xmap(DynamoFormat.stringFormat)(value => Valid(AmiId(value)))(_.value)

}