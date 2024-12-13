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

    // recursively find packages whose dependencies include packages whose dependencies include the specified dependency
    // continue until arrive at direct dependencies
    def go(depName: String, acc: List[String]): List[String] = {
      val packages: Map[String, Json] = resolved.filter { case (_, value) =>
        root.dependencies.each.string.getAll(value).toList.contains(depName)
      }
      if (packages.isEmpty) acc
      else {
        val next = packages.keys.toList.head
        go(next, acc :+ next)
//        for (n <- next) yield go(n, acc :+ n)
      }
    }

//    val packages2: List[List[String]] = go(dep, Nil)
    val packages2: List[String] = go(dep, List(dep))

    println(packages2.reverse.mkString(" > \n"))
  }
}
