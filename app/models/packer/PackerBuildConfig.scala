package models.packer

import play.api.libs.json.Json
import play.api.libs.json.OWrites

/** Case class representation of a Packer json file
  */
case class PackerBuildConfig(
    variables: PackerVariablesConfig,
    builders: Seq[PackerBuilderConfig],
    provisioners: Seq[PackerProvisionerConfig]
)

object PackerBuildConfig {
  implicit val jsonWrites: OWrites[PackerBuildConfig] =
    Json.writes[PackerBuildConfig]
}
