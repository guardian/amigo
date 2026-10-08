package e2e

import play.api.BuiltInComponents
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Handler, RequestHeader, Results}

private[e2e] object E2EPrismStub {
  private val responses: Map[String, JsValue] = Map(
    "/sources" -> Json.obj(
      "stale" -> false,
      "data" -> Json.arr(
        Json.obj(
          "origin" -> Json.obj(
            "accountName" -> "E2E",
            "accountNumber" -> "000000000000"
          )
        )
      )
    ),
    "/instances" -> empty("instances"),
    "/launch-configurations" -> empty("launch-configurations"),
    "/active-launch-template-versions" -> empty(
      "active-launch-template-versions"
    ),
    "/images" -> empty("images")
  )

  private def empty(name: String): JsValue =
    Json.obj("stale" -> false, "data" -> Json.obj(name -> Json.arr()))

  def routes(
      components: BuiltInComponents
  ): PartialFunction[RequestHeader, Handler] = {
    case request
        if request.method == "GET" && responses.contains(request.path) =>
      components.defaultActionBuilder {
        Results.Ok(responses(request.path))
      }
  }
}
