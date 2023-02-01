package ansible

import models.{CustomisedRole, Recipe}

object PlaybookGenerator {

  def generatePlaybook(recipe: Recipe, allVars: Map[String, String]): String = {
    val allRoles = recipe.baseImage.builtinRoles ++ recipe.roles

    s"""---
      |
      |- hosts: all
      |  become: yes
      |  pre_tasks:
      |    - name: Prevent apt from upgrading grub-efi-arm64
      |      ansible.builtin.dpkg_selections:
      |        name: grub-efi-arm64
      |        selection: hold
      |      when: ansible_facts['distribution_major_version'] == "18"
      |    - name: Prevent apt from upgrading grub-efi-arm64-bin
      |      ansible.builtin.dpkg_selections:
      |        name: grub-efi-arm64-bin
      |        selection: hold
      |      when: ansible_facts['distribution_major_version'] == "18"
      |    - name: Prevent apt from upgrading grub-efi-arm64-signed
      |      ansible.builtin.dpkg_selections:
      |        name: grub-efi-arm64-signed
      |        selection: hold
      |      when: ansible_facts['distribution_major_version'] == "18"
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
      s"{ role: ${role.roleId.value}, ${role.variables
          .map { case (k, v) => s"$k: ${v.quoted}" }
          .mkString(", ")} }"
  }

}
