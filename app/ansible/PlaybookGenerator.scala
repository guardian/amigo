package ansible

import models.{ CustomisedRole, Recipe }

object PlaybookGenerator {

  def generatePlaybook(recipe: Recipe, allVars: Map[String, String]): String = {
    val allRoles = recipe.baseImage.builtinRoles ++ recipe.roles

    s"""---
      |
      |- hosts: all
      |  become: yes
      |  vars:
      |${allVars.map { case (k, v) => s"    $k: $v" }.mkString("\n")}
      |  roles:
      |${allRoles.map(role => s"    - ${renderRole(role)}").mkString("\n")}
      |""".stripMargin
  }

  private def renderRole(role: CustomisedRole): String = {
    if (role.variables.isEmpty)
      role.roleId.value
    else
      s"{ role: ${role.roleId.value}, ${role.variables.map { case (k, v) => s"$k: ${v.quoted}" }.mkString(", ")} }"
  }

}
