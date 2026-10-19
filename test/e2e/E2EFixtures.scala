package e2e

import data.{Dynamo, DynamoFormats}
import models._
import org.joda.time.DateTime
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scanamo.DynamoFormat
import org.scanamo.generic.auto._
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

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
  ): Map[String, AttributeValue] =
    DynamoFormat[A].write(value).toAttributeValue.m().asScala.toMap

  private val tables = Map(
    "amigo-E2E-base-images" -> List(item(baseImage)),
    "amigo-E2E-recipes" -> List(item(recipe)),
    "amigo-E2E-bakes" -> Nil,
    "amigo-E2E-bake-logs" -> Nil
  )

  /* This allows us to pass args where a concrete implementation is required, ensuring it isn't actually used */
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

  def dynamo(): Dynamo =
    new Dynamo(new E2EDynamoClient(tables), "E2E")
}
