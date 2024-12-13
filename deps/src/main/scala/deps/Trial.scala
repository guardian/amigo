package deps

import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.parser.parse

import scala.math.Ordering.Implicits.seqOrdering

object Trial {

  case class Dependency(
      name: String,
      scope: Option[String],
      direct: Option[Boolean]
  )

  def main(args: Array[String]): Unit = {

//    val dep = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.3"
//    val dep = "org.playframework:play-guice_2.13:3.0.6"
    val dep = "org.playframework:play-ws-standalone_2.13:3.0.6"

    val snapshot = scala.io.Source
      .fromFile("deps/src/main/scala/deps/snapshot.json")
      .mkString

    val json = parse(snapshot) match {
      case Right(parsedJson) => parsedJson
      case Left(error) => throw new RuntimeException(s"Invalid JSON: $error")
    }

    val resolved: Map[String, Json] = root.manifests.each.resolved.json
      .getAll(json)
      .flatMap(_.asObject.map(_.toMap).getOrElse(Map.empty))
      .toMap

    def f(s: Dependency): List[List[Dependency]] = {

      def reversePaths(
          current: Dependency,
          path: List[Dependency]
      ): List[List[Dependency]] = {

        if (current.direct.getOrElse(false)) {
          // If direct dependency, we've reached the end of a reverse path
          List(current :: path)
        } else {
          val parents = resolved
            .filter { case (_, children) =>
              root.dependencies.each.string
                .getAll(children)
                .contains(current.name)
            }
            .map { case (k, v) =>
              Dependency(
                k,
                root.metadata.config.string.getOption(v),
                root.relationship.string.getOption(v).map(_ == "direct")
              )
            }

          if (parents.isEmpty) {
            // If there are no parents, we've reached the end of a reverse path
            List(current :: path)
          } else {
            // Otherwise, continue finding reverse paths for each parent
            parents.toList.flatMap(parent =>
              reversePaths(parent, current :: path)
            )
          }
        }
      }

      reversePaths(s, Nil)
    }

    val packages: List[List[Dependency]] = f(Dependency(dep, None, None))
    // dedup list
    val packages2 = packages.distinct.sortBy(x => x.map(_.name))

    for (p <- packages2) {
      for (d <- p) {
        println(
          s"${d.name} (${d.scope.getOrElse("unknown")}, ${d.direct.getOrElse("unknown")}) >"
        )
      }
      println
    }
  }
}
