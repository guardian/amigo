package controllers

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.applicativeError._
import com.gu.googleauth.{
  GoogleAuthConfig,
  GoogleGroupChecker,
  LoginSupport,
  UserIdentity
}
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.Loggable

import scala.concurrent.{ExecutionContext, Future}

class Login(
    val authConfig: GoogleAuthConfig,
    override val wsClient: WSClient,
    components: ControllerComponents,
    googleGroupsToCheck: Set[String],
    groupChecker: GoogleGroupChecker
)(implicit executionContext: ExecutionContext)
    extends AbstractController(components)
    with LoginSupport
    with Loggable {

  def loginAction = Action.async { implicit request =>
    startGoogleLogin()
  }

  def oauth2Callback = Action.async { implicit request =>
    processOauth2Callback()
  }

  private def processOauth2Callback()(implicit
      request: RequestHeader
  ): Future[Result] = {
    (for {
      identity <- checkIdentity()
      _ <- checkGoogleGroupMembership(identity)
    } yield {
      log.info(s"User ${identity.email} successfully logged in")
      setupSessionWhenSuccessful(identity)
    }).merge
  }

  /*
  This is a copy of https://github.com/guardian/play-googleauth with one difference: here we check for membership in at least _one_ group, where as play-googleauth checks membership in _all_ groups.
  TODO update https://github.com/guardian/play-googleauth ?
   */
  private def checkGoogleGroupMembership(
      userIdentity: UserIdentity
  ): EitherT[Future, Result, Unit] = {
    groupChecker
      .retrieveGroupsFor(userIdentity.email)
      .attemptT
      .leftMap({ t =>
        val message =
          s"Login failure, Could not look up Google groups for ${userIdentity.email}"

        logger.warn(message, t)
        redirectWithError(failureRedirectTarget, message)
      })
      .subflatMap { userGroups =>
        {
          if ((userGroups.intersect(googleGroupsToCheck)).nonEmpty) {
            // user is in at least one of the Google groups
            Right(())
          } else {
            val message =
              s"Login failure. ${userIdentity.email} does not belong to the required Google groups: ${googleGroupsToCheck
                  .mkString(", ")}}"
            logger.info(message)
            Left(redirectWithError(failureRedirectTarget, message))
          }
        }
      }
  }

  override val failureRedirectTarget: Call = routes.Login.loginAction()
  override val defaultRedirectTarget: Call = routes.RootController.index()
}
