package models.packer

import java.nio.file.Path

import play.api.libs.json.Json

/**
 * Case class representation of a Packer json file
 */
case class PackerBuildConfig(
  variables: Map[String, String],
  builders: Seq[PackerBuilderConfig],
  provisioners: Seq[PackerProvisionerConfig])

object PackerBuildConfig {
  implicit val jsonWrites = Json.writes[PackerBuildConfig]
}

