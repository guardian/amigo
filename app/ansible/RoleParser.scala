package ansible

import java.nio.file.{ Files, Path }

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import models.{ Markdown, RoleId, RoleSummary, Yaml }

import scala.collection.JavaConverters._
import scala.util.Try

object RoleParser {

  private val yamlFactory = new YAMLFactory()

  def createRoleSummary(roleDir: Path): Option[RoleSummary] = {
    val roleId = RoleId(roleDir.getFileName.toString)
    val dependsOn = parseDependencies(roleDir.resolve("meta/main.yml"))
    val tasksMain = readYamlFile(roleDir.resolve("tasks/main.yml"))
    val readme = readReadme(roleDir.resolve("README.md"))

    for {
      yaml <- tasksMain
    } yield {
      RoleSummary(roleId, dependsOn, yaml, readme)
    }
  }

  def readReadme(path: Path): Option[Markdown] = {
    if (Files.isRegularFile(path)) {
      Try(Files.readAllLines(path)).toOption
        .map(lines => Markdown(lines.asScala.mkString("\n")))
    } else None
  }

  def parseDependencies(metaMainYaml: Path): Set[RoleId] = {
    readYamlFile(metaMainYaml).map(parseDependenciesFromYaml).getOrElse(Set.empty)
  }

  /**
   * Extracts the role IDs from YAML that looks like this:
   *
   * {{{
   * ---
   * dependencies:
   *   - foo
   *   - { role: bar, baz: wow }
   * }}}
   */
  def parseDependenciesFromYaml(yaml: Yaml): Set[RoleId] = {
    val mapper = new ObjectMapper(yamlFactory)
    val jmap = mapper.readValue(yaml.content, classOf[java.util.Map[String, Any]])
    val deps = jmap.asScala.get("dependencies").map(_.asInstanceOf[java.util.List[Any]].asScala).getOrElse(Nil)
    deps.collect {
      case roleId: String => Some(RoleId(roleId))
      case customisedRole: java.util.Map[String, String] @unchecked => customisedRole.asScala.get("role").map(RoleId(_))
    }.flatten.toSet
  }

  private def readYamlFile(path: Path): Option[Yaml] = {
    if (Files.isRegularFile(path)) {
      Try(Files.readAllLines(path)).toOption
        .map(lines => Yaml(lines.asScala.mkString("\n")))
    } else None
  }

}
