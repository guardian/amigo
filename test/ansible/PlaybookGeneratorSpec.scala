package ansible

import models._
import org.joda.time.DateTime
import org.scalatest._

class PlaybookGeneratorSpec extends FlatSpec with Matchers {

  it should "generate an Ansible playbook containing the base image's builtin roles and the recipe's roles" in {
    val recipe = Recipe(
      id = RecipeId("my recipe"),
      description = None,
      baseImage = BaseImage(
        id = BaseImageId("my base image"),
        description = "",
        amiId = AmiId(""),
        builtinRoles = List(
          CustomisedRole(RoleId("builtinRole1"), Map("foo" -> SingleParamValue("bar"))),
          CustomisedRole(RoleId("builtinRole2"), Map.empty)
        ),
        createdBy = "Testy McTest",
        createdAt = DateTime.now(),
        modifiedBy = "Testy McTest",
        modifiedAt = DateTime.now()
      ),
      diskSize = None,
      roles = List(
        CustomisedRole(RoleId("recipeRole1"), Map("wow" -> ListParamValue.of("yeah", "bonza", "needs!quoting"))),
        CustomisedRole(RoleId("recipeRole7"), Map("wow" -> DictParamValue(Map("yeah" -> SingleParamValue("http://fdsfds.fdsfds/fdsfds/fds"))))),
        CustomisedRole(RoleId("recipeRole2"), Map.empty)
      ),
      createdBy = "Testy McTest",
      createdAt = DateTime.now(),
      modifiedBy = "Testy McTest",
      modifiedAt = DateTime.now(),
      bakeSchedule = None,
      encryptFor = Nil
    )

    PlaybookGenerator.generatePlaybook(recipe, Map("var1" -> "value1", "var2" -> "value2")) should be(
      """---
        |
        |- hosts: all
        |  become: yes
        |  vars:
        |    var1: value1
        |    var2: value2
        |  roles:
        |    - { role: builtinRole1, foo: bar }
        |    - builtinRole2
        |    - { role: recipeRole1, wow: [yeah, bonza, 'needs!quoting'] }
        |    - { role: recipeRole7, wow: {yeah: 'http://fdsfds.fdsfds/fdsfds/fds'} }
        |    - recipeRole2
        |""".stripMargin
    )
  }

}
