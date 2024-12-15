package deps

import io.circe.generic.auto._
import io.circe.jawn.decode
import io.circe.{Decoder, DecodingFailure}
import sttp.client3._
import io.circe.Json
import io.circe.optics.JsonPath.root
import io.circe.parser.parse

import scala.math.Ordering.Implicits.seqOrdering

object Final {

  // TODO config
  private val githubToken = sys.env("GITHUB_TOKEN")
  private val owner = sys.env("REPO_OWNER")
  private val repo = sys.env("REPO_NAME")
  // TODO: fetch from env
  private val snapshot2 = parse(
    scala.io.Source
      .fromFile("deps/src/main/scala/deps/snapshot.json")
      .mkString
  ).getOrElse(Json.Null)

  // Case classes for Dependabot alerts response
  private case class DependabotAlert(
      dependency: Dependency,
      security_advisory: SecurityAdvisory
  )
  private case class DependencyPackage(ecosystem: String, name: String)
  private case class Dependency(`package`: DependencyPackage)
  private case class SecurityAdvisory(description: String, severity: String)

  private def decodeWithErrorMessage[A: Decoder](
      input: String
  ): Either[String, A] = {
    decode[A](input).left.map {
      case failure: DecodingFailure =>
        s"Decoding failed with error: ${failure.getMessage}\nMissing field: ${failure.history.map(_.toString).mkString(" -> ")}\nInput: $input"
      case error =>
        s"Decoding failed with error: ${error.getMessage}\nInput: $input"
    }
  }

  // TODO: use requests lib
  // TODO: move to github service
  // TODO; get more fields out of the response
  private def fetchDependabotAlerts(
      token: String,
      owner: String,
      repo: String
  ): List[DependabotAlert] = {
    val backend = HttpURLConnectionBackend()
    val request = basicRequest
      .get(uri"https://api.github.com/repos/$owner/$repo/dependabot/alerts")
      .header("Authorization", s"Bearer $token")
      .header("Accept", "application/vnd.github+json")
    //      .response(asJson[List[DependabotAlert]])

    request.send(backend).body match {
      case Right(alerts) =>
        decodeWithErrorMessage[List[DependabotAlert]](alerts) match {
          case Right(decodedAlerts) => decodedAlerts
          case Left(error) =>
            throw new Exception(s"Failed to decode JSON response: $error")
        }
      case Left(error) => throw new Exception(s"REST API Error: $error")
    }
  }

  case class SnapshotDependency(
      name: String,
      scope: Option[String],
      direct: Option[Boolean]
  )

  def pathsToDependency(
      snapshot: Json,
      depName: String
  ): List[List[SnapshotDependency]] = {
    val resolved: Map[String, Json] = root.manifests.each.resolved.json
      .getAll(snapshot)
      .flatMap(_.asObject.map(_.toMap).getOrElse(Map.empty))
      .toMap

    def pathTo(s: SnapshotDependency): List[List[SnapshotDependency]] = {

      def reversePaths(
          current: SnapshotDependency,
          path: List[SnapshotDependency]
      ): List[List[SnapshotDependency]] = {

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
            .map { case (pkgName, json) =>
              SnapshotDependency(
                pkgName,
                root.metadata.config.string.getOption(json),
                root.relationship.string.getOption(json).map(_ == "direct")
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

    // Simplification: take the first package that matches the depName
    val initialPackage = resolved
      .find { case (pkgName, _) =>
        val versionIndex = pkgName.lastIndexOf(':')
        val pkgNameWithoutVersion = pkgName.substring(0, versionIndex)
        pkgNameWithoutVersion == depName
      }
      .map { case (pkgName, json) =>
        SnapshotDependency(
          pkgName,
          root.metadata.config.string.getOption(json),
          root.relationship.string.getOption(json).map(_ == "direct")
        )
      }
      .getOrElse(
        throw new Exception(s"Dependency $depName not found in snapshot")
      )

    val packages: List[List[SnapshotDependency]] = pathTo(initialPackage)

    // dedup list
    val packages2 = packages.distinct.sortBy(_.map(_.name))

    packages2
  }

  def main(args: Array[String]): Unit = {

    val dependabotAlerts =
      fetchDependabotAlerts(githubToken, owner, repo).filter(x =>
        x.dependency.`package`.ecosystem == "maven" && x.security_advisory.severity == "critical"
//        x.dependency.`package`.ecosystem == "maven" //&& x.security_advisory.severity == "low"
      )

    println(s"Dependabot alerts for $owner/$repo:")
    dependabotAlerts.foreach { alert =>
      println(
//        s" - ${alert.dependency.`package`.name}: ${alert.security_advisory.description}"
        s" - ${alert.dependency.`package`.name}"
      )
      pathsToDependency(snapshot2, alert.dependency.`package`.name).foreach {
        path =>
          println(
            path
              .map(d =>
                s"${d.name} (${d.scope.getOrElse("unknown")}, ${d.direct.getOrElse("unknown")})"
              )
              .mkString(" > ")
          )
      }
    }
  }
}
