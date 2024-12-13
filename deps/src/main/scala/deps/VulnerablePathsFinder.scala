package deps

import deps.Queries.dependencyGraphQuery
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.{Decoder, DecodingFailure}
import sttp.client3._
import sttp.client3.circe._
import sttp.model.MediaType

object VulnerablePathsFinder {

  private val githubApiUrl: String = "https://api.github.com/graphql"

  // Case classes for GraphQL response
  private case class DependencyNode(
      packageName: String,
      requirements: String,
      hasDependencies: Boolean
  )
  private case class DependencyEdge(node: DependencyNode)
  private case class DependencyEdgeWrapper(edges: List[DependencyEdge])
  private case class DependencyManifestNode(
      filename: String,
      dependencies: Option[DependencyEdgeWrapper]
  )
  private case class DependencyManifestEdge(node: DependencyManifestNode)
  private case class DependencyGraphManifests(
      edges: List[DependencyManifestEdge]
  )
  private case class Repository(
      dependencyGraphManifests: DependencyGraphManifests
  )
  private case class RepositoryWrapper(repository: Repository)

  private case class GraphQLResponse(
      data: Option[RepositoryWrapper],
      errors: Option[List[Map[String, String]]]
  )

  // Case classes for Dependabot alerts response
  private case class DependabotAlert(
      dependency: Dependency,
      security_advisory: SecurityAdvisory
  )
  private case class DependencyPackage(ecosystem: String, name: String)
  private case class Dependency(`package`: DependencyPackage)
  private case class SecurityAdvisory(description: String, severity: String)

  // Fetch data from the GraphQL API
  private def fetchDependencyGraph(
      token: String,
      owner: String,
      repo: String
  ): Repository = {
    val backend = HttpURLConnectionBackend()
    val query = dependencyGraphQuery(owner, repo)
    val request = basicRequest
      .post(uri"$githubApiUrl")
      .contentType(MediaType.ApplicationJson)
      .auth
      .bearer(token)
      .body(
        s"""{"query": "${query.replace("\n", "\\n").replace("\"", "\\\"")}"}"""
      )

    val response = request.send(backend)
    response.body match {
      case Right(body) =>
        decodeWithErrorMessage[GraphQLResponse](body) match {
          case Right(graphQLResponse) =>
            graphQLResponse.data.get.repository
          case Left(error) =>
            throw new Exception(s"Failed to decode JSON response: $error")
        }
      case Left(error) => throw new Exception(s"GraphQL Error: $error")
      case _ => throw new Exception("Failed to fetch dependency graph")
    }
  }

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

  // Fetch Dependabot alerts
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

  // Cross-reference and find paths
  private def findVulnerablePaths(
      dependencyGraph: Repository,
      alerts: List[DependabotAlert]
  ): List[String] = {
    val vulnerablePackages = alerts.map(_.dependency.`package`.name).toSet

    // Traverse dependency graph and collect paths to vulnerable dependencies
    def traverse(
        manifest: DependencyManifestNode,
        path: List[String] = Nil
    ): List[List[String]] = {
      val maybeEdges = manifest.dependencies.map(_.edges)
      maybeEdges.getOrElse(Nil).flatMap { edge =>
        val packageName = edge.node.packageName
        val currentPath = path :+ packageName
        if (vulnerablePackages.contains(packageName)) {
          List(currentPath)
        } else if (edge.node.hasDependencies) {
          traverse(manifest, currentPath)
        } else {
          Nil
        }
      }
    }

    dependencyGraph.dependencyGraphManifests.edges.flatMap { manifest =>
      traverse(manifest.node).map(_.mkString(" > "))
    }
  }

  def main(args: Array[String]): Unit = {
    val githubToken = sys.env("GITHUB_TOKEN")
    val owner = sys.env("REPO_OWNER")
    val repo = sys.env("REPO_NAME")

    val dependencyGraph = fetchDependencyGraph(githubToken, owner, repo)
    val dependabotAlerts =
      fetchDependabotAlerts(githubToken, owner, repo).filter(x =>
        x.dependency.`package`.ecosystem == "maven" && x.security_advisory.severity == "critical"
      )
    val paths = findVulnerablePaths(dependencyGraph, dependabotAlerts)
    println("Paths to vulnerable dependencies:")
    paths.foreach(println)
  }
}
