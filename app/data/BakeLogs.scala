package data

import cats.data.{ Validated, ValidatedNel }
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.{ DynamoFormat, DynamoReadError, Scanamo }
import models.{ RecipeId, _ }
import org.joda.time.DateTime

import scala.collection.JavaConverters._

object BakeLogs {

  implicit val dateTimeFormat = DynamoFormat.xmap(DynamoFormat.stringFormat)(d => Validated.valid(new DateTime(d)))(_.toString)

  def save(bakeLog: BakeLog)(implicit dynamo: Dynamo): Unit =
    Scanamo.put(dynamo.client)(tableName)(bakeLog)

  def list(recipeId: RecipeId)(implicit dynamo: Dynamo): Iterable[BakeLog] = {
    val queryRequest = new QueryRequest(tableName)
      .withKeyConditionExpression("recipeId = :recipeId")
      .withExpressionAttributeValues(Map(":recipeId" -> new AttributeValue(recipeId.value)).asJava)
      .withScanIndexForward(true) // return oldest logs first
    val items: Iterable[Option[ValidatedNel[DynamoReadError, BakeLog]]] =
      dynamo.client.query(queryRequest).getItems.asScala.map { item => Scanamo.from[BakeLog](new GetItemResult().withItem(item)) }
    items.flatMap(_.flatMap(_.toOption))
  }

  private def tableName(implicit dynamo: Dynamo) = dynamo.Tables.bakeLogs.name

}
