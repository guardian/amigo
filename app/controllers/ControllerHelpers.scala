package controllers

import models.{ RoleId, CustomisedRole }

object ControllerHelpers {

  /**
   * Parse a list of customised roles (role IDs and per-role custom variables) from a posted form.
   */
  def parseEnabledRoles(form: Map[String, Seq[String]]): List[CustomisedRole] = {
    val enabledRoles = form.getOrElse("roles", Nil)
    enabledRoles.map { roleName =>
      val variablesString = form.get(s"role-$roleName-variables").flatMap(_.headOption).getOrElse("")
      val variables = CustomisedRole.formInputTextToVariables(variablesString)
      CustomisedRole(RoleId(roleName), variables)
    }.toList
  }

}
