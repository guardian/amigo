package prism

import models._
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import prism.Prism.{
  AWSAccount,
  Image,
  Instance,
  LaunchConfiguration,
  LaunchTemplate
}
import services.PrismData

class RecipeUsageSpec extends AnyFlatSpec with Matchers with MockitoSugar {

  def fixtureBaseImage(baseImageId: String): BaseImage = BaseImage(
    BaseImageId(baseImageId),
    "MyDescription",
    AmiId("ami-1"),
    List(),
    "Test",
    DateTime.now,
    "Test",
    DateTime.now
  )
  def fixtureRecipe(id: String): Recipe = Recipe(
    RecipeId(id),
    None,
    fixtureBaseImage(s"base-image-$id"),
    None,
    List(),
    "Test",
    DateTime.now,
    "Test",
    DateTime.now,
    None,
    Nil
  )
  def fixtureRecipeWithSize(id: String, size: Int): Recipe = Recipe(
    RecipeId(id),
    None,
    fixtureBaseImage(s"base-image-$id"),
    Some(100),
    List(),
    "Test",
    DateTime.now,
    "Test",
    DateTime.now,
    None,
    Nil
  )
  def fixtureBake(recipe: Recipe, amiId: Option[AmiId]): Bake = Bake(
    recipe,
    1,
    amiId.map(_ => BakeStatus.Complete).getOrElse(BakeStatus.Failed),
    amiId,
    "Test",
    DateTime.now,
    false
  )

  val emptyUsage: RecipeUsage = RecipeUsage(Seq(), Seq(), Seq(), Seq())
  val instanceUsage: RecipeUsage = RecipeUsage(
    Seq(
      Instance("weatherwax", AmiId("a-tuin"), AWSAccount("Gaspode", "carrot"))
    ),
    Seq(),
    Seq(),
    Seq()
  )
  val launchConfigurationUsage = RecipeUsage(
    Seq(),
    Seq(
      LaunchConfiguration(
        "launch-configuration-1",
        AmiId("a-tuin"),
        AWSAccount("Gaspode", "carrot")
      )
    ),
    Seq(),
    Seq()
  )
  val launchTemplateUsage: RecipeUsage = RecipeUsage(
    Seq(),
    Seq(),
    Seq(
      LaunchTemplate(
        "lt-1",
        AmiId("a-tuin"),
        AWSAccount("Gaspode", "carrot")
      )
    ),
    Seq()
  )

  "RecipeUsage" should "find for each recipes where they are being used" in {
    val amiId1 = AmiId("1")
    val amiId2 = AmiId("2")
    val amiId3 = AmiId("3")
    val amiId4 = AmiId("4")
    val amiId5 = AmiId("5")
    val amiId6 = AmiId("6")

    val recipe1 = fixtureRecipe("recipe1")
    val recipe2 = fixtureRecipeWithSize("recipe2", 100)
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
        case _ =>
          Seq() // Without this compilation fails due to non-exhaustive match
      }
    }

    val account = AWSAccount("accountName", "1234567890")

    val instance1 = Instance("i-1", amiId1, account)
    val instance2 = Instance("i-2", amiId2, account)
    val instance5 = Instance("i-5", amiId5, account)

    val lc1 = LaunchConfiguration("lc-1", amiId1, account)
    val lc2 = LaunchConfiguration("lc-2", amiId3, account)

    val lt1 = LaunchTemplate("lt-1", amiId1, account)

    val mockPrismAgents = mock[PrismData]
    when(mockPrismAgents.allInstances) thenReturn Seq(
      instance1,
      instance2,
      instance5
    )
    when(mockPrismAgents.allLaunchConfigurations) thenReturn Seq(lc1, lc2)
    when(mockPrismAgents.allLaunchTemplates) thenReturn Seq(lt1)
    when(mockPrismAgents.copiedImages(any())) thenReturn Map[AmiId, Seq[
      Image
    ]]()
    when(mockPrismAgents.copiedImages(Set(AmiId("3")))) thenReturn Map(
      AmiId("3") -> Seq(
        Image(AmiId("5"), "1234", AmiId("3"), None, "available")
      )
    )

    val usages: Map[Recipe, RecipeUsage] =
      RecipeUsage.forAll(recipes, bakes)(mockPrismAgents)

    usages.size shouldBe 3
    usages.keySet shouldBe Set(recipe1, recipe2, recipe3)

    val recipe1Usages = usages(recipe1)
    recipe1Usages.instances shouldBe Seq(instance1, instance2)
    recipe1Usages.launchConfigurations shouldBe Seq(lc1)
    recipe1Usages.launchTemplates shouldBe Seq(lt1)
    recipe1Usages.bakeUsage.sortBy(_.amiId.value) shouldBe Seq(
      BakeUsage(AmiId("1"), bakeR1A1, None, Seq(instance1), Seq(lc1), Seq(lt1)),
      BakeUsage(
        AmiId("2"),
        bakeR1A2,
        None,
        Seq(instance2),
        Seq.empty,
        Seq.empty
      )
    )

    val recipe2Usages = usages(recipe2)
    recipe2Usages.instances shouldBe Seq(instance5)
    recipe2Usages.launchConfigurations shouldBe Seq(lc2)
    recipe2Usages.bakeUsage.sortBy(_.amiId.value) shouldBe Seq(
      BakeUsage(AmiId("3"), bakeR2A3, None, Seq.empty, Seq(lc2), Seq.empty),
      BakeUsage(
        AmiId("5"),
        bakeR2A3,
        Some(Image(AmiId("5"), "1234", AmiId("3"), None, "available")),
        Seq(instance5),
        Seq.empty,
        Seq.empty
      )
    )

    val recipe3Usages = usages(recipe3)
    recipe3Usages.instances shouldBe Seq.empty
    recipe3Usages.launchConfigurations shouldBe Seq.empty
    recipe3Usages.launchTemplates shouldBe Seq.empty
    recipe3Usages.bakeUsage shouldBe Seq.empty
  }

  "hasUsage" should "return true if a recipe is used by an instance" in {
    val recipe1 = fixtureRecipe("recipe1")
    val recipe2 = fixtureRecipe("recipe2")
    val usages = Map(recipe1 -> emptyUsage, recipe2 -> instanceUsage)

    RecipeUsage.hasUsage(recipe1, usages) shouldBe false
    RecipeUsage.hasUsage(recipe2, usages) shouldBe true
  }

  "hasUsage" should "return true if a recipe is used by a launch configuration" in {
    val recipe1 = fixtureRecipe("recipe1")
    val recipe2 = fixtureRecipe("recipe2")
    val usages = Map(recipe1 -> emptyUsage, recipe2 -> launchConfigurationUsage)

    RecipeUsage.hasUsage(recipe1, usages) shouldBe false
    RecipeUsage.hasUsage(recipe2, usages) shouldBe true
  }

  "hasUsage" should "return true if a recipe is used by a launch template" in {
    val recipe1 = fixtureRecipe("recipe1")
    val recipe2 = fixtureRecipe("recipe2")
    val usages = Map(recipe1 -> launchTemplateUsage, recipe2 -> emptyUsage)

    RecipeUsage.hasUsage(recipe1, usages) shouldBe true
    RecipeUsage.hasUsage(recipe2, usages) shouldBe false
  }
}
