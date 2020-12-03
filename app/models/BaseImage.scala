package models

import com.gu.scanamo.DynamoFormat
import models.packer.PackerProvisionerConfig
import org.joda.time.DateTime
import cats.syntax.either._
import com.gu.scanamo.error.TypeCoercionError

sealed trait LinuxDist {
  val name: String
  val provisioners: Seq[PackerProvisionerConfig]
  def postProvisioners(bakeId: BakeId, region: String): Seq[PackerProvisionerConfig]
  val loginName: String
}
object LinuxDist {
  implicit val dynamoFormat: DynamoFormat[LinuxDist] =
    DynamoFormat.xmap[LinuxDist, String](value => Either.fromOption(
      LinuxDist.create(value), TypeCoercionError(new RuntimeException(s"$value is not a known LinuxDist"))
    ))(_.name)

  def create(name: String): Option[LinuxDist] = all.get(name)

  val all = Map("ubuntu" -> Ubuntu, "redhat" -> RedHat, "amazon linux 2" -> AmazonLinux2)
}
case object Ubuntu extends LinuxDist {
  val name = "ubuntu"
  val loginName = "ubuntu"
  val provisioners = Seq(
    // bootstrap Ansible
    PackerProvisionerConfig.executeRemoteCommands(Seq(
      // Wait for cloud-init to finish first: https://github.com/mitchellh/packer/issues/2639
      "while [ ! -f /var/lib/cloud/instance/boot-finished ]; do echo 'Waiting for cloud-init...'; sleep 1; done",
      "DEBIAN_FRONTEND=noninteractive  apt-get --yes install software-properties-common",
      "version=$(. /etc/os-release; echo $VERSION_ID | cut -d'.' -f1)",
      "if (($version < 20)); then apt-add-repository --yes ppa:ansible/ansible; fi",
      // ansible ppa broken in ubuntu: https://github.com/ansible/ansible/issues/69203
      // and available in https://packages.ubuntu.com/focal/ansible
      "apt-get --yes update",
      "DEBIAN_FRONTEND=noninteractive apt-get --yes install ansible"
    ))
  )
  def postProvisioners(bakeId: BakeId, region: String) = Seq(PackerProvisionerConfig.executeRemoteCommands(Seq(
    s"""apt list --installed > /tmp/${bakeId}""",
    s"""aws s3 cp /tmp/${bakeId} s3://amigo-data/packagelists/${bakeId} --region ${region}""")
  ))
}
case object RedHat extends LinuxDist {
  val name = "redhat"
  val loginName = "ec2-user"
  val provisioners = Seq(
    PackerProvisionerConfig.executeRemoteCommands(Seq(
      "while [ ! -f /var/lib/cloud/instance/boot-finished ]; do echo 'Waiting for cloud-init...'; sleep 1; done",
      "rpm -Uvh http://download.fedoraproject.org/pub/epel/6/i386/epel-release-6-8.noarch.rpm",
      "yum -y update",
      "yum -y install ansible",
      "yum -y install libselinux-python-2.0.94-7.el6"
    ))
  )
  def postProvisioners(bakeId: BakeId, region: String) = Seq()
}

case object AmazonLinux2 extends LinuxDist {
  val name = "amazon linux 2"
  val loginName = "ec2-user"
  val provisioners = Seq(
    PackerProvisionerConfig.executeRemoteCommands(Seq(
      "while [ ! -f /var/lib/cloud/instance/boot-finished ]; do echo 'Waiting for cloud-init...'; sleep 1; done",
      "yum -y update",
      "yum -y install amazon-linux-extras", // should be a no-op
      "amazon-linux-extras enable ansible2",
      "yum -y install ansible"
    ))
  )
  def postProvisioners(bakeId: BakeId, region: String) = Seq()
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

