package e2e

import com.gu.googleauth.{
  AntiForgeryChecker,
  GoogleAuthConfig,
  GoogleGroupChecker
}
import com.gu.play.secretrotation.DualSecretTransition.InitialSecret
import no.nav.security.mock.oauth2.{MockOAuth2Server, OAuth2Config}
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import play.api.libs.json.Json

import java.time.Duration
import scala.concurrent.Future

private[e2e] object E2EGoogleAuth {
  val issuer = "google"
  val email = "navigation@guardian.co.uk"
  val group = "e2e-navigation@guardian.co.uk"
  val clientId = "amigo-e2e"

  def config(redirectUrl: String, sessionSecret: String): GoogleAuthConfig =
    GoogleAuthConfig(
      clientId = clientId,
      clientSecret = "unused-local-test-value",
      redirectUrl = redirectUrl,
      domains = List("guardian.co.uk"),
      maxAuthAge = Some(Duration.ofDays(90)),
      enforceValidity = true,
      antiForgeryChecker = AntiForgeryChecker(InitialSecret(sessionSecret))
    )

  def server(): MockOAuth2Server = {
    val config = Json.obj(
      "interactiveLogin" -> true,
      "tokenCallbacks" -> Json.arr(
        Json.obj(
          "issuerId" -> issuer,
          "requestMappings" -> Json.arr(
            Json.obj(
              "requestParam" -> "code",
              "match" -> "*",
              "claims" -> Json.obj(
                "email" -> email,
                "email_verified" -> true,
                "aud" -> clientId,
                "azp" -> clientId,
                // play-googleauth requires this field but does not validate the access-token hash.
                "at_hash" -> "unused-by-play-googleauth",
                "hd" -> "guardian.co.uk",
                "name" -> "Navigation Tester",
                "given_name" -> "Navigation",
                "family_name" -> "Tester"
              )
            )
          )
        )
      )
    )
    new MockOAuth2Server(
      OAuth2Config.Companion.fromJson(Json.stringify(config))
    )
  }

  def groupChecker(): GoogleGroupChecker =
    mock(
      classOf[GoogleGroupChecker],
      new Answer[AnyRef] {
        override def answer(invocation: InvocationOnMock): AnyRef =
          invocation.getMethod.getName match {
            case "retrieveGroupsFor" =>
              val requestedEmail = invocation.getArgument[String](0)
              require(
                requestedEmail == email,
                s"Unexpected E2E Google Groups lookup: $requestedEmail"
              )
              Future.successful(Set(group))
            case method =>
              throw new UnsupportedOperationException(
                s"GoogleGroupChecker.$method is disabled in E2E"
              )
          }
      }
    )
}
