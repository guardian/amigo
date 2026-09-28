package e2e

import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.libs.json.Json
import play.api.mvc.{EssentialFilter, Results}
import play.api.routing.Router
import play.api.routing.sird._

final class E2EComponents(
    context: Context,
    startupConfig: E2EStartupConfig
) extends BuiltInComponentsFromContext(context) {

  override lazy val httpFilters: Seq[EssentialFilter] = Seq.empty

  override lazy val router: Router = Router.from { case GET(p"/healthcheck") =>
    defaultActionBuilder {
      Results.Ok(
        Json.obj(
          "status" -> "ok",
          "scenario" -> startupConfig.scenario.name
        )
      )
    }
  }
}
