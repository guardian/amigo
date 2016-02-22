package ansible

import models.Recipe

object PlaybookGenerator {

  def generatePlaybook(recipe: Recipe): String = {
    val allRoles = recipe.baseImage.builtinRoles ++ recipe.roles

    s"""---
      |
      |- hosts: all
      |  become: yes
      |  roles:
      |${allRoles.map(roleId => s"    - ${roleId.value}").mkString("\n")}
    """.stripMargin
  }

}
