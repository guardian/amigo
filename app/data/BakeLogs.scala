package data

import cats.data.{ Validated, ValidatedNel }
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.{ DynamoFormat, DynamoReadError, Scanamo }
import models.{ RecipeId, _ }
import org.joda.time.DateTime

import scala.collection.JavaConverters._

object BakeLogs {

  implicit val dateTimeFormat = DynamoFormat.xmap(DynamoFormat.stringFormat)(d => Validated.valid(new DateTime(d)))(_.toString)

  def save(bakeLog: BakeLog)(implicit dynamo: Dynamo): Unit = {
    // Make sure we don't try to save an empty string to Dynamo
    val safeMessageParts = bakeLog.messageParts.map(part => part.copy(text = if (part.text.nonEmpty) part.text else " "))
    val safeBakeLog = bakeLog.copy(messageParts = safeMessageParts)
    Scanamo.put(dynamo.client)(tableName)(safeBakeLog)
  }

  def list(bakeId: BakeId)(implicit dynamo: Dynamo): Iterable[BakeLog] = {
    val queryRequest = new QueryRequest(tableName)
      .withKeyConditionExpression("#bakeId = :bakeId")
      .withExpressionAttributeNames(Map("#bakeId" -> "bakeId").asJava)
      .withExpressionAttributeValues(Map(":bakeId" -> BakeId.dynamoFormat.write(bakeId)).asJava)
      .withScanIndexForward(true) // return oldest logs first
    val items: Iterable[Option[ValidatedNel[DynamoReadError, BakeLog]]] =
      dynamo.client.query(queryRequest).getItems.asScala.map { item => Scanamo.from[BakeLog](new GetItemResult().withItem(item)) }
    items.flatMap(_.flatMap(_.toOption))
  }

  private def tableName(implicit dynamo: Dynamo) = dynamo.Tables.bakeLogs.name

}
