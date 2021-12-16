package controllers

import amigo.BuildInfo
import com.gu.googleauth.GoogleAuthConfig
import play.api.libs.json.Json
import play.api.mvc._
import services.Loggable

class RootController(val authConfig: GoogleAuthConfig) extends Controller with AuthActions with Loggable {

  def healthcheck = Action {
    log.info("Developer Tooling Intro - demo")
    Ok(Json.parse(BuildInfo.toJson))
  }

  def index = AuthAction {
    Ok(views.html.index())
  }

}

