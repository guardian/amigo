package models.packer

import java.nio.file.{ Files, Path }

import play.api.libs.json.Json

import scala.collection.JavaConverters._

case class PackerProvisionerConfig(
  `type`: String,
  source: Option[String] = None,
  destination: Option[String] = None,
  script: Option[String] = None,
  inline: Option[Seq[String]] = None,
  execute_command: Option[String] = None,
  playbook_file: Option[String] = None,
  role_paths: Option[Seq[String]] = None)

object PackerProvisionerConfig {
  implicit val jsonWrites = Json.writes[PackerProvisionerConfig]

  def fileCopy(source: Path, destination: String) = PackerProvisionerConfig(
    `type` = "file",
    source = Some(source.toAbsolutePath.toString),
    destination = Some(destination)
  )

  def executeRemoteScript(script: String) = PackerProvisionerConfig(
    `type` = "shell",
    script = Some(script),
    execute_command = Some("{{ .Vars }} sudo -E bash -x '{{ .Path }}'")
  )

  def executeRemoteCommands(commands: Seq[String]) = PackerProvisionerConfig(
    `type` = "shell",
    inline = Some(commands),
    execute_command = Some("{{ .Vars }} sudo -E bash -x '{{ .Path }}'")
  )

  def ansibleLocal(playbookFile: Path, rolesDir: Path) = {
    val rolePaths = Files.list(rolesDir).iterator.asScala.toSeq.map(_.toAbsolutePath.toString)
    PackerProvisionerConfig(
      `type` = "ansible-local",
      playbook_file = Some(playbookFile.toAbsolutePath.toString),
      role_paths = Some(rolePaths)
    )
  }
}
