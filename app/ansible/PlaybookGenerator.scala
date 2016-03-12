package ansible

import models.Recipe

object PlaybookGenerator {

  def generatePlaybook(recipe: Recipe): String = {
    val allRoles = recipe.baseImage.builtinRoles ++ recipe.roles

    // TODO variables for roles

    s"""---
      |
      |- hosts: all
      |  become: yes
      |  roles:
      |${allRoles.map(role => s"    - ${role.roleId.value}").mkString("\n")}
    """.stripMargin
  }

}
