package authentication

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.ws.WSClient
import play.api.test.FakeRequest
import play.api.{Configuration, Mode}
import software.amazon.awssdk.regions.Region

import java.time.Clock
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class IdentityProviderSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  private implicit val executionContext: ExecutionContext =
    ExecutionContext.global

  it should "use the configured fixed identity only in development mode" in {
    val provider = IdentityProvider.create(
      Mode.Dev,
      Configuration.from(
        Map(
          "auth.development.email" -> "developer@example.com",
          "auth.development.fullName" -> "Example Developer"
        )
      ),
      Region.EU_WEST_1,
      mock[WSClient],
      Clock.systemUTC()
    )

    val result = Await.result(provider.identityFor(FakeRequest()), 2.seconds)

    result should be(
      Right(UserIdentity("developer@example.com", "Example Developer"))
    )
  }

  it should "create the ALB provider outside development mode" in {
    val provider = IdentityProvider.create(
      Mode.Test,
      Configuration("auth.alb.expectedSignerArn" -> "expected-alb-arn"),
      Region.EU_WEST_1,
      mock[WSClient],
      Clock.systemUTC()
    )

    provider shouldBe a[AlbIdentityProvider]
  }

  it should "fail outside development mode when the ALB signer ARN is missing" in {
    val exception = intercept[RuntimeException] {
      IdentityProvider.create(
        Mode.Test,
        Configuration.empty,
        Region.EU_WEST_1,
        mock[WSClient],
        Clock.systemUTC()
      )
    }

    exception.getMessage shouldBe "Missing config key: auth.alb.expectedSignerArn"
  }
}
