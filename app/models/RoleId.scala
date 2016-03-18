package models

import cats.data.Validated.Valid
import com.gu.scanamo.DynamoFormat

case class RoleId(value: String)

object RoleId {

  implicit val dynamoFormat: DynamoFormat[RoleId] =
    DynamoFormat.xmap(DynamoFormat.stringFormat)(value => Valid(RoleId(value)))(_.value)

}
