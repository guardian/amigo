package models

case class Recipe(
  id: RecipeId,
  description: String,
  baseImage: BaseImage,
  features: Set[Feature])

