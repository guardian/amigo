package models

case class RoleSummary(
    roleId: RoleId,
    dependsOn: Set[RoleId],
    tasksMain: Yaml,
    readme: Option[Markdown]
)
