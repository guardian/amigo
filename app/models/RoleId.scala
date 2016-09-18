package models

import cats.data.Xor
import com.gu.scanamo.DynamoFormat

case class RoleId(value: String) extends AnyVal with StringId

object RoleId {

  implicit val dynamoFormat: DynamoFormat[RoleId] =
    DynamoFormat.xmap[RoleId, String](value => Xor.right(RoleId(value)))(_.value)

}
