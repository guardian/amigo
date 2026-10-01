package components

import authentication._
import components.Config.mandatoryConfig
import play.api.libs.ws.WSClient
import play.api.{Configuration, Mode}
import software.amazon.awssdk.regions.Region

import java.time.Clock
import scala.concurrent.ExecutionContext

/** Selects the identity provider for the running mode: a fixed development
  * identity in `Mode.Dev`, otherwise verified ALB identities.
  */
object IdentityProviderWiring {
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
        mandatoryConfig(configuration, "auth.alb.expectedSignerArn"),
        new CachingAlbPublicKeyProvider(wsClient, region),
        clock
      )
    }
}
