package packer

import models.packer.PackerVariablesConfig

object ImageTags {
  def tags(vars: PackerVariablesConfig, stage: String) = {
    Map(
      "BuiltBy" -> "amigo",
      "AmigoStage" -> stage,
      "Name" -> s"amigo_${vars.recipe}_${vars.build_number}_${vars.time}",
      "Recipe" -> vars.recipe,
      "BuildNumber" -> vars.build_number,
      "SourceAMI" -> vars.base_image_ami_id
    )
  }
}
