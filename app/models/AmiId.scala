package models

import com.gu.scanamo.DynamoFormat

case class AmiId(value: String) extends AnyVal with StringId

object AmiId {

  implicit val dynamoFormat: DynamoFormat[AmiId] =
    DynamoFormat.iso[AmiId, String](AmiId(_))(_.value)

}