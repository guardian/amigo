package models

case class Bake(recipe: Recipe, buildNumber: Int) {
  val bakeId = BakeId(recipe.id, buildNumber)
}
