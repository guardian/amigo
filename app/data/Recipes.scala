package data

import cats.std.option._
import cats.std.list._
import cats.syntax.traverse._
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.Table
import com.gu.scanamo.ops.ScanamoOps
import com.gu.scanamo.syntax._
import models.{ RecipeId, _ }
import org.joda.time.DateTime

import scala.collection.JavaConverters._

class Recipes(table: Table[Recipe.DbModel], baseImages: BaseImages) {
  import DynamoFormats.dateTimeFormat

  def list(): ScanamoOps[Iterable[Recipe]] = {
    for {
      dbModels <- table.scan()
      models = dbModels.flatMap(_.toOption)
      baseImage <- models.traverse(m => baseImages.findById(m.baseImageId))
      baseImages = baseImage.flatten
    } yield {
      for {
        model <- models
        image <- baseImages
      } yield Recipe.db2domain(model, image)
    }
  }

  def create(id: RecipeId,
    description: String,
    baseImage: BaseImage,
    roles: List[CustomisedRole],
    createdBy: String,
    bakeSchedule: Option[BakeSchedule]): ScanamoOps[Recipe] = {
    val now = DateTime.now()
    val recipe = Recipe(id, description, baseImage, roles, createdBy, createdAt = now, modifiedBy = createdBy, modifiedAt = now, bakeSchedule)
    table.put(Recipe.domain2db(recipe, nextBuildNumber = 0)).map(_ => recipe
    )
  }

  def update(recipe: Recipe,
    description: String,
    baseImage: BaseImage,
    roles: List[CustomisedRole],
    modifiedBy: String,
    bakeSchedule: Option[BakeSchedule]): ScanamoOps[Recipe] = {
    val updated = recipe.copy(
      description = description,
      baseImage = baseImage,
      roles = roles,
      modifiedBy = modifiedBy,
      modifiedAt = DateTime.now(),
      bakeSchedule = bakeSchedule
    )
    // TODO This is a bit horrible. We have to get the record from Dynamo just to copy the next build number over. Really we want to do a partial update.
    for {
      recipe <- table.get('id -> recipe.id)
      nextBuildNumber = recipe.flatMap(_.toOption).map(_.nextBuildNumber).getOrElse(0)
      _ <- table.put(Recipe.domain2db(updated, nextBuildNumber))
    } yield updated
  }

  def findById(id: RecipeId): ScanamoOps[Option[Recipe]] = {
    for {
      dbModel <- table.get('id -> id)
      modelOpt = dbModel.flatMap(_.toOption)
      baseImage <- modelOpt.traverse(m => baseImages.findById(m.baseImageId))
      baseImageOpt = baseImage.flatten
    } yield {
      for {
        model <- modelOpt
        image <- baseImageOpt
      } yield Recipe.db2domain(model, image)
    }
  }

  def incrementAndGetBuildNumber(id: RecipeId)(implicit dynamo: Dynamo): Option[Int] = {
    val updateRequest = new UpdateItemRequest()
      .withTableName(table.name)
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

}
