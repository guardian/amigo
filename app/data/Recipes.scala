package data

import com.amazonaws.services.dynamodbv2.model._
import models._
import org.joda.time.DateTime
import com.gu.scanamo.syntax._
import cats.syntax.either._

import scala.collection.JavaConverters._

object Recipes {
  import Dynamo._

  def list()(implicit dynamo: Dynamo): Iterable[Recipe] = {
    val dbModels = table.scan().exec().flatMap(_.toOption)
    for {
      dbModel <- dbModels
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe.db2domain(dbModel, baseImage)
    }
  }

  def create(id: RecipeId,
    description: String,
    baseImage: BaseImage,
    roles: List[CustomisedRole],
    createdBy: String,
    bakeSchedule: Option[BakeSchedule])(implicit dynamo: Dynamo): Recipe = {
    val now = DateTime.now()
    val recipe = Recipe(id, description, baseImage, roles, createdBy, createdAt = now, modifiedBy = createdBy, modifiedAt = now, bakeSchedule)
    table.put(Recipe.domain2db(recipe, nextBuildNumber = 0)).exec()

    recipe
  }

  def update(recipe: Recipe,
    description: String,
    baseImage: BaseImage,
    roles: List[CustomisedRole],
    modifiedBy: String,
    bakeSchedule: Option[BakeSchedule])(implicit dynamo: Dynamo): Recipe = {
    val updated = recipe.copy(
      description = description,
      baseImage = baseImage,
      roles = roles,
      modifiedBy = modifiedBy,
      modifiedAt = DateTime.now(),
      bakeSchedule = bakeSchedule
    )
    // TODO This is a bit horrible. We have to get the record from Dynamo just to copy the next build number over. Really we want to do a partial update.
    // This is currently blocked on guardian/scanamo#62.
    val ops = for {
      recipe <- table.get('id -> recipe.id)
      nextBuildNumber = recipe.flatMap(_.toOption).map(_.nextBuildNumber).getOrElse(0)
      _ <- table.put(Recipe.domain2db(updated, nextBuildNumber))
    } yield ()
    ops.exec()

    updated
  }

  def findById(id: RecipeId)(implicit dynamo: Dynamo): Option[Recipe] = {
    for {
      dbModel <- table.get('id -> id).exec().flatMap(_.toOption)
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe.db2domain(dbModel, baseImage)
    }
  }

  def incrementAndGetBuildNumber(id: RecipeId)(implicit dynamo: Dynamo): Option[Int] = {
    val updateRequest = new UpdateItemRequest()
      .withTableName(table.name)
      .withKey(Map("id" -> new AttributeValue(id.value)).asJava)
      .withUpdateExpression("ADD nextBuildNumber :val1")
      .withConditionExpression("attribute_exists(id)") // to ensure the recipe exists in Dynamo
      .withExpressionAttributeValues(Map(":val1" -> new AttributeValue().withN("1")).asJava)
      .withReturnValues(ReturnValue.UPDATED_NEW) // TODO Scanamo doesn't support this
    val updateResult = dynamo.client.updateItem(updateRequest)
    if (updateResult.getAttributes.containsKey("nextBuildNumber"))
      Some(updateResult.getAttributes.get("nextBuildNumber").getN.toInt)
    else
      None
  }

  private def table(implicit dynamo: Dynamo) = dynamo.Tables.recipes.table

}
