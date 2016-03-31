package models

import org.joda.time.DateTime

case class Bake(recipe: Recipe,
    buildNumber: Int,
    status: BakeStatus,
    amiId: Option[AmiId],
    startedBy: String,
    startedAt: DateTime) {
  val bakeId = BakeId(recipe.id, buildNumber)
}

object Bake {

  case class DbModel(
    recipeId: RecipeId,
    buildNumber: Int,
    status: BakeStatus,
    amiId: Option[AmiId],
    startedBy: String,
    startedAt: DateTime)

  import automagic._

  def domain2db(bake: Bake): DbModel =
    transform[Bake, Bake.DbModel](bake, "recipeId" -> bake.recipe.id)

  def db2domain(dbModel: DbModel, recipe: Recipe): Bake =
    transform[Bake.DbModel, Bake](dbModel, "recipe" -> recipe)

}