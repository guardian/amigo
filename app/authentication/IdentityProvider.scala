package authentication

import config.Config.mandatoryConfig
import play.api.libs.ws.WSClient
import play.api.mvc.RequestHeader
import play.api.{Configuration, Mode}
import software.amazon.awssdk.regions.Region

import java.time.Clock
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

sealed trait AuthenticationFailure {
  def reason: String
}

object AuthenticationFailure {
  final case class InvalidIdentity(reason: String) extends AuthenticationFailure
}

trait IdentityProvider {
  def identityFor(
      request: RequestHeader
  ): Future[Either[AuthenticationFailure, UserIdentity]]
}

object IdentityProvider {
  def create(
      mode: Mode,
      configuration: Configuration,
      region: Region,
      wsClient: WSClient,
      clock: Clock
  )(implicit executionContext: ExecutionContext): IdentityProvider =
    if (mode == Mode.Dev) {
      new DevelopmentIdentityProvider(
        UserIdentity(
          mandatoryConfig(configuration, "auth.development.email"),
          mandatoryConfig(configuration, "auth.development.fullName")
        )
      )
    } else {
      new AlbIdentityProvider(
        AlbIdentityConfig(
          expectedSignerArn =
            mandatoryConfig(configuration, "auth.alb.expectedSignerArn")
        ),
        new CachingAlbPublicKeyProvider(wsClient, region),
        clock
      )
    }
}
