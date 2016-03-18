package models

case class Recipe(
  id: RecipeId,
  description: String,
  baseImage: BaseImage,
  roles: List[CustomisedRole])

object Recipe {

  case class DbModel(
    id: RecipeId,
    description: String,
    baseImageId: BaseImageId,
    roles: List[CustomisedRole],
    nextBuildNumber: Int)

  import automagic._

  def db2domain(dbModel: DbModel, baseImage: BaseImage): Recipe = transform[DbModel, Recipe](dbModel, "baseImage" -> baseImage)

}
