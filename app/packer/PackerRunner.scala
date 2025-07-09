package packer

import ansible.PlaybookGenerator
import event.EventBus
import models.Bake
import models.packer.PackerVariablesConfig
import org.apache.http.concurrent.BasicFuture
import play.api.libs.json.Json
import services.{AmiMetadataLookup, Loggable, PrismData}

import java.io.{File, IOException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.jdk.CollectionConverters._
import scala.jdk.StreamConverters._
import scala.util.Try

class PackerRunner(maxInstances: Int) extends Loggable {

  private val packerCmd =
    sys.props.get("packerHome").map(ph => s"$ph/packer").getOrElse("packer")

  /** Starts a Packer process to create an image using the given recipe.
    *
    * @return
    *   a Future of the process's exit value
    */
  def createImage(
      stage: String,
      bake: Bake,
      prism: PrismData,
      eventBus: EventBus,
      ansibleVars: Map[String, String],
      debug: Boolean,
      amiMetadataLookup: AmiMetadataLookup,
      amigoDataBucket: Option[String]
  )(implicit packerConfig: PackerConfig): Future[Int] = {
    val sourceAmi = bake.recipe.baseImage.amiId.value
    val amiMetadata = amiMetadataLookup
      .lookupMetadataFor(sourceAmi)
      .getOrElse(
        throw new IllegalStateException(
          s"Unable to identify the architecture for $sourceAmi"
        )
      )

    val playbookYaml = PlaybookGenerator.generatePlaybook(
      bake.recipe,
      ansibleVars ++ Map(
        "arch" -> amiMetadata.architecture,
        "deb_arch" -> amiMetadata.debArchitecture
      )
    )
    val playbookFile =
      Files.createTempFile(s"amigo-ansible-${bake.recipe.id.value}", ".yml")
    Files.write(
      playbookFile,
      playbookYaml.getBytes(StandardCharsets.UTF_8)
    ) // TODO error handling

    val awsAccountNumbers = prism.accounts.map(_.accountNumber)

    val packerVars = PackerVariablesConfig(bake)
    val packerBuildConfig =
      PackerBuildConfigGenerator.generatePackerBuildConfig(
        stage,
        bake,
        playbookFile,
        packerVars,
        awsAccountNumbers,
        amiMetadata,
        amigoDataBucket,
        bake.recipe.baseImage.requiresXLargeBuilder
      )
    val packerJson = Json.prettyPrint(Json.toJson(packerBuildConfig))
    val packerConfigFile =
      Files.createTempFile(s"amigo-packer-${bake.recipe.id.value}", ".json")
    Files.write(
      packerConfigFile,
      packerJson.getBytes(StandardCharsets.UTF_8)
    ) // TODO error handling

    executePacker(bake, playbookFile, packerConfigFile, eventBus, debug)
  }

  private def executePacker(
      bake: Bake,
      playbookFile: Path,
      packerConfigFile: Path,
      eventBus: EventBus,
      debug: Boolean
  ): Future[Int] = {
    val maybeDebug = if (debug) Some("-debug") else None
    val command = Seq(
      packerCmd,
      "build",
      maybeDebug,
      "-machine-readable",
      packerConfigFile.toAbsolutePath.toString
    ) collect {
      case s: String       => s
      case Some(s: String) => s
    }
    val packerCacheDir =
      Files.createTempDirectory(s"amigo-packer-cache-${bake.recipe.id.value}")
    val packerBuilder = new ProcessBuilder()
      .command(command: _*)
      .directory(new File(System.getProperty("java.io.tmpdir")))
    packerBuilder
      .environment()
      .putAll(
        Map(
          "PACKER_CACHE_DIR" -> packerCacheDir.toAbsolutePath.toString,
          "PACKER_PLUGIN_PATH" -> "/opt/packer/.plugins"
        ).asJava
      )
    val packerProcess = packerBuilder.start()

    val exitValuePromise = Promise[Int]()

    val runnable = new Runnable {
      def run(): Unit = try {
        PackerProcessMonitor.monitorProcess(
          packerProcess,
          exitValuePromise,
          bake.bakeId,
          eventBus
        )
      } finally {
        startNextPacker(_ -= this)
      }
    }
    val listenerThread = new Thread(
      runnable,
      s"Packer process monitor for ${bake.recipe.id.value} #${bake.buildNumber}"
    )
    listenerThread.setDaemon(true)
    startNextPacker(_ += runnable -> listenerThread)

    val exitValueFuture = exitValuePromise.future

    // Make sure to delete the tmp files after Packer completes, regardless of success or failure
    exitValueFuture.onComplete { _ =>
      Try(Files.deleteIfExists(playbookFile))
        .fold(log.error("Failed to delete playbook file", _), _ => ())
      Try(Files.deleteIfExists(packerConfigFile))
        .fold(log.error("Failed to delete config file", _), _ => ())
      Try(
        Files
          .walk(packerCacheDir)
          .map(_.toFile)
          .toScala(List)
          .reverse
          .foreach(_.delete)
      ).fold(log.error("Failed to delete cache directory", _), _ => ())
    }

    exitValueFuture
  }

  private val packerProcesses = mutable.LinkedHashMap.empty[Runnable, Thread]

  private def startNextPacker(
      modify: mutable.LinkedHashMap[Runnable, Thread] => Unit
  ): Unit = {
    packerProcesses.synchronized {
      modify(packerProcesses)
      val running: Int = packerProcesses.count { case (_, thread) =>
        thread.isAlive
      }
      val toStart: List[Thread] = packerProcesses.toList
        .map { case (_, thread) => thread }
        .filterNot { _.isAlive }
        .take(maxInstances - running)
      log.info(
        s"AMIgo current bake total: ${packerProcesses.size}, running: $running, starting: ${toStart.length}"
      )
      toStart.foreach { _.start() }
    }
  }

}
