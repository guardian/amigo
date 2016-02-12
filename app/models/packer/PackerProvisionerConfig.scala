package models.packer

import java.nio.file.Path

import play.api.libs.json.Json

case class PackerProvisionerConfig(
  `type`: String,
  source: Option[String],
  destination: Option[String],
  script: Option[String],
  inline: Option[Seq[String]],
  execute_command: Option[String])

object PackerProvisionerConfig {
  implicit val jsonWrites = Json.writes[PackerProvisionerConfig]

  def fileCopy(source: Path, destination: String) = PackerProvisionerConfig(
    `type` = "file",
    source = Some(source.toAbsolutePath.toString),
    destination = Some(destination),
    script = None,
    inline = None,
    execute_command = None
  )

  def executeRemoteScript(script: String) = PackerProvisionerConfig(
    `type` = "shell",
    script = Some(script),
    execute_command = Some("{{ .Vars }} sudo -E bash -x '{{ .Path }}'"),
    inline = None,
    source = None,
    destination = None
  )

  def executeRemoteCommands(commands: Seq[String]) = PackerProvisionerConfig(
    `type` = "shell",
    inline = Some(commands),
    execute_command = Some("{{ .Vars }} sudo -E bash -x '{{ .Path }}'"),
    script = None,
    source = None,
    destination = None
  )
}
