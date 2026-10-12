package e2e

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.{ApplicationLoader, Environment, Mode}
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest

class E2EIsolationSpec extends AnyFlatSpec with Matchers {
  it should "reject both bootstrap entry points outside development mode" in {
    for (mode <- Seq(Mode.Prod, Mode.Test)) {
      val context =
        ApplicationLoader.Context.create(Environment.simple(mode = mode))
      val loaderError = intercept[IllegalArgumentException] {
        new E2EApplicationLoader().load(context)
      }
      val componentsError = intercept[IllegalArgumentException] {
        new E2EComponents(context, "http://127.0.0.1:1")
      }
      for (error <- Seq(loaderError, componentsError)) {
        error.getMessage shouldBe
          "requirement failed: E2E bootstrap requires development mode"
      }
    }
  }

  it should "reject fixture writes rather than silently simulating success" in {
    val dynamo = E2EFixtures.dynamo()
    val error = intercept[UnsupportedOperationException] {
      dynamo.client.putItem(
        PutItemRequest.builder().tableName("amigo-E2E-recipes").build()
      )
    }
    error.getMessage shouldBe "DynamoDB.putItem is disabled in E2E"
  }
}
