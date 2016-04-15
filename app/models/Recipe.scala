package models

import org.joda.time.DateTime

case class Recipe(
  id: RecipeId,
  description: String,
  baseImage: BaseImage,
  roles: List[CustomisedRole],
  createdBy: String,
  createdAt: DateTime,
  modifiedBy: String,
  modifiedAt: DateTime)

object Recipe {

  case class DbModel(
    id: RecipeId,
    description: String,
    baseImageId: BaseImageId,
    roles: List[CustomisedRole],
    nextBuildNumber: Int,
    createdBy: String,
    createdAt: DateTime,
    modifiedBy: String,
    modifiedAt: DateTime)

  import automagic._

  def db2domain(dbModel: DbModel, baseImage: BaseImage): Recipe = transform[DbModel, Recipe](dbModel, "baseImage" -> baseImage)

  def domain2db(recipe: Recipe, nextBuildNumber: Int): DbModel = transform[Recipe, DbModel](recipe, "baseImageId" -> recipe.baseImage.id, "nextBuildNumber" -> nextBuildNumber)

}
