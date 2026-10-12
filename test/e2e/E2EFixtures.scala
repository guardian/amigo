package e2e

import data.{Dynamo, DynamoFormats}
import models._
import org.joda.time.DateTime
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scanamo.DynamoFormat
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import scala.jdk.CollectionConverters._

private[e2e] object E2EFixtures {
  private val createdAt = DateTime.parse("2026-01-01T00:00:00Z")
  private val baseImage = BaseImage(
    BaseImageId("e2e-base"),
    "Synthetic base image for navigation tests",
    AmiId("ami-00000000000000000"),
    Nil,
    "E2E",
    createdAt,
    "E2E",
    createdAt
  )
  private val recipe = Recipe.DbModel(
    RecipeId("e2e-recipe"),
    Some("Synthetic recipe for navigation tests"),
    baseImage.id,
    None,
    Nil,
    0,
    "E2E",
    createdAt,
    "E2E",
    createdAt,
    None,
    Some(Nil)
  )

  import DynamoFormats._

  private def item[A: DynamoFormat](
      value: A
  ): java.util.Map[String, AttributeValue] =
    DynamoFormat[A].write(value).toAttributeValue.m()

  private val tables = Map(
    "amigo-E2E-base-images" -> List(item(baseImage)),
    "amigo-E2E-recipes" -> List(item(recipe)),
    "amigo-E2E-bakes" -> Nil,
    "amigo-E2E-bake-logs" -> Nil
  )

  private def rows(name: String): List[java.util.Map[String, AttributeValue]] =
    tables.getOrElse(
      name,
      throw new IllegalArgumentException(s"Unknown E2E table: $name")
    )

  def disabled[A](kind: Class[A]): A =
    mock(
      kind,
      new Answer[AnyRef] {
        override def answer(invocation: InvocationOnMock): AnyRef =
          throw new UnsupportedOperationException(
            s"${kind.getSimpleName}.${invocation.getMethod.getName} is disabled in E2E"
          )
      }
    )

  def dynamo(): Dynamo = {
    val client = mock(
      classOf[DynamoDbClient],
      new Answer[AnyRef] {
        override def answer(invocation: InvocationOnMock): AnyRef =
          invocation.getMethod.getName match {
            case "scan" =>
              val request = invocation.getArgument[ScanRequest](0)
              ScanResponse
                .builder()
                .items(rows(request.tableName()).asJava)
                .build()
            case "getItem" =>
              val request = invocation.getArgument[GetItemRequest](0)
              val found = rows(request.tableName()).find { row =>
                request.key().asScala.forall { case (key, value) =>
                  row.get(key) == value
                }
              }
              GetItemResponse
                .builder()
                .item(found.getOrElse(Map.empty[String, AttributeValue].asJava))
                .build()
            case "query" =>
              val request = invocation.getArgument[QueryRequest](0)
              require(
                request.tableName() == "amigo-E2E-bakes",
                "Only bake queries are supported by the E2E fixtures"
              )
              QueryResponse
                .builder()
                .items(rows(request.tableName()).asJava)
                .build()
            case method =>
              throw new UnsupportedOperationException(
                s"DynamoDB.$method is disabled in E2E"
              )
          }
      }
    )
    new Dynamo(client, "E2E")
  }
}
