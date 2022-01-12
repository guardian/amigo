package controllers

import amigo.BuildInfo
import com.gu.googleauth.GoogleAuthConfig
import play.api.libs.json.Json
import play.api.mvc._

class RootController(val authConfig: GoogleAuthConfig, components: ControllerComponents) extends AbstractController(components) with AuthActions {

  def healthcheck = Action {
    Ok(Json.parse(BuildInfo.toJson))
  }

  def index = AuthAction {
    Ok(views.html.index())
  }

}

