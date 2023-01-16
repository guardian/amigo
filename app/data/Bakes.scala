package data

import org.scanamo.syntax._
import models._
import org.joda.time.DateTime
import org.scanamo.DynamoReadError
import services.Loggable

object Bakes extends Loggable {
  import Dynamo._

  def create(recipe: Recipe, buildNumber: Int, startedBy: String)(implicit
      dynamo: Dynamo
  ): Bake = {
    val bake = Bake(
      recipe,
      buildNumber,
      status = BakeStatus.Running,
      amiId = None,
      startedBy = startedBy,
      startedAt = DateTime.now(),
      deleted = false
    )
    val dbModel = Bake.domain2db(bake)
    table.put(dbModel).exec()
    bake
  }

  def updateStatus(bakeId: BakeId, status: BakeStatus)(implicit
      dynamo: Dynamo
  ): Unit = {
    table
      .when(attributeExists("recipeId") and attributeExists("buildNumber"))
      .update(
        ("recipeId" === bakeId.recipeId) and ("buildNumber" === bakeId.buildNumber),
        set("status", status)(BakeStatus.dynamoFormat)
      )
      .exec()
  }

  def updateStatusIfRunning(bakeId: BakeId, status: BakeStatus)(implicit
      dynamo: Dynamo
  ): Unit = {
    table
      .when(attributeExists("recipeId") and attributeExists("buildNumber"))
      .update(
        ("recipeId" === bakeId.recipeId) and ("buildNumber" === bakeId.buildNumber),
        set("status", status)(BakeStatus.dynamoFormat)
      )
      .exec()
  }

  def updateAmiId(bakeId: BakeId, amiId: AmiId)(implicit
      dynamo: Dynamo
  ): Unit = {
    table
      .when(attributeExists("recipeId") and attributeExists("buildNumber"))
      .update(
        ("recipeId" === bakeId.recipeId) and ("buildNumber" === bakeId.buildNumber),
        set("amiId", amiId)
      )
      .exec()
  }

  def list(recipeId: RecipeId)(implicit dynamo: Dynamo): Iterable[Bake] = {
    val dbModels = table.descending
      .query(
        "recipeId" === recipeId
      ) // return newest (highest build number) first
      .exec()
      .flatMap(_.toOption)
    val recipe = Recipes.findById(recipeId)
    for {
      r <- recipe.toList
      dbModel <- dbModels
    } yield {
      Bake.db2domain(dbModel, r)
    }
  }

  def findById(recipeId: RecipeId, buildNumber: Int)(implicit
      dynamo: Dynamo
  ): Option[Bake] = {
    val dbModel = table
      .get(("recipeId" === recipeId) and ("buildNumber" === buildNumber))
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

  @scala.annotation.tailrec
  def findPreviousSuccessfulBake(recipeId: RecipeId, buildNumber: Int)(implicit
      dynamo: Dynamo
  ): Option[Bake] = {
    if (buildNumber > 0) {
      val bake = findById(recipeId, buildNumber)
      if (bake.isEmpty || bake.exists(_.status != BakeStatus.Complete))
        findPreviousSuccessfulBake(recipeId, buildNumber - 1)
      else bake
    } else None
  }

  def scanForAll()(implicit dynamo: Dynamo): List[Bake.DbModel] = {
    table
      .scan()
      .exec()
      .flatMap(_.toOption)
  }

  def markToDelete(bakeId: BakeId)(implicit dynamo: Dynamo): Unit = {
    table
      .when(attributeExists("recipeId") and attributeExists("buildNumber"))
      .update(
        ("recipeId" === bakeId.recipeId) and ("buildNumber" === bakeId.buildNumber),
        set("deleted", true)
      )
      .exec()
  }

  def findDeleted()(implicit dynamo: Dynamo): List[Bake.DbModel] = {
    val res = table
      .filter("deleted" === true)
      .scan()
      .exec()

    res.foreach {
      case Left(error) =>
        log.error(
          s"findDeleted failed with a read error: ${DynamoReadError.describe(error)}"
        )
      case _ => // ignore
    }

    res.flatMap(_.toOption)
  }

  def deleteById(bakeId: BakeId)(implicit dynamo: Dynamo): Unit = {
    table
      .delete(
        ("recipeId" === bakeId.recipeId) and ("buildNumber" === bakeId.buildNumber)
      )
      .exec()
  }

  private def table(implicit dynamo: Dynamo) = dynamo.Tables.bakes.table

}
