package controllers

import models.{ RoleId, CustomisedRole }

object ControllerHelpers {

  /**
   * Parse a list of customised roles (role IDs and per-role custom variables) from a posted form.
   */
  def parseEnabledRoles(form: Map[String, Seq[String]]): Either[String, List[CustomisedRole]] = {
    val enabledRoles = form.getOrElse("roles", Nil).toList
    val rolesOrErrors = enabledRoles.map { roleName =>
      val variablesString = form.get(s"role-$roleName-variables").flatMap(_.headOption).getOrElse("")
      val variables = CustomisedRole.formInputTextToVariables(variablesString)
      variables.map(CustomisedRole(RoleId(roleName), _))
    }
    val roles = rolesOrErrors.collect { case Right(role) => role }
    val errors = rolesOrErrors.collect { case Left(error) => error }
    if (errors.nonEmpty) {
      Left(errors.head)
    } else {
      Right(roles)
    }
  }

}
