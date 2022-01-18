package controllers

import models.{ RoleId, CustomisedRole }
import cats.syntax.either._
import cats.syntax.traverse._
import cats.instances.either._
import cats.instances.list._

object ControllerHelpers {

  /**
   * Parse a list of customised roles (role IDs and per-role custom variables) from a posted form.
   */
  def parseEnabledRoles(form: Map[String, Seq[String]]): Either[String, List[CustomisedRole]] = {
    val enabledRoles = form.getOrElse("roles", Nil).toList
    enabledRoles.traverseU { roleName =>
      val variablesString = form.get(s"role-$roleName-variables").flatMap(_.headOption).getOrElse("")
      val variables = CustomisedRole.formInputTextToVariables(variablesString)
      variables.map(CustomisedRole(RoleId(roleName), _))
    }
  }

}
