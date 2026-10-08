package e2e

import com.gu.googleauth.{AuthAction, GoogleAuth, UserIdentity}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.Mode
import play.api.libs.ws.{WSCookie, WSClient, WSResponse}
import play.api.mvc.{AnyContent, Call, Results}
import play.api.test.{FakeRequest, Helpers, WsTestClient}
import play.core.server.{Server, ServerConfig}

import java.net.URI
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class E2EGoogleAuthSpec extends AnyFlatSpec with Matchers {
  private def withApp(test: (WSClient, String) => Unit): Unit = {
    val previousDiscovery = GoogleAuth.discoveryDocumentHolder
    try {
      Server.withApplicationFromContext(
        ServerConfig(port = Some(0), address = "localhost", mode = Mode.Dev)
      )(context => new E2EApplicationLoader().load(context)) { port =>
        WsTestClient.withClient { client =>
          test(client, s"http://localhost:$port")
        }
      }
    } finally GoogleAuth.discoveryDocumentHolder = previousDiscovery
  }

  private def cookiesHeader(
      cookies: scala.collection.Seq[WSCookie]
  ): (String, String) =
    "Cookie" -> cookies
      .map(cookie => s"${cookie.name}=${cookie.value}")
      .mkString("; ")

  private def get(
      client: WSClient,
      url: String,
      cookies: scala.collection.Seq[WSCookie] = Nil
  ): WSResponse =
    Await.result(
      client
        .url(url)
        .withFollowRedirects(false)
        .withHttpHeaders(cookiesHeader(cookies))
        .get(),
      10.seconds
    )

  it should "require login and return to the original page with a real session" in withApp {
    (client, baseUrl) =>
      val protectedPage = get(client, s"$baseUrl/base-images")
      protectedPage.status shouldBe 303
      protectedPage.header("Location") shouldBe Some("/login")

      val login = get(client, s"$baseUrl/login", protectedPage.cookies)
      login.status shouldBe 303
      val authorizationUrl = login.header("Location").get
      val authorization = get(client, authorizationUrl)
      authorization.status shouldBe 200
      authorization.body should include("Mock OAuth2 Server Sign-in")

      val signIn = Await.result(
        client
          .url(authorizationUrl)
          .withFollowRedirects(false)
          .withHttpHeaders(cookiesHeader(authorization.cookies))
          .post(Map("username" -> Seq("navigation"), "claims" -> Seq(""))),
        10.seconds
      )
      signIn.status shouldBe 302
      val callbackQuery = URI.create(signIn.header("Location").get).getRawQuery
      val callback = get(
        client,
        s"$baseUrl/oauth2callback?$callbackQuery",
        login.cookies
      )
      callback.status shouldBe 303
      callback.header("Location") shouldBe Some("/base-images")
      callback.cookies.map(_.name) should contain("PLAY_SESSION")

      val destination = get(client, s"$baseUrl/base-images", callback.cookies)
      destination.status shouldBe 200
      destination.body should include("<h1>Base images</h1>")
      get(client, s"$baseUrl/base-images").header("Location") shouldBe Some(
        "/login"
      )
  }

  it should "reject callbacks without valid state rather than establish a session" in withApp {
    (client, baseUrl) =>
      val protectedPage = get(client, s"$baseUrl/roles")
      val login = get(client, s"$baseUrl/login", protectedPage.cookies)
      val callback =
        get(
          client,
          s"$baseUrl/oauth2callback?code=forged&state=invalid",
          login.cookies
        )
      callback.status shouldBe 303
      callback.header("Location") shouldBe Some("/login")
      get(client, s"$baseUrl/roles", callback.cookies)
        .header("Location") shouldBe Some("/login")
  }

  it should "keep session validity enforcement enabled and restrict Google Groups lookups" in {
    val config = E2EGoogleAuth.config(
      "http://localhost:9000/oauth2callback",
      "local-test-anti-forgery-signing-value"
    )
    config.enforceValidity shouldBe true
    config.domains shouldBe List("guardian.co.uk")
    val checker = E2EGoogleAuth.groupChecker()
    implicit val ec: scala.concurrent.ExecutionContext =
      scala.concurrent.ExecutionContext.global
    Await.result(
      checker.retrieveGroupsFor(E2EGoogleAuth.email),
      5.seconds
    ) shouldBe Set(E2EGoogleAuth.group)
    intercept[IllegalArgumentException] {
      checker.retrieveGroupsFor("unexpected@example.com")
    }
  }

  it should "redirect an expired identity to login instead of bypassing authentication in development" in {
    implicit val ec: scala.concurrent.ExecutionContext =
      scala.concurrent.ExecutionContext.global
    val config = E2EGoogleAuth.config(
      "http://localhost:9000/oauth2callback",
      "local-test-anti-forgery-signing-value"
    )
    val action = new AuthAction[AnyContent](
      config,
      Call("GET", "/login"),
      Helpers.stubControllerComponents().parsers.default
    )
    val identity = UserIdentity(
      "expired",
      E2EGoogleAuth.email,
      "Navigation",
      "Tester",
      exp = 0,
      avatarUrl = None
    )
    val request = FakeRequest("GET", "/roles")
      .withSession(UserIdentity.KEY -> identity.asJson)
    val result = Await.result(
      action
        .invokeBlock[AnyContent](request, _ => Future.successful(Results.Ok)),
      5.seconds
    )
    result.header.status shouldBe 303
    result.header.headers("Location") shouldBe "/login"
  }
}
