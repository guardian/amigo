package models.packer

import play.api.libs.json.{ Json, OWrites }

case class BlockDeviceMapping(
  device_name: String = "/dev/sda1",
  volume_size: Int,
  volume_type: String = "gp2",
  delete_on_termination: Boolean = true)

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
  snapshot_users: String,
  iam_instance_profile: Option[String],
  tags: Map[String, String],
  ami_block_device_mappings: Option[List[BlockDeviceMapping]],
  launch_block_device_mappings: Option[List[BlockDeviceMapping]])

object PackerBuilderConfig {
  implicit val jsonDiskWrites: OWrites[BlockDeviceMapping] = Json.writes[BlockDeviceMapping]
  implicit val jsonWrites: OWrites[PackerBuilderConfig] = Json.writes[PackerBuilderConfig]
}