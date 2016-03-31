package data

import cats.data.ValidatedNel
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.{ DynamoReadError, Scanamo }
import models.Recipe.DbModel
import models.RecipeId
import models._

import scala.collection.JavaConverters._

object Recipes {
  import DynamoFormats._

  def list()(implicit dynamo: Dynamo): Iterable[Recipe] = {
    val scanRequest = new ScanRequest(tableName)
    val dbModels: Iterable[Option[ValidatedNel[DynamoReadError, DbModel]]] =
      dynamo.client.scan(scanRequest).getItems.asScala.map { item => Scanamo.from[Recipe.DbModel](new GetItemResult().withItem(item)) }
    for {
      dbModel <- dbModels.flatMap(_.flatMap(_.toOption))
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe.db2domain(dbModel, baseImage)
    }
  }

  def findById(id: RecipeId)(implicit dynamo: Dynamo): Option[Recipe] = {
    for {
      dbModel <- Scanamo.get[RecipeId, DbModel](dynamo.client)(tableName)("id" -> id).flatMap(_.toOption)
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe.db2domain(dbModel, baseImage)
    }
  }

  def incrementAndGetBuildNumber(id: RecipeId)(implicit dynamo: Dynamo): Option[Int] = {
    val updateRequest = new UpdateItemRequest()
      .withTableName(tableName)
      .withKey(Map("id" -> new AttributeValue(id.value)).asJava)
      .withUpdateExpression("ADD nextBuildNumber :val1")
      .withConditionExpression("attribute_exists(id)") // to ensure the recipe exists in Dynamo
      .withExpressionAttributeValues(Map(":val1" -> new AttributeValue().withN("1")).asJava)
      .withReturnValues(ReturnValue.UPDATED_NEW)
    val updateResult = dynamo.client.updateItem(updateRequest)
    if (updateResult.getAttributes.containsKey("nextBuildNumber"))
      Some(updateResult.getAttributes.get("nextBuildNumber").getN.toInt)
    else
      None
  }

  private def tableName(implicit dynamo: Dynamo) = dynamo.Tables.recipes.name

}
