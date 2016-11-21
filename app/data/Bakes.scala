package data

import com.gu.scanamo.syntax._
import models._
import org.joda.time.DateTime

object Bakes {
  import Dynamo._
  import cats.syntax.either._

  def create(recipe: Recipe, buildNumber: Int, startedBy: String)(implicit dynamo: Dynamo): Bake = {
    val bake = Bake(recipe, buildNumber, status = BakeStatus.Running, amiId = None, startedBy = startedBy, startedAt = DateTime.now())
    val dbModel = Bake.domain2db(bake)
    table.put(dbModel).exec()
    bake
  }

  def updateStatus(bakeId: BakeId, status: BakeStatus)(implicit dynamo: Dynamo): Unit = {
    table
      .given(attributeExists('recipeId) and attributeExists('buildNumber))
      .update(
        ('recipeId -> bakeId.recipeId) and ('buildNumber -> bakeId.buildNumber),
        set('status -> status)
      )
      .exec()
  }

  def updateAmiId(bakeId: BakeId, amiId: AmiId)(implicit dynamo: Dynamo): Unit = {
    table
      .given(attributeExists('recipeId) and attributeExists('buildNumber))
      .update(
        ('recipeId -> bakeId.recipeId) and ('buildNumber -> bakeId.buildNumber),
        set('amiId -> amiId)
      )
      .exec()
  }

  def list(recipeId: RecipeId, limit: Int = 20)(implicit dynamo: Dynamo): Iterable[Bake] = {
    val dbModels = table.limit(limit).query(('recipeId -> recipeId).descending) // return newest (highest build number) first
      .exec()
      .flatMap(_.toOption)
    val recipe = Recipes.findById(recipeId)
    for {
      r <- recipe.toIterable
      dbModel <- dbModels
    } yield {
      Bake.db2domain(dbModel, r)
    }
  }

  def findById(recipeId: RecipeId, buildNumber: Int)(implicit dynamo: Dynamo): Option[Bake] = {
    val dbModel = table.get(('recipeId -> recipeId) and ('buildNumber -> buildNumber))
      .exec()
      .flatMap(_.toOption)
    val recipe = Recipes.findById(recipeId)
    for {
      r <- recipe
      model <- dbModel
    } yield {
      Bake.db2domain(model, r)
    }
  }

  private def table(implicit dynamo: Dynamo) = dynamo.Tables.bakes.table

}
