package authentication

import authentication.AuthAction.UserIdentityRequest
import play.api.mvc.Security.AuthenticatedRequest
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/** Requires a resolved identity before invoking a protected controller action.
  *
  * Delegates identity resolution to [[IdentityProvider]] and exposes successful
  * results as an `AuthenticatedRequest` containing [[UserIdentity]]. Rejected
  * requests receive HTTP 401; the controller action is not invoked.
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
      case Left(_)         => Left(Results.Unauthorized)
    }
}

object AuthAction {
  type UserIdentityRequest[A] = AuthenticatedRequest[A, UserIdentity]
}
