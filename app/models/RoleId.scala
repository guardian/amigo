package models

import org.scanamo.DynamoFormat

case class RoleId(value: String) extends AnyVal with StringId

object RoleId {

  implicit val dynamoFormat: DynamoFormat[RoleId] =
    DynamoFormat.iso[RoleId, String](RoleId(_), _.value)

}
