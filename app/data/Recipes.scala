package data

import software.amazon.awssdk.services.dynamodb.model._
import models.Recipe.DbModel
import models._
import org.joda.time.DateTime
import org.scanamo.DynamoReadError
import org.scanamo.generic.auto.genericDerivedFormat
import org.scanamo.syntax._

import scala.jdk.CollectionConverters._

object Recipes {
  import Dynamo._
  import DynamoFormats._

  def list()(implicit dynamo: Dynamo): Iterable[Recipe] = filteredList(_ => true)

  def filteredList(p: DbModel => Boolean)(implicit dynamo: Dynamo): Iterable[Recipe] = {
    val dbModels = table.scan().exec().collect { case Right(dbModel) => dbModel }
    for {
      dbModel <- dbModels
      if p(dbModel)
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe.db2domain(dbModel, baseImage)
    }
  }

  def recipesWithErrors()(implicit dynamo: Dynamo): (List[DynamoReadError], List[Recipe]) = {
    val dbResponse = table.scan().exec()
    val errors = dbResponse.collect { case Left(error) => error }
    val models = dbResponse.collect { case Right(recipe) => recipe }

    val recipes = for {
      dbModel <- models
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe.db2domain(dbModel, baseImage)
    }
    (errors, recipes)
  }

  def create(
    id: RecipeId,
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

  def update(
    recipe: Recipe,
    description: Option[String],
    baseImage: BaseImage,
    diskSize: Option[Int],
    roles: List[CustomisedRole],
    modifiedBy: String,
    bakeSchedule: Option[BakeSchedule],
    encryptFor: List[AccountNumber])(implicit dynamo: Dynamo): Either[DynamoReadError, Recipe] = {

    val baseUpdateExpr =
      set("baseImageId", baseImage.id) and
        set("roles", roles) and
        set("modifiedBy", modifiedBy) and
        set("modifiedAt", DateTime.now()) and
        (if (bakeSchedule.isDefined) set("bakeSchedule", bakeSchedule) else remove("bakeSchedule")) and
        (if (encryptFor.nonEmpty) set("encryptFor", encryptFor) else remove("encryptFor")) and
        (if (diskSize.isDefined) set("diskSize", diskSize) else remove("diskSize"))

    val updateExpr = description match {
      case Some(desc) => baseUpdateExpr and set("description", desc)
      case None => baseUpdateExpr
    }

    val update = table.update("id" === recipe.id, updateExpr)
    update.exec().map(Recipe.db2domain(_, baseImage))
  }

  def delete(recipe: Recipe)(implicit dynamo: Dynamo): Unit = {
    table.delete("id" === recipe.id.value).exec()
  }

  def findById(id: RecipeId)(implicit dynamo: Dynamo): Option[Recipe] = {
    val dbModel: Option[DbModel] = table.get("id" === id).exec().flatMap { attempt =>
      attempt match {
        case Right(dbModel) => Some(dbModel)
        case Left(_) => None
      }
    }
    for {
      dbModel <- dbModel
      baseImage <- BaseImages.findById(dbModel.baseImageId)
    } yield {
      Recipe.db2domain(dbModel, baseImage)
    }
  }

  def findByBaseImage(imageId: BaseImageId)(implicit dynamo: Dynamo): Iterable[Recipe] = filteredList(_.baseImageId == imageId)

  def incrementAndGetBuildNumber(id: RecipeId)(implicit dynamo: Dynamo): Option[Int] = {
    val updateRequest = UpdateItemRequest.builder()
      .tableName(table.name)
      .key(Map("id" -> AttributeValue.builder.s(id.value).build()).asJava)
      .updateExpression("ADD nextBuildNumber :val1")
      .conditionExpression("attribute_exists(id)") // to ensure the recipe exists in Dynamo
      .expressionAttributeValues(Map(":val1" -> AttributeValue.builder().n("1").build()).asJava)
      .returnValues(ReturnValue.UPDATED_NEW) // TODO Scanamo doesn't support this
      .build
    val updateResult = dynamo.client.updateItem(updateRequest)
    if (updateResult.attributes.containsKey("nextBuildNumber"))
      Some(updateResult.attributes.get("nextBuildNumber").n.toInt)
    else
      None
  }

  private def table(implicit dynamo: Dynamo) = dynamo.Tables.recipes.table

}
