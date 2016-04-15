package models

case class CustomisedRole(
    roleId: RoleId,
    variables: Map[String, String]) {

  def variablesToString = variables.map { case (k, v) => s"$k: $v" }.mkString("{ ", ", ", " }")

  /** Render the variables for display in a form input box */
  def variablesToFormInputText = {
    if (variables.isEmpty) ""
    else variables.map { case (k, v) => s"$k: $v" }.mkString(", ")
  }

}

object CustomisedRole {

  private val KeyValuePair = """(.+): ?(.+)""".r

  def formInputTextToVariables(input: String): Map[String, String] = {
    input.split(", *").collect { case KeyValuePair(k, v) => (k.trim, v.trim) }.toMap
  }

}
