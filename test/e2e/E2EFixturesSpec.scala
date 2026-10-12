package e2e

import data.{BaseImages, Bakes, Dynamo, Recipes}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.test.WsTestClient
import play.core.server.{Server, ServerConfig}
import prism.Prism
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class E2EFixturesSpec extends AnyFlatSpec with Matchers {
  it should "serve every Prism dataset required by the application" in {
    Server.withRouterFromComponents(
      ServerConfig(port = Some(0), address = "127.0.0.1")
    )(E2EPrismStub.routes) { port =>
      WsTestClient.withClient { client =>
        val prism = new Prism(client, s"http://127.0.0.1:$port")
        Await.result(prism.findAllAWSAccounts(), 5.seconds) shouldBe
          Seq(Prism.AWSAccount("E2E", "000000000000"))
        Await.result(prism.findAllInstances(), 5.seconds) shouldBe empty
        Await.result(
          prism.findAllLaunchConfigurations(),
          5.seconds
        ) shouldBe empty
        Await.result(prism.findAllLaunchTemplates(), 5.seconds) shouldBe empty
        Await.result(prism.findCopiedImages(), 5.seconds) shouldBe empty
      }
    }
  }

  it should "provide a coherent synthetic base image and recipe without AWS" in {
    implicit val dynamo: Dynamo = E2EFixtures.dynamo()
    val baseImages = BaseImages.list().toList
    val recipes = Recipes.list().toList
    baseImages.map(_.id.value) shouldBe List("e2e-base")
    recipes.map(_.id.value) shouldBe List("e2e-recipe")
    recipes.head.baseImage shouldBe baseImages.head
    Bakes.list(recipes.head.id) shouldBe empty
  }

  it should "reject writes rather than silently simulating success" in {
    val dynamo = E2EFixtures.dynamo()
    val error = intercept[UnsupportedOperationException] {
      dynamo.client.putItem(
        PutItemRequest.builder().tableName("amigo-E2E-recipes").build()
      )
    }
    error.getMessage shouldBe "DynamoDB.putItem is disabled in E2E"
  }
}
