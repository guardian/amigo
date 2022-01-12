package controllers

import com.gu.googleauth.GoogleAuthConfig
import data._
import play.api.mvc._

class RoleController(val authConfig: GoogleAuthConfig, components: ControllerComponents)(implicit dynamo: Dynamo) extends AbstractController(components) with AuthActions {

  def listRoles = AuthAction {
    Ok(views.html.roles(Roles.list, Recipes.list(), BaseImages.list().toSeq))
  }

}

