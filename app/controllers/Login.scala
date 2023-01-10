package controllers

import com.gu.googleauth.{ GoogleAuthConfig, LoginSupport }
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext
import com.gu.googleauth.GoogleGroupChecker

class Login(
  val authConfig: GoogleAuthConfig,
  override val wsClient: WSClient,
  components: ControllerComponents,
  requiredGoogleGroups: Set[String],
  groupChecker: GoogleGroupChecker,
)(implicit executionContext: ExecutionContext) extends AbstractController(components) with LoginSupport {

  def loginAction = Action.async { implicit request =>
    startGoogleLogin()
  }

  def oauth2Callback = Action.async { implicit request =>
    processOauth2Callback(requiredGoogleGroups, groupChecker)
  }

  override val failureRedirectTarget: Call = routes.Login.loginAction()
  override val defaultRedirectTarget: Call = routes.RootController.index()

}