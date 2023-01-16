package packer

import models.packer.PackerVariablesConfig

case class ImageDetails(name: String, description: String, tags: Map[String, String])

object ImageDetails {
  def apply(vars: PackerVariablesConfig, stage: String): ImageDetails = {
    ImageDetails(
      name = s"amigo_${vars.recipe}_${vars.build_number}_${vars.time}",
      description = s"AMI for ${vars.recipe} built by Amigo: #${vars.build_number}",
      tags = Map(
        "BuiltBy" -> "amigo",
        "AmigoStage" -> stage,
        "Name" -> s"amigo_${vars.recipe}_${vars.build_number}_${vars.time}",
        "Recipe" -> vars.recipe,
        "BuildNumber" -> vars.build_number,
        "SourceAMI" -> vars.base_image_ami_id))
  }
}
