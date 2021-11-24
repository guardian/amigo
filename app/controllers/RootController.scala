package controllers

import amigo.BuildInfo
import com.gu.googleauth.GoogleAuthConfig
import play.api.libs.json.Json
import play.api.mvc._
import services.Loggable
import java.time.LocalDate

class RootController(val authConfig: GoogleAuthConfig) extends Controller with AuthActions with Loggable {

  def healthcheck = Action {
    log.info(LocalDate.now)
    Ok(Json.parse(BuildInfo.toJson))
  }

  def index = AuthAction {
    Ok(views.html.index())
  }

}

