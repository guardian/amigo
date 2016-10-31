package models

import cats.data.Xor
import com.gu.scanamo.DynamoFormat
import models.packer.PackerProvisionerConfig

import org.joda.time.DateTime

sealed trait LinuxDist {
  val name: String
  val provisioners: Seq[PackerProvisionerConfig]
  val loginName: String
}
object LinuxDist {
  implicit val dynamoFormat: DynamoFormat[LinuxDist] =
    DynamoFormat.xmap[LinuxDist, String](value => Xor.right(LinuxDist.create(value)))(_.name)

  def create(name: String): LinuxDist = name match {
    case "ubuntu" => Ubuntu
    case "redhat" => RedHat
  }
}
case object Ubuntu extends LinuxDist {
  val name = "ubuntu"
  val loginName = "ubuntu"
  val provisioners = Seq(
    // bootstrap Ansible
    PackerProvisionerConfig.executeRemoteCommands(Seq(
      // Wait for cloud-init to finish first: https://github.com/mitchellh/packer/issues/2639
      "while [ ! -f /var/lib/cloud/instance/boot-finished ]; do echo 'Waiting for cloud-init...'; sleep 1; done",
      "apt-get --yes install software-properties-common",
      "apt-add-repository ppa:ansible/ansible",
      "apt-get --yes update",
      "apt-get --yes install ansible"
    ))
  )
}
case object RedHat extends LinuxDist {
  val name = "redhat"
  val loginName = "ec2-user"
  val provisioners = Nil
}

case class BaseImage(
  id: BaseImageId,
  description: String,
  amiId: AmiId,
  builtinRoles: List[CustomisedRole],
  createdBy: String,
  createdAt: DateTime,
  modifiedBy: String,
  modifiedAt: DateTime,
  linuxDist: Option[LinuxDist] = None)

