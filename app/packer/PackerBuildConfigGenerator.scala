package packer

import java.nio.file.{ Path, Paths }

import models.{ Bake, Ubuntu }
import models.packer._

object PackerBuildConfigGenerator {

  /**
   * Generates a Packer build config that:
   *  - starts an EC2 machine
   *  - installs Ansible
   *  - runs Ansible to install the required roles
   *  - tags the resulting AMI with the recipe ID and build number
   */
  def generatePackerBuildConfig(
    bake: Bake, playbookFile: Path, variables: PackerVariablesConfig, awsAccountNumbers: Seq[String])(implicit packerConfig: PackerConfig): PackerBuildConfig = {
    val awsAccounts = awsAccountNumbers.mkString(",")
    val imageDetails = ImageDetails.apply(variables, packerConfig.stage)

    val disk = bake.recipe.diskSize.map(size => List(BlockDeviceMapping(volume_size = size)))

    val builder = PackerBuilderConfig(
      name = "{{user `recipe`}}",
      `type` = "amazon-ebs",
      region = "eu-west-1",
      vpc_id = packerConfig.vpcId,
      subnet_id = packerConfig.subnetId,
      source_ami = "{{user `base_image_ami_id`}}",
      instance_type = "t2.micro",

      ssh_username = bake.recipe.baseImage.linuxDist.getOrElse(Ubuntu).loginName,

      run_tags = Map(
        "Stage" -> "INFRA",
        "Stack" -> "amigo-packer",
        "App" -> "{{user `recipe`}}"
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

    val provisioners = bake.recipe.baseImage.linuxDist.getOrElse(Ubuntu).provisioners ++ Seq(
      PackerProvisionerConfig.ansibleLocal(playbookFile, Paths.get("roles"))
    )

    PackerBuildConfig(
      variables,
      Seq(builder),
      provisioners
    )
  }

}
