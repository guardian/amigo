package features

import java.nio.file.{ Path, Files, Paths }

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import models.{ FeatureId, Feature }

import scala.collection.JavaConverters._

/**
 * Proof of concept that just loads features from the local disk
 */
object FeaturesRepository {

  val rootDir = Paths.get("features")

  val features = loadFeatures()

  private def loadFeatures(): Seq[Feature] = {
    Files.list(rootDir).iterator.asScala.toSeq.collect {
      case path if looksLikeValidFeatureDir(path) => loadFeatureFromDisk(path)
    }
  }

  private def looksLikeValidFeatureDir(path: Path): Boolean = {
    Files.isDirectory(path) && Files.isRegularFile(path.resolve("feature.yaml"))
  }

  private def loadFeatureFromDisk(path: Path): Feature = {
    // TODO validation, error handling
    val metadataFile = path.resolve("feature.yaml")
    val yaml = new ObjectMapper(new YAMLFactory()).readTree(metadataFile.toFile)
    val id = FeatureId(path.getFileName.toString)
    val description = yaml.get("description").asText("")
    val dependencies = yaml.get("dependencies").elements().asScala.map(x => FeatureId(x.asText())).toSet
    Feature(id, description, dependencies, path)
  }

}
