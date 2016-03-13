package data

import cats.data.ValidatedNel
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.{ DynamoReadError, Scanamo }
import models.{ RecipeId, _ }

import scala.collection.JavaConverters._

object Bakes {

  def create(recipe: Recipe, buildNumber: Int)(implicit dynamo: Dynamo): Bake = {
    val bake = Bake(recipe, buildNumber, status = BakeStatus.Running, amiId = None)
    val dbModel = Bake.domain2db(bake)
    Scanamo.put(dynamo.client)(tableName)(dbModel)
    bake
  }

  def list(recipeId: RecipeId)(implicit dynamo: Dynamo): Iterable[Bake] = {
    val queryRequest = new QueryRequest(tableName)
      .withKeyConditionExpression("#recipeId = :recipeId")
      .withScanIndexForward(false) // return newest (highest build number) first
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

  private def tableName(implicit dynamo: Dynamo) = dynamo.Tables.bakes.name

}
