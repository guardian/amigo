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
    bakeDay: Option[String],
    encryptFor: List[AccountNumber]
)

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
      bakeDay: Option[String],
      encryptFor: Option[List[AccountNumber]]
  )

  def db2domain(dbModel: DbModel, baseImage: BaseImage): Recipe = Recipe(
    id = dbModel.id,
    description = dbModel.description,
    baseImage = baseImage,
    diskSize = dbModel.diskSize,
    roles = dbModel.roles,
    createdBy = dbModel.createdBy,
    createdAt = dbModel.createdAt,
    modifiedBy = dbModel.modifiedBy,
    modifiedAt = dbModel.modifiedAt,
    bakeSchedule = dbModel.bakeSchedule,
    bakeDay = dbModel.bakeDay,
    encryptFor = dbModel.encryptFor.getOrElse(Nil)
  )

  def domain2db(recipe: Recipe, nextBuildNumber: Int): DbModel = DbModel(
    id = recipe.id,
    description = recipe.description,
    baseImageId = recipe.baseImage.id,
    diskSize = recipe.diskSize,
    roles = recipe.roles,
    nextBuildNumber = nextBuildNumber,
    createdBy = recipe.createdBy,
    createdAt = recipe.createdAt,
    modifiedBy = recipe.modifiedBy,
    modifiedAt = recipe.modifiedAt,
    bakeSchedule = recipe.bakeSchedule,
    bakeDay = recipe.bakeDay,
    encryptFor = Some(recipe.encryptFor)
  )

}
