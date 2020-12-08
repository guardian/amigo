package packer

import models.packer._
import models.{ Bake, Ubuntu }
import services.AmiMetadata

import java.nio.file.{ Path, Paths }

object PackerBuildConfigGenerator {

  val stage: String = "INFRA"
  val stack: String = "amigo-packer"

  /**
   * Generates a Packer build config that:
   *  - starts an EC2 machine
   *  - installs Ansible
   *  - runs Ansible to install the required roles
   *  - tags the resulting AMI with the recipe ID and build number
   */
  def generatePackerBuildConfig(
    amigoStage: String, bake: Bake, playbookFile: Path, variables: PackerVariablesConfig, awsAccountNumbers: Seq[String], sourceAmiMetadata: AmiMetadata, amigoDataBucket: Option[String])(implicit packerConfig: PackerConfig): PackerBuildConfig = {
    val awsAccounts = awsAccountNumbers.mkString(",")
    val imageDetails = ImageDetails.apply(variables, packerConfig.stage)
    val region = "eu-west-1"

    val disk = bake.recipe.diskSize.map(size => List(BlockDeviceMapping(volume_size = size)))

    val instanceType = sourceAmiMetadata.architecture match {
      case "x86_64" => "t3.small"
      case "arm64" => "t4g.small"
      case other => throw new IllegalArgumentException(s"Don't know what instance type to use to bake an AMI for $other")
    }

    val builder = PackerBuilderConfig(
      name = "{{user `recipe`}}",
      `type` = "amazon-ebs",
      region = region,
      vpc_id = packerConfig.vpcId,
      subnet_id = packerConfig.subnetId,
      source_ami = "{{user `base_image_ami_id`}}",
      instance_type = instanceType,

      ssh_username = bake.recipe.baseImage.linuxDist.getOrElse(Ubuntu).loginName,

      run_tags = Map(
        "Stage" -> stage,
        "AmigoStage" -> amigoStage,
        "Stack" -> stack,
        "App" -> "{{user `recipe`}}",
        "BakeId" -> s"${bake.bakeId.toString}"
      ),
      ami_name = imageDetails.name,
      ami_description = imageDetails.description,
      ami_users = awsAccounts,
      snapshot_users = awsAccounts,
      iam_instance_profile = packerConfig.instanceProfile,
      tags = imageDetails.tags,
      ami_block_device_mappings = disk,
      launch_block_device_mappings = disk

    )

    val baseImage = bake.recipe.baseImage.linuxDist.getOrElse(Ubuntu)

    val uploadPackagesCommand = amigoDataBucket.map { bucket =>
      PackerProvisionerConfig.executeRemoteCommands(baseImage.uploadPackagesCommands(bake.bakeId, region, bucket))
    }.toSeq

    val provisioners = baseImage.provisioners ++ Seq(
      PackerProvisionerConfig.ansibleLocal(playbookFile, Paths.get("roles"))
    ) ++ uploadPackagesCommand

    PackerBuildConfig(
      variables,
      Seq(builder),
      provisioners
    )
  }

}
