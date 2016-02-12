package packer

import models.packer.{ PackerBuilderConfig, PackerBuildConfig }
import models.{ Bake, Recipe }

object PackerConfigGenerator {

  /**
   * Generates a Packer build config that looks like:
   *
   * // format: OFF
   * {{{
     {
       "variables": {
         "recipe": "my recipe name",
         "base_image_ami_id": "base image's AMI ID",
         "build_number": "build number"
       },
       "builders": [
         {
           "name": "{{user `recipe`}}",
           "type": "amazon-ebs",
           "region": "eu-west-1",
           "source_ami": "{{user `base_image_ami_id`}},
           "instance_type": "t2.micro",
           "ssh_username": "ubuntu",
           "run_tags": {"Stage":"INFRA", "Stack":"amigo-packer", "App": "{{user `recipe`}}"},
           "ami_name": "amigo_{{user `recipe`}}_{{user `build_number`}}_{{isotime \"2006/01/02_15-04-05\"}}",
           "ami_description": "AMI for {{user `recipe`}} built by Amigo: #{{user `build_number`}}",
           // TODO ami_users, iam_instance_profile
           "tags": {
             "Name": "{{user `recipe`}}_{{user `build_number`}}_{{isotime \"2006/01/02_15-04-05\"}}",
             "Recipe": "{{user `recipe`}}",
             "Build":"{{user `build_number`}}",
             "SourceAMI":"{{user `base_image_ami_id`}}"
           }
         }
       ],
       "provisioners": [
         {
           "type": "shell",
           "inline": [
             ... base image's init script
           ]
         },
         {
           ... copy required features to /tmp
         },
         {
           ... install all features, in correct dependency order
         },
         {
           "type": "shell",
           "inline": [
             "# clean up /tmp"
             "/usr/bin/sudo rm -rf /tmp/features"
           ]
         }
       ]
     }
   * }}}
   * // format: ON
   */
  def generatePackerBuildConfig(bake: Bake): PackerBuildConfig = {
    val variables = Map(
      "recipe" -> bake.recipe.id.value,
      "base_image_ami_id" -> bake.recipe.baseImage.amiId.value,
      "build_number" -> bake.buildNumber.toString
    )
    val builder = PackerBuilderConfig(
      name = "{{user `recipe`}}",
      `type` = "amazon-ebs",
      region = "eu-west-1",
      source_ami = "{{user `base_image_ami_id`}}",
      instance_type = "t2.micro",
      ssh_username = "ubuntu",
      run_tags = Map(
        "Stage" -> "INFRA",
        "Stack" -> "amigo-packer",
        "App" -> "{{user `recipe`}}"
      ),
      ami_name = "amigo_{{user `recipe`}}_{{user `build_number`}}_{{isotime \"2006/01/02_15-04-05\"}}",
      ami_description = "AMI for {{user `recipe`}} built by Amigo: #{{user `build_number`}}",
      tags = Map(
        "Name" -> "{{user `recipe`}}_{{user `build_number`}}_{{isotime \"2006/01/02_15-04-05\"}}",
        "Recipe" -> "{{user `recipe`}}",
        "Build" -> "{{user `build_number`}}",
        "SourceAMI" -> "{{user `base_image_ami_id`}}"
      )
    )
    val provisioners = Seq(
      // TODO
    )
    PackerBuildConfig(
      variables,
      Seq(builder),
      provisioners
    )
  }

}
