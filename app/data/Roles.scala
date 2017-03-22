package data

import java.nio.file.{ Files, Paths }

import ansible.RoleParser
import models.{ RoleId, RoleSummary }

import scala.annotation.tailrec
import scala.collection.JavaConverters._

object Roles {

  private val rootDir = Paths.get("roles")

  val list: Seq[RoleSummary] = Files.list(rootDir).iterator.asScala.toSeq
    .flatMap(RoleParser.createRoleSummary)
    .sortBy(_.roleId.value)

  val listIds: Seq[RoleId] = list.map(_.roleId)

  def findById(id: RoleId) = list.find(_ == id)

  def transitiveDependencies(roles: Seq[RoleSummary], role: RoleSummary): Set[RoleId] = {
    @tailrec
    def go(rs: Seq[RoleSummary], dep: Seq[RoleId], acc: Seq[RoleId]): Set[RoleId] = {
      dep match {
        case h :: t =>
          val currDependencies: List[RoleId] = roles.find(r => r.roleId == h).get.dependsOn.toList
          val totalDependencies = (t ++ currDependencies).distinct
          go(rs, totalDependencies, h +: acc)
        case _ =>
          acc.toSet
      }
    }
    go(roles, role.dependsOn.toList, role.dependsOn.toList)
  }
}
