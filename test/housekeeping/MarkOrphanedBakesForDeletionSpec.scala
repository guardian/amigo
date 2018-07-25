package housekeeping

import models._
import org.joda.time.{ DateTime, DateTimeZone }
import org.scalatest.{ FlatSpec, Matchers }

class MarkOrphanedBakesForDeletionSpec extends FlatSpec with Matchers {
  val date = new DateTime(2018, 5, 28, 0, 0, 0, DateTimeZone.UTC)

  def fixtureBakeDbModel(recipeId: String, amiId: Option[AmiId]): Bake.DbModel = {
    Bake.DbModel(RecipeId(recipeId), 1, BakeStatus.Complete, amiId, "user", date, None)
  }

  val orphanBakes = List(fixtureBakeDbModel("recipe-x", None), fixtureBakeDbModel("recipe-y", None))
  val otherBakes = List(fixtureBakeDbModel("recipe-1", None), fixtureBakeDbModel("recipe-2", None))

  "findOrphanedBakeIds" should "return empty when there are no orphaned bakes" in {
    val recipeIds = Set(RecipeId("recipe-1"), RecipeId("recipe-2"), RecipeId("recipe-3"), RecipeId("recipe-4"))
    val markedBakes = MarkOrphanedBakesForDeletion.findOrphanedBakeIds(recipeIds, otherBakes)

    markedBakes.size shouldEqual 0
  }

  it should "only find the orphaned bakes" in {
    val recipeIds = Set(RecipeId("recipe-1"), RecipeId("recipe-2"), RecipeId("recipe-3"), RecipeId("recipe-4"))
    val markedBakes = MarkOrphanedBakesForDeletion.findOrphanedBakeIds(recipeIds, otherBakes ++ orphanBakes)

    markedBakes.size shouldEqual 2
    markedBakes shouldEqual List(BakeId(RecipeId("recipe-x"), 1), BakeId(RecipeId("recipe-y"), 1))
  }

  it should "find all the orphaned bakes" in {
    val recipeIds = Set(RecipeId("recipe-1"), RecipeId("recipe-3"), RecipeId("recipe-4"))
    val markedBakes = MarkOrphanedBakesForDeletion.findOrphanedBakeIds(recipeIds, otherBakes ++ orphanBakes)

    markedBakes.size shouldEqual 3
    markedBakes.toSet shouldEqual Set(BakeId(RecipeId("recipe-x"), 1), BakeId(RecipeId("recipe-y"), 1), BakeId(RecipeId("recipe-2"), 1))
  }
}
