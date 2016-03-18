package packer

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import ansible.PlaybookGenerator
import event.EventBus
import models.Bake
import play.api.libs.json.Json

import scala.concurrent.{ Future, Promise }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

object PackerRunner {

  private val packerCmd = sys.env.get("PACKER_HOME").map(ph => s"$ph/packer").getOrElse("packer")

  /**
   * Starts a Packer process to create an image using the given recipe.
   *
   * @return a Future of the process's exit value
   */
  def createImage(bake: Bake, eventBus: EventBus): Future[Int] = {
    val playbookYaml = PlaybookGenerator.generatePlaybook(bake.recipe)
    println(playbookYaml)
    val playbookFile = Files.createTempFile(s"amigo-ansible-${bake.recipe.id.value}", ".yml")
    Files.write(playbookFile, playbookYaml.getBytes(StandardCharsets.UTF_8)) // TODO error handling
    println(s"Wrote Playbook file to $playbookFile")

    val packerBuildConfig = PackerConfigGenerator.generatePackerBuildConfig(bake, playbookFile)
    val packerJson = Json.prettyPrint(Json.toJson(packerBuildConfig))
    val packerConfigFile = Files.createTempFile(s"amigo-packer-${bake.recipe.id.value}", ".json")
    Files.write(packerConfigFile, packerJson.getBytes(StandardCharsets.UTF_8)) // TODO error handling
    println(s"Wrote Packer json to $packerConfigFile")

    val packerProcess = new ProcessBuilder()
      .command(packerCmd, "build", "-machine-readable", packerConfigFile.toAbsolutePath.toString)
      .start()

    val exitValuePromise = Promise[Int]()

    val runnable = new Runnable {
      def run(): Unit = PackerProcessMonitor.monitorProcess(packerProcess, exitValuePromise, bake.bakeId, eventBus)
    }
    val listenerThread = new Thread(runnable, s"Packer process monitor for ${bake.recipe.id.value} #${bake.buildNumber}")
    listenerThread.setDaemon(true)
    listenerThread.start()

    val exitValueFuture = exitValuePromise.future

    // Make sure to delete the tmp files after Packer completes, regardless of success or failure
    exitValueFuture.onComplete {
      case _ =>
        Try(Files.deleteIfExists(playbookFile))
        Try(Files.deleteIfExists(packerConfigFile))
    }

    exitValueFuture
  }

}
