package models

import org.joda.time.DateTime

case class Bake(recipe: Recipe,
    buildNumber: Int,
    status: BakeStatus,
    amiId: Option[AmiId],
    startedBy: String,
    startedAt: DateTime,
    deleted: Boolean) {
  val bakeId = BakeId(recipe.id, buildNumber)
}

object Bake {

  case class DbModel(
      recipeId: RecipeId,
      buildNumber: Int,
      status: BakeStatus,
      amiId: Option[AmiId],
      startedBy: String,
      startedAt: DateTime,
      deleted: Option[Boolean]) {
    val bakeId = BakeId(recipeId, buildNumber)
  }

  import automagic._

  def domain2db(bake: Bake): DbModel =
    transform[Bake, Bake.DbModel](bake, "recipeId" -> bake.recipe.id, "deleted" -> Some(bake.deleted))

  def db2domain(dbModel: DbModel, recipe: Recipe): Bake =
    transform[Bake.DbModel, Bake](dbModel, "recipe" -> recipe, "deleted" -> dbModel.deleted.getOrElse(false))

}