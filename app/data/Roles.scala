package data

import java.nio.file.{ Files, Paths }

import ansible.RoleParser
import models._

import scala.jdk.CollectionConverters._

object Roles {

  private val rootDir = Paths.get("roles")

  val list: Seq[RoleSummary] = Files.list(rootDir).iterator.asScala.toSeq
    .flatMap(RoleParser.createRoleSummary(_))
    .sortBy(_.roleId.value)

  val listIds: Seq[RoleId] = list.map(_.roleId)

  def findById(id: RoleId) = list.find(_.roleId == id)

  def transitiveDependencies(allRoles: Seq[RoleSummary], roleToAnalyse: RoleId): Dependency = {
    def dependencies(roleId: RoleId): Set[RoleId] = {
      val summaries: Set[RoleSummary] = allRoles.find(r => r.roleId == roleId).toSet
      summaries.flatMap(_.dependsOn)
    }

    def go(roleId: RoleId): Dependency = {
      val children = dependencies(roleId).map(go)
      Dependency(roleId, children)
    }
    go(roleToAnalyse)
  }

  def customisedTransitiveDependency(allRoles: Seq[RoleSummary], customisedRoles: Iterable[CustomisedRole]): Iterable[(CustomisedRole, Dependency)] = {
    customisedRoles.map { role =>
      role -> transitiveDependencies(allRoles, role.roleId)
    }
  }

  def usedBy(allRoles: Seq[RoleSummary], roleToAnalyse: RoleSummary): Seq[RoleId] = {
    allRoles.filter(_.dependsOn.contains(roleToAnalyse.roleId)).distinct.map((r: RoleSummary) => r.roleId).sortBy(_.value)
  }

  def usedByRecipes(allRecipes: Seq[Recipe], roleToAnalyse: RoleSummary): Seq[RecipeId] = {
    allRecipes.filter(_.roles.map(_.roleId).contains(roleToAnalyse.roleId)).map(_.id)
  }

  def usedByBaseImages(allBaseImages: Seq[BaseImage], roleToAnalyse: RoleSummary): Seq[BaseImage] = {
    allBaseImages.filter(_.builtinRoles.map(_.roleId).contains(roleToAnalyse.roleId))
  }
}
