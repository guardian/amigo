package prism

import models._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.{ FlatSpec, Matchers }
import org.scalatest.mock.MockitoSugar
import prism.Prism.{ AWSAccount, Image, Instance, LaunchConfiguration }
import services.PrismAgents

class RecipeUsageSpec extends FlatSpec with Matchers with MockitoSugar {

  def fixtureBaseImage(baseImageId: String): BaseImage = BaseImage(BaseImageId(baseImageId), "MyDescription", AmiId("ami-1"), List(), "Test", DateTime.now, "Test", DateTime.now)
  def fixtureRecipe(id: String): Recipe = Recipe(RecipeId(id), None, fixtureBaseImage(s"base-image-$id"), List(), "Test", DateTime.now, "Test", DateTime.now, None, Nil)
  def fixtureBake(recipe: Recipe, amiId: Option[AmiId]): Bake = Bake(recipe, 1, amiId.map(_ => BakeStatus.Complete).getOrElse(BakeStatus.Failed), amiId, "Test", DateTime.now)

  "RecipeUsage" should "find for each recipes where they are being used" in {
    val amiId1 = AmiId("1")
    val amiId2 = AmiId("2")
    val amiId3 = AmiId("3")
    val amiId4 = AmiId("4")
    val amiId5 = AmiId("5")

    val recipe1 = fixtureRecipe("recipe1")
    val recipe2 = fixtureRecipe("recipe2")
    val recipe3 = fixtureRecipe("recipe3")
    val recipes = Seq(recipe1, recipe2, recipe3)

    val bakeR1A1 = fixtureBake(recipe1, Some(amiId1))
    val bakeR1A2 = fixtureBake(recipe1, Some(amiId2))
    val bakeR2A3 = fixtureBake(recipe2, Some(amiId3))
    val bakeR2F = fixtureBake(recipe2, None)
    val bakeR3A4 = fixtureBake(recipe3, Some(amiId4))

    def bakes(recipeId: RecipeId): Iterable[Bake] = {
      recipeId match {
        case recipe1.id => Seq(bakeR1A1, bakeR1A2)
        case recipe2.id => Seq(bakeR2A3, bakeR2F)
        case recipe3.id => Seq(bakeR3A4)
      }
    }

    val account = AWSAccount("accountName", "1234567890")

    val instance1 = Instance("i-1", amiId1.value, account)
    val instance2 = Instance("i-2", amiId2.value, account)
    val instance5 = Instance("i-5", amiId5.value, account)

    val lc1 = LaunchConfiguration("lc-1", amiId1.value, account)
    val lc2 = LaunchConfiguration("lc-2", amiId3.value, account)

    val mockPrismAgents = mock[PrismAgents]
    when(mockPrismAgents.allInstances) thenReturn Seq(instance1, instance2, instance5)
    when(mockPrismAgents.allLaunchConfigurations) thenReturn Seq(lc1, lc2)
    when(mockPrismAgents.copiedImages(any())) thenReturn Map[String, Seq[Image]]()
    when(mockPrismAgents.copiedImages(Set("3"))) thenReturn Map("3" -> Seq(Image("5", "1234", "3", None, "available")))

    val usages: Map[Recipe, RecipeUsage] = RecipeUsage.forAll(recipes, bakes)(mockPrismAgents)

    usages.size shouldBe 3
    usages.keySet shouldBe Set(recipe1, recipe2, recipe3)

    val recipe1Usages = usages(recipe1)
    recipe1Usages.instances shouldBe Seq(instance1, instance2)
    recipe1Usages.launchConfigurations shouldBe Seq(lc1)
    recipe1Usages.bakeUsage.sortBy(_.amiId) shouldBe Seq(
      BakeUsage("1", bakeR1A1, None, Seq(instance1), Seq(lc1)),
      BakeUsage("2", bakeR1A2, None, Seq(instance2), Seq.empty)
    )

    val recipe2Usages = usages(recipe2)
    recipe2Usages.instances shouldBe Seq(instance5)
    recipe2Usages.launchConfigurations shouldBe Seq(lc2)
    recipe2Usages.bakeUsage.sortBy(_.amiId) shouldBe Seq(
      BakeUsage("3", bakeR2A3, None, Seq.empty, Seq(lc2)),
      BakeUsage("5", bakeR2A3, Some(Image("5", "1234", "3", None, "available")), Seq(instance5), Seq.empty)
    )

    val recipe3Usages = usages(recipe3)
    recipe3Usages.instances shouldBe Seq.empty
    recipe3Usages.launchConfigurations shouldBe Seq.empty
    recipe3Usages.bakeUsage shouldBe Seq.empty
  }

}
