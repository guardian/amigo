package authentication

import authentication.AuthAction.UserIdentityRequest
import play.api.http.HeaderNames.CACHE_CONTROL
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/** Requires a resolved identity before invoking a protected controller action.
  *
  * Delegates identity resolution to [[IdentityProvider]] and exposes successful
  * results as an `AuthenticatedRequest` containing [[UserIdentity]]. Rejected
  * requests receive HTTP 401 with a friendly sign-in error page and
  * `Cache-Control: no-store`; the controller action is not invoked.
  *
  * This action only handles requests that reach Amigo. Authentication and group
  * rejection by Cognito or the ALB happens upstream and cannot render this
  * page.
  *
  * @tparam A
  *   Request body type produced by the default body parser.
  * @param identityProvider
  *   Resolves the request's identity or returns an authentication failure.
  * @param parser
  *   Default body parser, which individual actions may override.
  */
final class AuthAction[A](
    identityProvider: IdentityProvider,
    override val parser: BodyParser[A]
)(implicit override val executionContext: ExecutionContext)
    extends ActionBuilder[UserIdentityRequest, A]
    with ActionRefiner[Request, UserIdentityRequest] {

  override protected def refine[B](
      request: Request[B]
  ): Future[Either[Result, AuthenticatedRequest[B, UserIdentity]]] =
    identityProvider.identityFor(request).map {
      case Right(identity) => Right(new AuthenticatedRequest(identity, request))
      case Left(_)         =>
        Left(
          Results
            .Unauthorized(views.html.authenticationError())
            .withHeaders(CACHE_CONTROL -> "no-store")
        )
    }
}

object AuthAction {
  type UserIdentityRequest[A] = AuthenticatedRequest[A, UserIdentity]
}
