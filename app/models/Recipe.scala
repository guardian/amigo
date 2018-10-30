package models

import org.joda.time.DateTime

case class Recipe(
  id: RecipeId,
  description: Option[String],
  baseImage: BaseImage,
  diskSize: Option[Int],
  roles: List[CustomisedRole],
  createdBy: String,
  createdAt: DateTime,
  modifiedBy: String,
  modifiedAt: DateTime,
  bakeSchedule: Option[BakeSchedule],
  encryptFor: List[AccountNumber])

object Recipe {

  case class DbModel(
    id: RecipeId,
    description: Option[String],
    baseImageId: BaseImageId,
    diskSize: Option[Int],
    roles: List[CustomisedRole],
    nextBuildNumber: Int,
    createdBy: String,
    createdAt: DateTime,
    modifiedBy: String,
    modifiedAt: DateTime,
    bakeSchedule: Option[BakeSchedule],
    encryptFor: Option[List[AccountNumber]])

  import automagic._

  def db2domain(dbModel: DbModel, baseImage: BaseImage): Recipe = transform[DbModel, Recipe](dbModel, "baseImage" -> baseImage, "encryptFor" -> dbModel.encryptFor.getOrElse(Nil))

  def domain2db(recipe: Recipe, nextBuildNumber: Int): DbModel = transform[Recipe, DbModel](recipe, "baseImageId" -> recipe.baseImage.id, "nextBuildNumber" -> nextBuildNumber, "encryptFor" -> Some(recipe.encryptFor))

}
