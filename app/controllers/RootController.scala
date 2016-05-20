package controllers

import com.gu.googleauth.GoogleAuthConfig

import play.api.mvc._

class RootController(val authConfig: GoogleAuthConfig) extends Controller with AuthActions {

  def healthcheck = Action {
    Ok("OK")
  }

  def index = AuthAction {
    Ok(views.html.index())
  }

}

