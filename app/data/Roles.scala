package data

import java.nio.file.{ Files, Paths }

import ansible.RoleParser
import models.{ RoleId, RoleSummary }

import scala.collection.JavaConverters._

object Roles {

  private val rootDir = Paths.get("roles")

  val list: Seq[RoleSummary] = Files.list(rootDir).iterator.asScala.toSeq
    .flatMap(RoleParser.createRoleSummary)
    .sortBy(_.roleId.value)

  val listIds: Seq[RoleId] = list.map(_.roleId)

  def findById(id: RoleId) = list.find(_ == id)

}
