package packer

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import models.Bake
import play.api.libs.json.Json

object PackerRunner {

  /**
   * Starts a Packer process to create an image using the given recipe.
   *
   * Commits the ultimate sin, i.e. returns Unit, because there is nothing that
   * could be usefully returned. It spawns a new process that communicates its progress
   * back to the provided listener.
   */
  def createImage(bake: Bake, packerListener: PackerListener): Unit = {
    val packerBuildConfig = PackerConfigGenerator.generatePackerBuildConfig(bake)
    val packerJson = Json.prettyPrint(Json.toJson(packerBuildConfig))
    val tmpFile = Files.createTempFile(s"amigo-packer-${bake.recipe.id.value}", ".json")
    Files.write(tmpFile, packerJson.getBytes(StandardCharsets.UTF_8)) // TODO error handling
    println(s"Wrote Packer json to $tmpFile")
    // TODO spawn a Packer process, plus a monitor thread that consumes its stdout and sends updates to the listener
    // TODO make sure to delete the tmp file after Packer completes, regardless of success or failure
  }

}
