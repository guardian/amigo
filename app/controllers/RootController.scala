package controllers

import amigo.BuildInfo
import com.gu.googleauth.AuthAction
import play.api.libs.json.Json
import play.api.mvc._

class RootController(val authAction: AuthAction[AnyContent], components: ControllerComponents) extends AbstractController(components) {

  def healthcheck = Action {
    Ok(Json.parse(BuildInfo.toJson))
  }

  def index = authAction {
    Ok(views.html.index())
  }

}

