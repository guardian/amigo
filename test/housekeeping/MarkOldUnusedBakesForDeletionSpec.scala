package housekeeping

import models._
import org.joda.time.{ DateTime, DateTimeZone }
import org.scalatest.{ FlatSpec, Matchers }
import prism.{ BakeUsage, RecipeUsage }

class MarkOldUnusedBakesForDeletionSpec extends FlatSpec with Matchers {
  val oldDate = new DateTime(2018, 5, 28, 0, 0, 0, DateTimeZone.UTC)
  val newDate = new DateTime(2018, 6, 20, 0, 0, 0, DateTimeZone.UTC)
  val oldBake1: Bake = fixtureBake(fixtureRecipe("recipe-1", oldDate), Some(AmiId("ami-1")), oldDate)
  val oldBake2: Bake = fixtureBake(fixtureRecipe("recipe-2", oldDate), Some(AmiId("ami-2")), oldDate)
  val newBake1: Bake = fixtureBake(fixtureRecipe("recipe-3", newDate), Some(AmiId("ami-3")), newDate)
  val newBake2: Bake = fixtureBake(fixtureRecipe("recipe-4", newDate), Some(AmiId("ami-4")), newDate)

  def fixtureBaseImage(baseImageId: String, createdDate: DateTime): BaseImage = {
    BaseImage(BaseImageId(baseImageId), "MyDescription", AmiId("ami-1"), List(), "Test", createdDate, "Test", createdDate)
  }
  def fixtureRecipe(id: String, createdDate: DateTime): Recipe = {
    Recipe(RecipeId(id), None, fixtureBaseImage(s"base-image-$id", createdDate), None, List(), "Test", createdDate, "Test", createdDate, None, Nil)
  }
  def fixtureBake(recipe: Recipe, amiId: Option[AmiId], startedAt: DateTime): Bake = {
    Bake(recipe, 1, amiId.map(_ => BakeStatus.Complete).getOrElse(BakeStatus.Failed), amiId, "Test", startedAt, deleted = false)
  }

  def getBakes(recipeId: RecipeId): Iterable[Bake] = Iterable(oldBake1, oldBake2, newBake1, newBake2)
  def getEmptyRecipeUsage(bakes: Iterable[Bake]): RecipeUsage = RecipeUsage(Seq.empty, Seq.empty, Seq.empty)

  def getRecipeUsage(bakes: Iterable[Bake]): RecipeUsage = {
    val bakeUsageA = BakeUsage(AmiId("ami-2"), oldBake2, None, Seq.empty, Seq.empty)
    val bakeUsageB = BakeUsage(AmiId("ami-4"), newBake2, None, Seq.empty, Seq.empty)
    RecipeUsage(Seq.empty, Seq.empty, Seq(bakeUsageA, bakeUsageB))
  }

  "getOldUnusedBakes" should "return empty when all bakes are recent" in {
    val housekeepingDate = new DateTime(2018, 6, 24, 0, 0, 0, DateTimeZone.UTC)
    val recipeIds = Set(RecipeId("recipe-1"), RecipeId("recipe-2"), RecipeId("recipe-3"), RecipeId("recipe-4"))
    val markedBakes = MarkOldUnusedBakesForDeletion.getOldUnusedBakes(recipeIds, housekeepingDate, getBakes, getEmptyRecipeUsage)

    markedBakes.size shouldEqual 0
  }

  it should "find all the unused bakes that are older than the max age" in {
    val housekeepingDate = new DateTime(2018, 7, 12, 0, 0, 0, DateTimeZone.UTC)
    val recipeIds = Set(RecipeId("recipe-1"), RecipeId("recipe-2"), RecipeId("recipe-3"), RecipeId("recipe-4"))
    val markedBakes = MarkOldUnusedBakesForDeletion.getOldUnusedBakes(recipeIds, housekeepingDate, getBakes, getEmptyRecipeUsage)

    markedBakes.size shouldEqual 2
    markedBakes.map(_.bakeId) shouldEqual Set(BakeId(RecipeId("recipe-1"), 1), BakeId(RecipeId("recipe-2"), 1))
  }

  it should "not include old bakes that are still in use" in {
    val housekeepingDate = new DateTime(2018, 7, 12, 0, 0, 0, DateTimeZone.UTC)
    val recipeIds = Set(RecipeId("recipe-1"), RecipeId("recipe-2"), RecipeId("recipe-3"), RecipeId("recipe-4"))
    val markedBakes = MarkOldUnusedBakesForDeletion.getOldUnusedBakes(recipeIds, housekeepingDate, getBakes, getRecipeUsage)

    markedBakes.size shouldEqual 1
    markedBakes.map(_.bakeId) shouldEqual Set(BakeId(RecipeId("recipe-1"), 1))
  }
}
