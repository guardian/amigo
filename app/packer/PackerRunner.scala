package packer

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import models.Bake
import play.api.libs.json.Json

import scala.concurrent.{ Future, Promise }
import scala.concurrent.ExecutionContext.Implicits.global

object PackerRunner {

  /**
   * Starts a Packer process to create an image using the given recipe.
   *
   * @return a Future of the process's exit value
   */
  def createImage(bake: Bake, packerListener: PackerListener): Future[Int] = {
    val packerBuildConfig = PackerConfigGenerator.generatePackerBuildConfig(bake)
    val packerJson = Json.prettyPrint(Json.toJson(packerBuildConfig))
    val tmpFile = Files.createTempFile(s"amigo-packer-${bake.recipe.id.value}", ".json")
    Files.write(tmpFile, packerJson.getBytes(StandardCharsets.UTF_8)) // TODO error handling
    println(s"Wrote Packer json to $tmpFile")

    val packerProcess = new ProcessBuilder()
      .command("packer", "build", "-machine-readable", tmpFile.toAbsolutePath.toString)
      .start()

    val exitValuePromise = Promise[Int]()

    val runnable = new Runnable {
      def run(): Unit = PackerProcessMonitor.monitorProcess(packerProcess, exitValuePromise, packerListener)
    }
    val listenerThread = new Thread(runnable, s"Packer process monitor for ${bake.recipe.id.value} #${bake.buildNumber}")
    listenerThread.setDaemon(true)
    listenerThread.start()

    val exitValueFuture = exitValuePromise.future

    // Make sure to delete the tmp file after Packer completes, regardless of success or failure
    exitValueFuture.onComplete {
      case _ => Files.deleteIfExists(tmpFile)
    }

    exitValueFuture
  }

}
