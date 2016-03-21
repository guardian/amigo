package ansible

import models.{ CustomisedRole, Recipe }

object PlaybookGenerator {

  def generatePlaybook(recipe: Recipe): String = {
    val allRoles = recipe.baseImage.builtinRoles ++ recipe.roles

    // TODO variables for roles

    s"""---
      |
      |- hosts: all
      |  become: yes
      |  roles:
      |${allRoles.map(role => s"    - ${renderRole(role)}").mkString("\n")}
      |""".stripMargin
  }

  private def renderRole(role: CustomisedRole): String = {
    if (role.variables.isEmpty)
      role.roleId.value
    else
      s"{ role: ${role.roleId.value}, ${role.variables.map { case (k, v) => s"$k: '$v'" }.mkString(", ")} }"
  }

}
