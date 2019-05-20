package packer

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

import ansible.PlaybookGenerator
import attempt.{ Attempt, UnknownFailure }
import event.EventBus
import models.Bake
import models.packer.PackerVariablesConfig
import play.api.libs.json.Json
import services.{ Loggable, PrismAgents }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.util.Try
import scala.util.control.NonFatal

object PackerRunner extends Loggable {

  private val packerCmd = sys.props.get("packerHome").map(ph => s"$ph/packer").getOrElse("packer")

  /**
   * Starts a Packer process to create an image using the given recipe.
   *
   * @return a Future of the process's exit value
   */
  def createImage(bake: Bake, prism: PrismAgents, eventBus: EventBus, ansibleVars: Map[String, String], debug: Boolean)(implicit packerConfig: PackerConfig): Attempt[Int] = {
    val playbookYaml = PlaybookGenerator.generatePlaybook(bake.recipe, ansibleVars)
    val playbookFile = Files.createTempFile(s"amigo-ansible-${bake.recipe.id.value}", ".yml")
    Files.write(playbookFile, playbookYaml.getBytes(StandardCharsets.UTF_8)) // TODO error handling

    for {
      awsAccounts <- prism.accounts
      awsAccountNumbers = awsAccounts.map(_.accountNumber)
      _ = log.info(s"AMI will be shared with the following AWS accounts: $awsAccountNumbers")
      packerVars = PackerVariablesConfig(bake)
      packerBuildConfig = PackerBuildConfigGenerator.generatePackerBuildConfig(bake, playbookFile, packerVars, awsAccountNumbers)
      packerJson = Json.prettyPrint(Json.toJson(packerBuildConfig))
      packerConfigFile = Files.createTempFile(s"amigo-packer-${bake.recipe.id.value}", ".json")
      _ = Files.write(packerConfigFile, packerJson.getBytes(StandardCharsets.UTF_8)) // TODO error handling
      exitCode <- executePacker(bake, playbookFile, packerConfigFile, eventBus, debug)
    } yield exitCode
  }

  private def executePacker(bake: Bake, playbookFile: Path, packerConfigFile: Path, eventBus: EventBus, debug: Boolean): Attempt[Int] = {
    val maybeDebug = if (debug) Some("-debug") else None
    val command = Seq(packerCmd, "build", maybeDebug, "-machine-readable", packerConfigFile.toAbsolutePath.toString) collect {
      case s: String => s
      case Some(s: String) => s
    }
    val packerProcess = new ProcessBuilder()
      .command(command: _*)
      .directory(new File(System.getProperty("java.io.tmpdir")))
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

    Attempt.fromFuture(exitValueFuture) {
      case NonFatal(t) => UnknownFailure(t)
    }
  }

}
