package models.packer

import models.Bake
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.Json

case class PackerVariablesConfig(
  recipe: String,
  base_image_ami_id: String,
  build_number: String,
  time: String)

object PackerVariablesConfig {
  implicit val jsonWrites = Json.writes[PackerVariablesConfig]

  val format = DateTimeFormat.forPattern("yyyy/MM/dd_HH-mm-ss")
  def apply(bake: Bake): PackerVariablesConfig = {
    PackerVariablesConfig(
      recipe = bake.recipe.id.value,
      base_image_ami_id = bake.recipe.baseImage.amiId.value,
      build_number = bake.buildNumber.toString,
      time = format.print(bake.startedAt)
    )
  }
}
