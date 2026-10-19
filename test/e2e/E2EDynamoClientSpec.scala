package e2e

import data.{Bakes, BaseImages, Dynamo, Recipes}
import models.{BaseImageId, RecipeId}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.model._

import scala.jdk.CollectionConverters._

class E2EDynamoClientSpec extends AnyFlatSpec with Matchers {
  private implicit val dynamo: Dynamo = E2EFixtures.dynamo()

  it should "decode the seeded base image through Scanamo" in {
    BaseImages.list().map(_.id).toList shouldBe List(BaseImageId("e2e-base"))
  }

  it should "decode the seeded recipe and resolve its base image" in {
    Recipes
      .list()
      .map(recipe => (recipe.id, recipe.baseImage.id))
      .toList shouldBe
      List((RecipeId("e2e-recipe"), BaseImageId("e2e-base")))
  }

  it should "return no base image for a missing ID" in {
    BaseImages.findById(BaseImageId("missing")) shouldBe None
  }

  it should "match every attribute of a composite key" in {
    val recipeId = AttributeValue.builder().s("e2e-recipe").build()
    val firstKey = Map(
      "recipeId" -> recipeId,
      "buildNumber" -> AttributeValue.builder().n("1").build()
    )
    val secondKey =
      firstKey.updated("buildNumber", AttributeValue.builder().n("2").build())
    val client = new E2EDynamoClient(
      Map("composite-key-table" -> List(firstKey, secondKey))
    )

    client
      .getItem(
        GetItemRequest
          .builder()
          .tableName("composite-key-table")
          .key(secondKey.asJava)
          .build()
      )
      .item()
      .asScala
      .toMap shouldBe secondKey
  }

  it should "scan an empty bake table" in {
    Bakes.scanForAll() shouldBe empty
  }

  it should "return no bakes when querying the seeded recipe" in {
    Bakes.list(RecipeId("e2e-recipe")) shouldBe empty
  }

  it should "reject an unknown table" in {
    val error = intercept[IllegalArgumentException] {
      dynamo.client.scan(ScanRequest.builder().tableName("unknown").build())
    }
    error.getMessage shouldBe "Unknown E2E table: unknown"
  }

  it should "reject queries against tables other than bakes" in {
    val error = intercept[IllegalArgumentException] {
      dynamo.client.query(
        QueryRequest.builder().tableName("amigo-E2E-recipes").build()
      )
    }
    error.getMessage shouldBe
      "requirement failed: Only bake queries are supported by the E2E fixtures"
  }

  it should "reject operations without an implementation" in {
    intercept[UnsupportedOperationException] {
      dynamo.client.deleteItem(
        DeleteItemRequest.builder().tableName("amigo-E2E-recipes").build()
      )
    }
  }
}
