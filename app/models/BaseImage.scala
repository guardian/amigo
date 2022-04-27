package models

import org.scanamo.DynamoFormat
import models.packer.PackerProvisionerConfig
import org.joda.time.DateTime
import cats.syntax.either._
import org.scanamo.TypeCoercionError
import BakeId.toMetadata
import BakeId.toFilename
import data.PackageList

sealed trait LinuxDist {
  val name: String
  val provisioners: Seq[PackerProvisionerConfig]
  def savePackageListCommand(bakeId: BakeId): String
  val loginName: String
}
object LinuxDist {
  implicit val dynamoFormat: DynamoFormat[LinuxDist] =
    DynamoFormat.xmap[LinuxDist, String](value => Either.fromOption(
      LinuxDist.create(value), TypeCoercionError(new RuntimeException(s"$value is not a known LinuxDist"))), _.name)

  def create(name: String): Option[LinuxDist] = all.get(name)

  def packageListTempPath(bakeId: BakeId) = s"/tmp/${toFilename(bakeId)}"

  def uploadPackageListCommand(bakeId: BakeId, region: String, bucket: String) =
    s"aws s3 cp ${packageListTempPath(bakeId)} ${PackageList.s3Url(bakeId, bucket)} --region ${region} --metadata ${toMetadata(bakeId)}"

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
      "DEBIAN_FRONTEND=noninteractive apt-get --yes install ansible")))
  def savePackageListCommand(bakeId: BakeId) =
    s"dpkg-query -W > ${LinuxDist.packageListTempPath(bakeId)}"
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
      "yum -y install libselinux-python-2.0.94-7.el6")))
  def savePackageListCommand(bakeId: BakeId) =
    s"yum list installed > ${LinuxDist.packageListTempPath(bakeId)}"

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
      "yum -y install ansible")))
  def savePackageListCommand(bakeId: BakeId) =
    s"yum list installed > ${LinuxDist.packageListTempPath(bakeId)}"
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
  linuxDist: Option[LinuxDist] = None,
  eolDate: Option[DateTime] = None)

sealed trait EolStatus
case object EndOfLife extends EolStatus
case object EndOfLifeSoon extends EolStatus
case object Supported extends EolStatus

object BaseImage {

  def eolStatus(image: BaseImage): EolStatus = {
    if (image.eolDate.exists(_.isBefore(DateTime.now))) {
      EndOfLife
    } else if (image.eolDate.exists(_.isBefore((DateTime.now.plusMonths(3))))) {
      EndOfLifeSoon
    } else Supported
  }

  def eolStatusClass(image: BaseImage): String = eolStatus(image) match {
    case EndOfLife => "end-of-life"
    case EndOfLifeSoon => "end-of-life-soon"
    case Supported => "supported"
  }

  def eolStatusString(image: BaseImage): String = {
    val eolDateString = image.eolDate.map(_.toString("dd/MM/yyyy")).getOrElse("unknown")
    eolStatus(image) match {
      case EndOfLife => s"WARNING: Base image no longer supported. Support ended on ${eolDateString}"
      case EndOfLifeSoon => s"WARNING: Base image end of life on ${eolDateString}"
      case Supported => s"Base image supported until ${eolDateString}"
    }
  }
}