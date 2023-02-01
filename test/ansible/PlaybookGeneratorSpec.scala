package ansible

import models._
import org.joda.time.DateTime
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PlaybookGeneratorSpec extends AnyFlatSpec with Matchers {

  it should "generate an Ansible playbook containing the base image's builtin roles and the recipe's roles" in {
    val recipe = Recipe(
      id = RecipeId("my recipe"),
      description = None,
      baseImage = BaseImage(
        id = BaseImageId("my base image"),
        description = "",
        amiId = AmiId(""),
        builtinRoles = List(
          CustomisedRole(
            RoleId("builtinRole1"),
            Map("foo" -> SingleParamValue("bar"))
          ),
          CustomisedRole(RoleId("builtinRole2"), Map.empty)
        ),
        createdBy = "Testy McTest",
        createdAt = DateTime.now(),
        modifiedBy = "Testy McTest",
        modifiedAt = DateTime.now()
      ),
      diskSize = None,
      roles = List(
        CustomisedRole(
          RoleId("recipeRole1"),
          Map("wow" -> ListParamValue.of("yeah", "bonza", "needs!quoting"))
        ),
        CustomisedRole(
          RoleId("recipeRole7"),
          Map(
            "wow" -> DictParamValue(
              Map("yeah" -> SingleParamValue("http://fdsfds.fdsfds/fdsfds/fds"))
            )
          )
        ),
        CustomisedRole(RoleId("recipeRole2"), Map.empty)
      ),
      createdBy = "Testy McTest",
      createdAt = DateTime.now(),
      modifiedBy = "Testy McTest",
      modifiedAt = DateTime.now(),
      bakeSchedule = None,
      encryptFor = Nil
    )

    PlaybookGenerator.generatePlaybook(
      recipe,
      Map("var1" -> "value1", "var2" -> "value2")
    ) should be("""---
        |
        |- hosts: all
        |  become: yes
        |  pre_tasks:
        |    - name: Prevent apt from upgrading grub-efi-arm64
        |      ansible.builtin.dpkg_selections:
        |        name: grub-efi-arm64
        |        selection: hold
        |      when: ansible_facts['distribution_major_version'] == "18" and ansible_facts['architecture'] == "aarch64"
        |    - name: Prevent apt from upgrading grub-efi-arm64-bin
        |      ansible.builtin.dpkg_selections:
        |        name: grub-efi-arm64-bin
        |        selection: hold
        |      when: ansible_facts['distribution_major_version'] == "18" and ansible_facts['architecture'] == "aarch64"
        |    - name: Prevent apt from upgrading grub-efi-arm64-signed
        |      ansible.builtin.dpkg_selections:
        |        name: grub-efi-arm64-signed
        |        selection: hold
        |      when: ansible_facts['distribution_major_version'] == "18" and ansible_facts['architecture'] == "aarch64"
        |  vars:
        |    var1: value1
        |    var2: value2
        |  roles:
        |    - { role: builtinRole1, foo: bar }
        |    - builtinRole2
        |    - { role: recipeRole1, wow: [yeah, bonza, 'needs!quoting'] }
        |    - { role: recipeRole7, wow: {yeah: 'http://fdsfds.fdsfds/fdsfds/fds'} }
        |    - recipeRole2
        |""".stripMargin)
  }

}
