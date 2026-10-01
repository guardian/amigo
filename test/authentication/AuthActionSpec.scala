package authentication

import authentication.AuthenticationFailure.InvalidIdentity
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc.{RequestHeader, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthActionSpec extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private implicit val actorSystem: ActorSystem = ActorSystem("AuthActionSpec")
  private implicit val materializer: Materializer =
    SystemMaterializer(actorSystem).materializer
  private val components = stubControllerComponents()
  private val identity = UserIdentity("developer@example.com", "Example User")

  private val authenticatedProvider = new IdentityProvider {
    override def identityFor(
        request: RequestHeader
    ): Future[Either[AuthenticationFailure, UserIdentity]] =
      Future.successful(Right(identity))
  }

  it should "provide the authenticated user to a default body parser" in {
    val authAction =
      new AuthAction(authenticatedProvider, components.parsers.default)
    val action = authAction { request =>
      Results.Ok(s"${request.user.email}:${request.user.fullName}")
    }

    val result = call(action, FakeRequest())

    status(result) should be(OK)
    contentAsString(result) should be("developer@example.com:Example User")
  }

  it should "preserve an explicitly selected body parser" in {
    val authAction =
      new AuthAction(authenticatedProvider, components.parsers.default)
    val action = authAction(components.parsers.formUrlEncoded) { request =>
      Results.Ok(request.body("field").head)
    }

    val result = call(
      action,
      FakeRequest("POST", "/").withFormUrlEncodedBody("field" -> "value")
    )

    status(result) should be(OK)
    contentAsString(result) should be("value")
  }

  it should "return a friendly uncached unauthorized page when identity resolution fails" in {
    val rejectedProvider = new IdentityProvider {
      override def identityFor(
          request: RequestHeader
      ): Future[Either[AuthenticationFailure, UserIdentity]] =
        Future.successful(Left(InvalidIdentity("missing identity")))
    }
    val authAction =
      new AuthAction(rejectedProvider, components.parsers.default)
    val action = authAction(_ => Results.Ok)

    val result = call(action, FakeRequest())

    status(result) should be(UNAUTHORIZED)
    contentType(result) shouldBe Some("text/html")
    header(CACHE_CONTROL, result) shouldBe Some("no-store")
    val body = contentAsString(result)
    body should include("We couldn't verify your sign-in")
    body should include("private browser window")
    body should include("Guardian Google account")
    body should include("authorised Google group")
    body should include("""href="/"""")
    body should not include "missing identity"
    body should not include identity.email
  }

  override protected def afterAll(): Unit = {
    actorSystem.terminate()
    super.afterAll()
  }
}
