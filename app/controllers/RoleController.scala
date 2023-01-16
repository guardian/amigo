package controllers

import com.gu.googleauth.AuthAction
import data._
import play.api.mvc._

class RoleController(
    val authAction: AuthAction[AnyContent],
    components: ControllerComponents
)(implicit dynamo: Dynamo)
    extends AbstractController(components) {

  def listRoles = authAction {
    Ok(views.html.roles(Roles.list, Recipes.list(), BaseImages.list().toSeq))
  }

}
