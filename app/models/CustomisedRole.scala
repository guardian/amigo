package models

case class CustomisedRole(
  roleId: RoleId,
  variables: Map[String, String])

