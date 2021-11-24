package controllers

import amigo.BuildInfo
import com.gu.googleauth.GoogleAuthConfig
import play.api.libs.json.Json
import play.api.mvc._

class RootController(val authConfig: GoogleAuthConfig) extends Controller with AuthActions {

  def healthcheck = Action {
    println("hi")
    Ok(Json.parse(BuildInfo.toJson))
  }

  def index = AuthAction {
    Ok(views.html.index())
  }

}

