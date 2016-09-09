package models.packer

import play.api.libs.json.Json

case class PackerBuilderConfig(
  name: String,
  `type`: String,
  region: String,
  vpc_id: Option[String],
  subnet_id: Option[String],
  source_ami: String,
  instance_type: String,
  ssh_username: String,
  run_tags: Map[String, String],
  ami_name: String,
  ami_description: String,
  ami_users: String,
  iam_instance_profile: Option[String],
  tags: Map[String, String])

object PackerBuilderConfig {
  implicit val jsonWrites = Json.writes[PackerBuilderConfig]
}
