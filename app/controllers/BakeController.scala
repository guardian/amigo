package controllers

import akka.stream.scaladsl.Source
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3Client }
import com.gu.googleauth.{ AuthAction, GoogleAuthConfig }
import data._
import event._
import models.BakeStatus.DeletionScheduled
import packer._
import models._
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.{ AmiMetadataLookup, Loggable, PrismData }
import play.api.libs.json._
import prism.{ RecipeUsage, SimpleBakeUsage }

class BakeController(
  val authAction: AuthAction[AnyContent],
  stage: String,
  prism: PrismData,
  components: ControllerComponents,
  ansibleVars: Map[String, String],
  debugAvailable: Boolean,
  amiMetadataLookup: AmiMetadataLookup,
  amigoDataBucket: Option[String],
  s3Client: AmazonS3,
  packerRunner: PackerRunner,
  bakeDeletionFrequencyMinutes: Int)(implicit dynamo: Dynamo, packerConfig: PackerConfig, eventBus: EventBus)
  extends AbstractController(components) with I18nSupport with Loggable {

  def startBaking(recipeId: RecipeId, debug: Boolean): Action[AnyContent] = authAction { request =>
    Recipes.findById(recipeId).fold[Result](NotFound) { recipe =>
      Recipes.incrementAndGetBuildNumber(recipe.id) match {
        case Some(buildNumber) =>
          val theBake = Bakes.create(recipe, buildNumber, startedBy = request.user.fullName)
          packerRunner.createImage(stage, theBake, prism, eventBus, ansibleVars, debugAvailable && debug, amiMetadataLookup, amigoDataBucket)
          Redirect(routes.BakeController.showBake(recipeId, buildNumber))
        case None =>
          val message = s"Failed to get the next build number for recipe $recipeId"
          log.warn(message)
          InternalServerError(message)
      }
    }
  }

  def showBake(recipeId: RecipeId, buildNumber: Int): Action[AnyContent] = authAction {
    val previousBakeId = Bakes.findPreviousSuccessfulBake(recipeId, buildNumber - 1).map(_.bakeId)
    Bakes.findById(recipeId, buildNumber).fold[Result](NotFound) { bake: Bake =>
      val bakeLogs = BakeLogs.list(BakeId(recipeId, buildNumber))
      val packageList = PackageList.getPackageList(s3Client, BakeId(recipeId, buildNumber), amigoDataBucket)
      val packageListDiff = packageList.flatMap(p => PackageList.getPackageListDiff(s3Client, p, previousBakeId, amigoDataBucket))

      val recipeUsage: RecipeUsage = RecipeUsage(Seq(bake))(prism)
      val recentCopies = prism.copiedImages(Set(bake.amiId).flatten)
      val bakeInUse = RecipeUsage.bakeIsUsed(recipeUsage, bake.amiId, recentCopies)
      Ok(views.html.showBake(bake, bakeLogs, packageList, packageListDiff, bakeInUse))
    }
  }

  def bakePackages(recipeId: RecipeId, buildNumber: Int): Action[AnyContent] = authAction {
    val list = PackageList.getPackageList(s3Client, BakeId(recipeId, buildNumber), amigoDataBucket)
    if (list.isLeft) {
      NotFound(s"Could not find package list for recipe $recipeId, bake $buildNumber: ${list.left.toOption.get}")
    } else {
      Ok(Json.obj("packages" -> Json.toJson(list.toOption.get)))
    }
  }

  def allBakeUsages: Action[AnyContent] = authAction {

    val allUsages = RecipeUsage.getUsages(Recipes.list())(prism, dynamo)

    val bakeUsages = SimpleBakeUsage.fromRecipeUsages(allUsages, amigoDataBucket)
    Ok(Json.toJson(bakeUsages))
  }

  def deleteConfirm(recipeId: RecipeId, buildNumber: Int): Action[AnyContent] = authAction { implicit request =>
    Bakes.findById(recipeId, buildNumber).fold[Result](NotFound) { bake =>
      val recipeUsage: RecipeUsage = RecipeUsage(Seq(bake))(prism)
      Ok(views.html.confirmBakeDelete(bake.bakeId, recipeUsage.bakeUsage, bakeDeletionFrequencyMinutes))
    }
  }

  def deleteBake(recipeId: RecipeId, buildNumber: Int): Action[AnyContent] = authAction { implicit request =>
    Bakes.findById(recipeId, buildNumber).fold[Result](NotFound) { bake =>
      val recipeUsage: RecipeUsage = RecipeUsage(Seq(bake))(prism)

      if (recipeUsage.bakeUsage.nonEmpty) {
        Conflict(s"Can't delete bake ${bake.bakeId.buildNumber} from recipe ${bake.bakeId.recipeId} as it is still used by ${recipeUsage.bakeUsage.size} resources.")
      } else {
        Bakes.updateStatus(bake.bakeId, DeletionScheduled)
        Bakes.markToDelete(bake.bakeId)
        Redirect(routes.RecipeController.showRecipe(recipeId))
      }

    }
  }

}