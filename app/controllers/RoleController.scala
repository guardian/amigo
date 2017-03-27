package controllers

import com.gu.googleauth.GoogleAuthConfig
import data._
import play.api.mvc._

class RoleController(val authConfig: GoogleAuthConfig)(implicit dynamo: Dynamo) extends Controller with AuthActions {

  def listRoles = AuthAction {
    Ok(views.html.roles(Roles.list, Recipes.list()))
  }

}

