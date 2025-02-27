package packer

import models.packer._
import models.{Bake, LinuxDist, Ubuntu}
import services.AmiMetadata
import java.nio.file.{Path, Paths}

object PackerBuildConfigGenerator {

  val stage: String = "INFRA"
  val stack: String = "amigo-packer"

  /** Generates a Packer build config that:
    *   - starts an EC2 machine
    *   - installs Ansible
    *   - runs Ansible to install the required roles
    *   - tags the resulting AMI with the recipe ID and build number
    */
  def generatePackerBuildConfig(
      amigoStage: String,
      bake: Bake,
      playbookFile: Path,
      variables: PackerVariablesConfig,
      awsAccountNumbers: Seq[String],
      sourceAmiMetadata: AmiMetadata,
      amigoDataBucket: Option[String],
      requiresXlargeBuilder: Boolean
  )(implicit packerConfig: PackerConfig): PackerBuildConfig = {
    val awsAccounts = awsAccountNumbers.mkString(",")
    val imageDetails = ImageDetails.apply(variables, packerConfig.stage)
    val region = "eu-west-1"

    val disk = bake.recipe.diskSize.map(size =>
      List(BlockDeviceMapping(volume_size = size))
    )

    val instanceSize = if (requiresXlargeBuilder) "xlarge" else "small"

    val instanceType = sourceAmiMetadata.architecture match {
      case "x86_64" => s"t3.$instanceSize"
      case "arm64"  => s"t4g.$instanceSize"
      case other =>
        throw new IllegalArgumentException(
          s"Don't know what instance type to use to bake an AMI for $other"
        )
    }

    // here we are using requiresXlargeBuilder as an indicator that freezing the packer instance to an AMI will take longer
    // 15, 240 = poll every 15 seconds, stop after 240 attempts (total 1 hour)
    val awsPolling =
      if (requiresXlargeBuilder) Some(AwsPolling(15, 240)) else None

    val builder = PackerBuilderConfig(
      name = "{{user `recipe`}}",
      `type` = "amazon-ebs",
      region = region,
      vpc_id = packerConfig.vpcId,
      subnet_id = packerConfig.subnetId,
      source_ami = "{{user `base_image_ami_id`}}",
      instance_type = instanceType,
      ssh_username =
        bake.recipe.baseImage.linuxDist.getOrElse(Ubuntu).loginName,
      ssh_interface = "session_manager",
      run_tags = Map(
        "Name" -> "Packer Builder",
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
      launch_block_device_mappings = disk,
      security_group_id = packerConfig.securityGroupId,
      aws_polling = awsPolling
    )

    val baseImage = bake.recipe.baseImage.linuxDist.getOrElse(Ubuntu)

    val uploadPackagesCommand = amigoDataBucket.map { bucket =>
      PackerProvisionerConfig.executeRemoteCommands(
        Seq(
          baseImage.savePackageListCommand(bake.bakeId),
          LinuxDist.uploadPackageListCommand(bake.bakeId, region, bucket)
        )
      )
    }.toSeq

    val provisioners = baseImage.provisioners ++ Seq(
      PackerProvisionerConfig.ansibleLocal(playbookFile, Paths.get("roles"))
    ) ++ uploadPackagesCommand

    PackerBuildConfig(variables, Seq(builder), provisioners)
  }

}
