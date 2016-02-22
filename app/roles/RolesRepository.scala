package roles

import java.nio.file.{ Files, Paths }

import models.RoleId

import scala.collection.JavaConverters._

/**
 * Proof of concept that just loads the list of roles from the local disk
 */
object RolesRepository {

  private val rootDir = Paths.get("roles")

  val roles: Seq[RoleId] = Files.list(rootDir).iterator.asScala.toSeq.map(path => RoleId(path.getFileName.toString))

}
