package models.packer

import play.api.libs.json.Json

case class PackerBuilderConfig(
  name: String,
  `type`: String,
  region: String,
  vpc_id: String,
  subnet_id: String,
  source_ami: String,
  instance_type: String,
  ssh_username: String,
  run_tags: Map[String, String],
  ami_name: String,
  ami_description: String,
  // TODO ami_users, iam_instance_profile
  tags: Map[String, String])

object PackerBuilderConfig {
  implicit val jsonWrites = Json.writes[PackerBuilderConfig]
}
