package controllers

import com.gu.googleauth.{
  AntiForgeryChecker,
  AuthAction,
  DiscoveryDocument,
  GoogleAuth,
  GoogleAuthConfig,
  UserIdentity
}
import com.gu.play.secretrotation.DualSecretTransition.InitialSecret
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.http.Status.{FOUND, SEE_OTHER}
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.{AnyContent, Result, Results}
import play.api.mvc.request.RequestTarget
import play.api.test.FakeRequest
import play.api.test.Helpers.{
  GET,
  await,
  defaultAwaitTimeout,
  redirectLocation,
  session,
  status,
  stubControllerComponents
}

import java.net.URI
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class LoginCompatibilitySpec
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with BeforeAndAfterEach {

  private val issuerId = "amigo"
  private val clientId = "amigo-client"
  private val allowedGroup = "allowed-group"
  private val testSecret = "test-secret-that-is-long-enough-for-hmac-sha256"

  override protected def beforeEach(): Unit = {
    GoogleAuth.discoveryDocumentHolder = None
    super.beforeEach()
  }

  override protected def afterEach(): Unit = {
    GoogleAuth.discoveryDocumentHolder = None
    super.afterEach()
  }

  "Login" should "create a Play identity session through mock OAuth when the user is admitted" in {
    val callbackResult = runLoginCallback(
      email = "test.user@guardian.co.uk",
      groupResult = Future.successful(Set(allowedGroup))
    )

    status(callbackResult) shouldBe SEE_OTHER
    redirectLocation(callbackResult) shouldBe Some("/")
    val identity = Json
      .parse(session(callbackResult).get("identity").value)
      .as[UserIdentity]
    identity.email shouldBe "test.user@guardian.co.uk"
    identity.firstName shouldBe "Test"
    identity.lastName shouldBe "User"
  }

  it should "reject an identity outside the configured Google domain" in {
    val callbackResult = runLoginCallback(
      email = "test.user@example.com",
      groupResult = Future.failed(
        new AssertionError("Group lookup must not run for a rejected domain")
      )
    )

    assertLoginRejected(callbackResult)
  }

  it should "reject an identity without an allowed Google group" in {
    val callbackResult = runLoginCallback(
      email = "test.user@guardian.co.uk",
      groupResult = Future.successful(Set("another-group"))
    )

    assertLoginRejected(callbackResult)
  }

  it should "fail closed when Google Directory lookup fails" in {
    val callbackResult = runLoginCallback(
      email = "test.user@guardian.co.uk",
      groupResult = Future.failed(new RuntimeException("Directory unavailable"))
    )

    assertLoginRejected(callbackResult)
  }

  it should "reject a callback with invalid anti-forgery state" in {
    val callbackResult = runLoginCallback(
      email = "test.user@guardian.co.uk",
      groupResult = Future.failed(
        new AssertionError("Group lookup must not run for invalid state")
      ),
      invalidateState = true
    )

    assertLoginRejected(callbackResult)
  }

  it should "reject malformed Google profile claims" in {
    val callbackResult = runLoginCallback(
      email = "test.user@guardian.co.uk",
      groupResult = Future.failed(
        new AssertionError("Group lookup must not run for malformed claims")
      ),
      includeFamilyName = false
    )

    assertLoginRejected(callbackResult)
  }

  "AuthAction" should "reject an expired application identity" in {
    val components = stubControllerComponents()
    val authAction = new AuthAction[AnyContent](
      authConfig,
      routes.Login.loginAction(),
      components.parsers.default
    )
    val expiredIdentity = UserIdentity(
      sub = "test-subject",
      email = "test.user@guardian.co.uk",
      firstName = "Test",
      lastName = "User",
      exp = Instant.now().minusSeconds(1).getEpochSecond,
      avatarUrl = None
    )
    val request = FakeRequest(GET, "/")
      .withSession("identity" -> Json.stringify(Json.toJson(expiredIdentity)))
    val result = authAction(_ => Results.Ok)(request)

    status(result) shouldBe SEE_OTHER
    redirectLocation(result) shouldBe Some("/login")
  }

  private def runLoginCallback(
      email: String,
      groupResult: Future[Set[String]],
      invalidateState: Boolean = false,
      includeFamilyName: Boolean = true
  ): Future[Result] = {
    val oauthServer = new MockOAuth2Server()
    oauthServer.start()

    try {
      oauthServer.enqueueCallback(tokenCallback(email, includeFamilyName))

      play.api.test.WsTestClient.withClient { delegateClient =>
        val wsClient = new DiscoveryRedirectingWsClient(
          delegateClient,
          oauthServer.wellKnownUrl(issuerId).toString
        )
        val controller = new Login(
          authConfig,
          wsClient,
          stubControllerComponents(),
          Set(allowedGroup),
          GoogleGroupLookupFixture(groupResult)
        )

        val loginResult = controller.loginAction(FakeRequest(GET, "/login"))
        status(loginResult) shouldBe SEE_OTHER

        val authorizationResult = await(
          delegateClient
            .url(redirectLocation(loginResult).value)
            .withFollowRedirects(false)
            .get()
        )
        authorizationResult.status shouldBe FOUND

        val callbackUri =
          URI.create(authorizationResult.header("Location").value)
        val validCallbackRequest = FakeRequest(
          GET,
          s"${callbackUri.getRawPath}?${callbackUri.getRawQuery}"
        ).withSession(session(loginResult).data.toSeq: _*)
        val callbackRequest =
          if (invalidateState) {
            val target = validCallbackRequest.target
            validCallbackRequest.withTarget(
              RequestTarget(
                target.uri.toString,
                target.path,
                validCallbackRequest.queryString.updated(
                  "state",
                  Seq("invalid-state")
                )
              )
            )
          } else validCallbackRequest

        Future.successful(await(controller.oauth2Callback(callbackRequest)))
      }
    } finally oauthServer.shutdown()
  }

  private def assertLoginRejected(result: Future[Result]): Unit = {
    status(result) shouldBe SEE_OTHER
    redirectLocation(result) shouldBe Some("/login")
    session(result).get("identity") shouldBe empty
  }

  private def authConfig: GoogleAuthConfig =
    GoogleAuthConfig(
      clientId = clientId,
      clientSecret = "test-client-secret",
      redirectUrl = "http://localhost/oauth2callback",
      domains = List("guardian.co.uk"),
      maxAuthAge = None,
      enforceValidity = true,
      antiForgeryChecker = AntiForgeryChecker(InitialSecret(testSecret))
    )

  private def tokenCallback(
      email: String,
      includeFamilyName: Boolean
  ): DefaultOAuth2TokenCallback = {
    val requiredClaims = Map[String, AnyRef](
      "email" -> email,
      "email_verified" -> java.lang.Boolean.TRUE,
      "at_hash" -> "test-access-token-hash",
      "name" -> "Test User",
      "given_name" -> "Test",
      "picture" -> "https://example.com/avatar.png",
      "iat" -> Long.box(Instant.now().minusSeconds(60).getEpochSecond),
      "exp" -> Long.box(Instant.now().plusSeconds(3600).getEpochSecond)
    )
    val claims =
      if (includeFamilyName) requiredClaims.updated("family_name", "User")
      else requiredClaims

    new DefaultOAuth2TokenCallback(
      issuerId,
      "test-subject",
      "JWT",
      List(clientId).asJava,
      claims.asJava,
      3600
    )
  }
}

private case class GoogleGroupLookupFixture(result: Future[Set[String]])
    extends GoogleGroupLookup {
  override def retrieveGroupsFor(email: String): Future[Set[String]] = result
}

private class DiscoveryRedirectingWsClient(
    delegate: WSClient,
    discoveryUrl: String
) extends WSClient {
  override def url(url: String): WSRequest =
    delegate.url(if (url == DiscoveryDocument.url) discoveryUrl else url)

  override def underlying[T]: T = delegate.underlying[T]

  override def close(): Unit = delegate.close()
}
