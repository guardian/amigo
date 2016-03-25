package models

case class CustomisedRole(
    roleId: RoleId,
    variables: Map[String, String]) {

  def variablesToString = variables.map { case (k, v) => s"$k: $v" }.mkString("{ ", ", ", " }")
}

