package models.packer

import play.api.libs.json._

/** Case class representation of a Packer json file
  */
case class PackerBuildConfig(
    variables: PackerVariablesConfig,
    builders: Seq[PackerBuilderConfig],
    provisioners: Seq[PackerProvisionerConfig]
)

object PackerBuildConfig {
  implicit val jsonWrites: Writes[PackerBuildConfig] =
    Json.writes[PackerBuildConfig]
}
