package data

import cats.data.ValidatedNel
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.{ DynamoFormat, DynamoReadError, Scanamo }
import models.{ RecipeId, _ }
import org.joda.time.DateTime

import scala.collection.JavaConverters._

object Bakes {
  import DynamoFormats._

  def create(recipe: Recipe, buildNumber: Int, startedBy: String)(implicit dynamo: Dynamo): Bake = {
    val bake = Bake(recipe, buildNumber, status = BakeStatus.Running, amiId = None, startedBy = startedBy, startedAt = DateTime.now())
    val dbModel = Bake.domain2db(bake)
    Scanamo.put(dynamo.client)(tableName)(dbModel)
    bake
  }

  def updateStatus(bakeId: BakeId, status: BakeStatus)(implicit dynamo: Dynamo, fbs: DynamoFormat[BakeStatus]): Unit = {
    updateItem(bakeId, "SET #status = :status", "#status" -> "status", ":status" -> fbs.write(status))
  }

  def updateAmiId(bakeId: BakeId, amiId: AmiId)(implicit dynamo: Dynamo, fai: DynamoFormat[AmiId]): Unit = {
    updateItem(bakeId, "SET #amiId = :amiId", "#amiId" -> "amiId", ":amiId" -> fai.write(amiId))
  }

  def list(recipeId: RecipeId, limit: Int = 20)(implicit dynamo: Dynamo): Iterable[Bake] = {
    val queryRequest = new QueryRequest(tableName)
      .withKeyConditionExpression("#recipeId = :recipeId")
      .withExpressionAttributeNames(Map("#recipeId" -> "recipeId").asJava)
      .withExpressionAttributeValues(Map(":recipeId" -> new AttributeValue(recipeId.value)).asJava)
      .withScanIndexForward(false) // return newest (highest build number) first
      .withLimit(limit)
    val recipe = Recipes.findById(recipeId)
    val dbModels: Iterable[Option[ValidatedNel[DynamoReadError, Bake.DbModel]]] =
      dynamo.client.query(queryRequest).getItems.asScala.map { item => Scanamo.from[Bake.DbModel](new GetItemResult().withItem(item)) }
    for {
      r <- recipe.toIterable
      dbModel <- dbModels.flatMap(_.flatMap(_.toOption))
    } yield {
      Bake.db2domain(dbModel, r)
    }
  }

  def findById(recipeId: RecipeId, buildNumber: Int)(implicit dynamo: Dynamo): Option[Bake] = {
    val queryRequest = new QueryRequest(tableName)
      .withKeyConditionExpression("#recipeId = :recipeId AND #buildNumber = :buildNumber")
      .withExpressionAttributeNames(Map("#recipeId" -> "recipeId", "#buildNumber" -> "buildNumber").asJava)
      .withExpressionAttributeValues(Map(":recipeId" -> new AttributeValue(recipeId.value), ":buildNumber" -> new AttributeValue().withN(buildNumber.toString)).asJava)
      .withScanIndexForward(false) // return newest (highest build number) first
      .withLimit(1)
    val recipe = Recipes.findById(recipeId)
    val dbModel: Option[ValidatedNel[DynamoReadError, Bake.DbModel]] =
      dynamo.client.query(queryRequest).getItems.asScala.map { item => Scanamo.from[Bake.DbModel](new GetItemResult().withItem(item)) }
        .headOption.flatten
    for {
      r <- recipe
      dbModel <- dbModel.flatMap(_.toOption)
    } yield {
      Bake.db2domain(dbModel, r)
    }
  }

  private def updateItem(bakeId: BakeId, updateExpression: String, attrNames: (String, String), attrValues: (String, AttributeValue))(implicit dynamo: Dynamo, frid: DynamoFormat[RecipeId], fint: DynamoFormat[Int]): Unit = {
    val updateRequest = new UpdateItemRequest()
      .withTableName(tableName)
      .withConditionExpression("attribute_exists(recipeId) AND attribute_exists(buildNumber)")
      .withKey(Map("recipeId" -> frid.write(bakeId.recipeId), "buildNumber" -> fint.write(bakeId.buildNumber)).asJava)
      .withUpdateExpression(updateExpression)
      .withExpressionAttributeNames(Map(attrNames).asJava)
      .withExpressionAttributeValues(Map(attrValues).asJava)
    dynamo.client.updateItem(updateRequest)
  }

  private def tableName(implicit dynamo: Dynamo) = dynamo.Tables.bakes.name

}
