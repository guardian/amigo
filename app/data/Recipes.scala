package data

import cats.implicits.catsSyntaxMonadCombineSeparate
import cats.instances.either._
import cats.instances.list._
import cats.syntax.either._
import com.amazonaws.services.dynamodbv2.model._
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.syntax._
import models.Recipe.DbModel
import models._
import org.joda.time.DateTime

import scala.collection.JavaConverters._

object Recipes {
  import Dynamo._
  import DynamoFormats._

  def list()(implicit dynamo: Dynamo): Iterable[Recipe] = filteredList(_ => true)

  def filteredList(p: DbModel => Boolean)(implicit dynamo: Dynamo): Iterable[Recipe] = {
    val dbModels = table.scan().exec().flatMap(_.toOption)
    for {
      dbModel <- dbModels
      if p(dbModel)
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe.db2domain(dbModel, baseImage)
    }
  }

  def recipesWithErrors()(implicit dynamo: Dynamo): (List[DynamoReadError], List[Recipe]) = {
    val dbResponse: Traversable[Either[DynamoReadError, DbModel]] = table.scan().exec()
    val (errors, models) = dbResponse.toList.separate

    val recipes = for {
      dbModel <- models
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe.db2domain(dbModel, baseImage)
    }
    (errors, recipes)
  }

  def create(id: RecipeId,
    description: Option[String],
    baseImage: BaseImage,
    diskSize: Option[Int],

    roles: List[CustomisedRole],
    createdBy: String,
    bakeSchedule: Option[BakeSchedule],
    encryptedCopies: List[AccountNumber])(implicit dynamo: Dynamo): Recipe = {
    val now = DateTime.now()
    val recipe = Recipe(id, description, baseImage, diskSize, roles, createdBy, createdAt = now, modifiedBy = createdBy, modifiedAt = now, bakeSchedule, encryptedCopies)
    table.put(Recipe.domain2db(recipe, nextBuildNumber = 0)).exec()

    recipe
  }

  def update(recipe: Recipe,
    description: Option[String],
    baseImage: BaseImage,
    diskSize: Option[Int],
    roles: List[CustomisedRole],
    modifiedBy: String,
    bakeSchedule: Option[BakeSchedule],
    encryptFor: List[AccountNumber])(implicit dynamo: Dynamo): Either[DynamoReadError, Recipe] = {

    val baseUpdateExpr =
      set('baseImageId -> baseImage.id) and
        set('roles -> roles) and
        set('modifiedBy -> modifiedBy) and
        set('modifiedAt -> DateTime.now()) and
        (if (bakeSchedule.isDefined) set('bakeSchedule -> bakeSchedule) else remove('bakeSchedule)) and
        (if (encryptFor.nonEmpty) set('encryptFor -> encryptFor) else remove('encryptFor)) and
        (if (diskSize.isDefined) set('diskSize -> diskSize) else remove('diskSize))

    val updateExpr = description match {
      case Some(desc) => baseUpdateExpr and set('description -> desc)
      case None => baseUpdateExpr
    }

    val update = table.update('id -> recipe.id, updateExpr)
    update.exec().map(Recipe.db2domain(_, baseImage))
  }

  def delete(recipe: Recipe)(implicit dynamo: Dynamo): DeleteItemResult = {
    table.delete('id -> recipe.id.value).exec()
  }

  def findById(id: RecipeId)(implicit dynamo: Dynamo): Option[Recipe] = {
    for {
      dbModel <- table.get('id -> id).exec().flatMap(_.toOption)
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe.db2domain(dbModel, baseImage)
    }
  }

  def findByBaseImage(imageId: BaseImageId)(implicit dynamo: Dynamo): Iterable[Recipe] = filteredList(_.baseImageId == imageId)

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
