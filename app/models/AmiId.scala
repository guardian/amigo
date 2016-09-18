package models

import cats.data.Xor
import com.gu.scanamo.DynamoFormat

case class AmiId(value: String) extends AnyVal with StringId

object AmiId {

  implicit val dynamoFormat: DynamoFormat[AmiId] =
    DynamoFormat.xmap[AmiId, String](value => Xor.right(AmiId(value)))(_.value)

}