package deps

import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.parser.parse

object Trial {
  def main(args: Array[String]): Unit = {

    val dep = "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.3"

    val snapshot = scala.io.Source
      .fromFile(
        "/Users/Kelvin_Chappell/code/amigo/deps/src/main/scala/deps/snapshot.json"
      )
      .mkString

    val json = parse(snapshot) match {
      case Right(parsedJson) => parsedJson
      case Left(error) => throw new RuntimeException(s"Invalid JSON: $error")
    }

    val resolved: Map[String, Json] = root.manifests.each.resolved.json
      .getAll(json)
      .flatMap(_.asObject.map(_.toMap).getOrElse(Map.empty))
      .toMap

    def f(s: String): List[List[String]] = {

      def reversePaths(
          current: String,
          path: List[String]
      ): List[List[String]] = {
        val parents = resolved.filter { case (_, children) =>
          root.dependencies.each.string.getAll(children).contains(current)
        }.keys

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

      reversePaths(s, Nil)
    }

    val packages2: List[List[String]] = f(dep)

    for (p <- packages2) {
      println(p.reverse.mkString(" > \n"))
      println
    }
  }
}
