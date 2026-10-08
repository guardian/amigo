package authentication

import play.api.mvc.RequestHeader

import scala.concurrent.Future

final class DevelopmentIdentityProvider(identity: UserIdentity)
    extends IdentityProvider {
  override def identityFor(
      request: RequestHeader
  ): Future[Either[AuthenticationFailure, UserIdentity]] =
    Future.successful(Right(identity))
}
