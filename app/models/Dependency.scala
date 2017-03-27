package models

case class Dependency(roleId: RoleId, dependencies: Set[Dependency])
