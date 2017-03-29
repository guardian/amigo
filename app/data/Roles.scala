package data

import java.nio.file.{ Files, Paths }

import ansible.RoleParser
import models._

import scala.collection.JavaConverters._

object Roles {

  private val rootDir = Paths.get("roles")

  val list: Seq[RoleSummary] = Files.list(rootDir).iterator.asScala.toSeq
    .flatMap(RoleParser.createRoleSummary)
    .sortBy(_.roleId.value)

  val listIds: Seq[RoleId] = list.map(_.roleId)

  def findById(id: RoleId) = list.find(_ == id)

  def transitiveDependencies(allRoles: Seq[RoleSummary], roleToAnalyse: RoleSummary): Dependency = {
    def dependencies(roleId: RoleId): Set[RoleId] = {
      val summaries: Set[RoleSummary] = allRoles.find(_.roleId == roleId).toSet
      summaries.flatMap(_.dependsOn)
    }

    def go(roleId: RoleId): Dependency = {
      val children = dependencies(roleId).map(go)
      Dependency(roleId, children)
    }
    go(roleToAnalyse.roleId)
  }

  def usedBy(allRoles: Seq[RoleSummary], roleToAnalyse: RoleSummary): Seq[RoleId] = {
    allRoles
      .filter(_.dependsOn.contains(roleToAnalyse.roleId))
      .distinct
      .map((r: RoleSummary) => r.roleId)
      .sortBy(_.value)
  }

  def usedByRecipes(allRecipes: Seq[Recipe], roleToAnalyse: RoleSummary): Seq[RecipeId] = {
    allRecipes
      .filter(_.roles.map(_.roleId).contains(roleToAnalyse.roleId))
      .map(_.id)
  }
}
