package prism

import models._
import org.joda.time.DateTime
import org.scalatest.{ FlatSpec, Matchers }
import prism.Prism.{ Instance, LaunchConfiguration }
import services.PrismAgents
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar

class RecipeUsageSpec extends FlatSpec with Matchers with MockitoSugar {

  def fixtureBaseImage(baseImageId: String): BaseImage = BaseImage(BaseImageId(baseImageId), "MyDescription", AmiId("ami-1"), List(), "Test", DateTime.now, "Test", DateTime.now)
  def fixtureRecipe(id: String): Recipe = Recipe(RecipeId(id), None, fixtureBaseImage(s"base-image-$id"), List(), "Test", DateTime.now, "Test", DateTime.now, None, Nil)
  def fixtureBake(recipe: Recipe, amiId: Option[AmiId]): Bake = Bake(recipe, 1, amiId.map(_ => BakeStatus.Complete).getOrElse(BakeStatus.Failed), amiId, "Test", DateTime.now)

  "RecipeUsage" should "find for each recipes where they are being used" in {
    val amiId1 = AmiId("1")
    val amiId2 = AmiId("2")
    val amiId3 = AmiId("3")
    val amiId4 = AmiId("4")

    val recipe1 = fixtureRecipe("recipe1")
    val recipe2 = fixtureRecipe("recipe2")
    val recipe3 = fixtureRecipe("recipe3")
    val recipes = Seq(recipe1, recipe2, recipe3)

    def bakes(recipeId: RecipeId): Iterable[Bake] = {
      recipeId match {
        case recipe1.id => Seq(fixtureBake(recipe1, Some(amiId1)), fixtureBake(recipe1, Some(amiId2)))
        case recipe2.id => Seq(fixtureBake(recipe2, Some(amiId3)), fixtureBake(recipe2, None))
        case recipe3.id => Seq(fixtureBake(recipe3, Some(amiId4)))
      }
    }

    val instance1 = Instance(amiId1.value)
    val instance2 = Instance(amiId2.value)

    val lc1 = LaunchConfiguration(amiId1.value)
    val lc2 = LaunchConfiguration(amiId3.value)

    val mockPrismAgents = mock[PrismAgents]
    when(mockPrismAgents.allInstances) thenReturn Seq(instance1, instance2)
    when(mockPrismAgents.allLaunchConfigurations) thenReturn Seq(lc1, lc2)

    val usages: Map[Recipe, RecipeUsage] = RecipeUsage.forAll(recipes, bakes)(mockPrismAgents)
    val expected: Map[Recipe, RecipeUsage] = Map(
      recipe1 -> RecipeUsage(instances = Seq(instance1, instance2), launchConfigurations = Seq(lc1)),
      recipe2 -> RecipeUsage(instances = Seq(), launchConfigurations = Seq(lc2)),
      recipe3 -> RecipeUsage(instances = Seq(), launchConfigurations = Seq())
    )
    usages should be(expected)
  }

}
