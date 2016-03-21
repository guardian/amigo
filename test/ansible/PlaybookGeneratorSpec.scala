package ansible

import models._
import org.scalatest._

class PlaybookGeneratorSpec extends FlatSpec with Matchers {

  it should "generate an Ansible playbook containing the base image's builtin roles and the recipe's roles" in {
    val recipe = Recipe(
      id = RecipeId("my recipe"),
      description = "",
      baseImage = BaseImage(
        id = BaseImageId("my base image"),
        description = "",
        amiId = AmiId(""),
        builtinRoles = List(
          CustomisedRole(RoleId("builtinRole1"), Map("foo" -> "bar")),
          CustomisedRole(RoleId("builtinRole2"), Map.empty)
        )
      ),
      roles = List(
        CustomisedRole(RoleId("recipeRole1"), Map("wow" -> "yeah")),
        CustomisedRole(RoleId("recipeRole2"), Map.empty)
      )
    )

    PlaybookGenerator.generatePlaybook(recipe) should be(
      """---
        |
        |- hosts: all
        |  become: yes
        |  roles:
        |    - { role: builtinRole1, foo: 'bar' }
        |    - builtinRole2
        |    - { role: recipeRole1, wow: 'yeah' }
        |    - recipeRole2
        |""".stripMargin
    )
  }

}
