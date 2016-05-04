package data

import com.amazonaws.services.dynamodbv2.model._
import com.gu.cm.Identity
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.{ DynamoFormat, Table }
import models.{ RecipeId, _ }
import org.joda.time.DateTime

import scala.collection.JavaConverters._

class Bakes(identity: Identity, recipes: Recipes) {
  import DynamoFormats._
  import com.gu.scanamo.syntax._

  private val tableName = Dynamo.tableName(identity, "bakes")
  val table = Table[Bake.DbModel](tableName)

  def create(recipe: Recipe, buildNumber: Int, startedBy: String): ScanamoOps[Bake] = {
    val bake = Bake(recipe, buildNumber, status = BakeStatus.Running, amiId = None, startedBy = startedBy, startedAt = DateTime.now())
    val dbModel = Bake.domain2db(bake)
    for {
      _ <- table.put(dbModel)
    } yield bake
  }

  def updateStatus(bakeId: BakeId, status: BakeStatus)(implicit dynamo: Dynamo, fbs: DynamoFormat[BakeStatus]): Unit = {
    updateItem(bakeId, "SET #status = :status", "#status" -> "status", ":status" -> fbs.write(status))
  }

  def updateAmiId(bakeId: BakeId, amiId: AmiId)(implicit dynamo: Dynamo, fai: DynamoFormat[AmiId]): Unit = {
    updateItem(bakeId, "SET #amiId = :amiId", "#amiId" -> "amiId", ":amiId" -> fai.write(amiId))
  }

  def list(recipeId: RecipeId, limit: Int = 20): ScanamoOps[Iterable[Bake]] = {
    for {
      r <- recipes.findById(recipeId)
      dbModels <- table.query(('recipeId -> recipeId).descending)
    } yield {
      for {
        recipe <- r.toIterable
        dbModel <- dbModels.flatMap(_.toOption).take(limit)
      } yield Bake.db2domain(dbModel, recipe)
    }
  }

  def findById(recipeId: RecipeId, buildNumber: Int): ScanamoOps[Option[Bake]] = {
    for {
      r <- recipes.findById(recipeId)
      dbBake <- table.get('recipeId -> recipeId and 'buildNumber -> buildNumber)
    } yield {
      for {
        recipe <- r
        dbModel <- dbBake.flatMap(_.toOption)
      } yield Bake.db2domain(dbModel, recipe)
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
}
