package models

case class Recipe(
  id: RecipeId,
  description: String,
  baseImage: BaseImage,
  roles: Seq[RoleId])

