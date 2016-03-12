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

}
