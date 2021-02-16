package controllers

import akka.stream.scaladsl.Source
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3Client }
import com.gu.googleauth.GoogleAuthConfig
import data._
import event._
import packer._
import models._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.EventSource
import play.api.mvc._
import services.{ AmiMetadataLookup, Loggable, PrismAgents }
import services.{ Loggable, PrismAgents }
import play.api.libs.json._
import prism.{ RecipeUsage, SimpleBakeUsage }

class BakeController(
  stage: String,
  eventsSource: Source[BakeEvent, _],
  prism: PrismAgents,
  val authConfig: GoogleAuthConfig,
  val messagesApi: MessagesApi,
  ansibleVars: Map[String, String],
  debugAvailable: Boolean,
  amiMetadataLookup: AmiMetadataLookup,
  amigoDataBucket: Option[String],
  s3Client: AmazonS3,
  packerRunner: PackerRunner)(implicit dynamo: Dynamo, packerConfig: PackerConfig, eventBus: EventBus)
    extends Controller with AuthActions with I18nSupport with Loggable {

  def startBaking(recipeId: RecipeId, debug: Boolean) = AuthAction { request =>
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

  def showBake(recipeId: RecipeId, buildNumber: Int): Action[AnyContent] = AuthAction {
    val previousBakeId = Bakes.findPreviousSuccessfulBake(recipeId, buildNumber - 1).map(_.bakeId)
    Bakes.findById(recipeId, buildNumber).fold[Result](NotFound) { bake: Bake =>
      val bakeLogs = BakeLogs.list(BakeId(recipeId, buildNumber))
      val packageList = PackageList.getPackageList(s3Client, BakeId(recipeId, buildNumber), amigoDataBucket)
      val packageListDiff = packageList.right.flatMap(p => PackageList.getPackageListDiff(s3Client, p, previousBakeId, amigoDataBucket))
      Ok(views.html.showBake(bake, bakeLogs, packageList, packageListDiff))
    }
  }

  def bakePackages(recipeId: RecipeId, buildNumber: Int): Action[AnyContent] = AuthAction {
    val list = PackageList.getPackageList(s3Client, BakeId(recipeId, buildNumber), amigoDataBucket)
    if (list.isLeft) {
      NotFound(s"Could not find package list for recipe $recipeId, bake $buildNumber: ${list.left.get}")
    } else {
      Ok(Json.obj("packages" -> Json.toJson(list.right.get)))
    }
  }

  def bakeEvents(recipeId: RecipeId, buildNumber: Int) = AuthAction { implicit req =>
    val bakeId = BakeId(recipeId, buildNumber)
    val source = eventsSource
      .filter(_.bakeId == bakeId) // only include events relevant to this bake
      .via(EventSource.flow)
    Ok.chunked(source).as("text/event-stream")
  }

  def allBakeUsages: Action[AnyContent] = AuthAction {

    val allUsages = RecipeUsage.getUsages(Recipes.list())(prism, dynamo)

    val bakeUsages = SimpleBakeUsage.fromRecipeUsages(allUsages, amigoDataBucket)
    Ok(Json.toJson(bakeUsages))
  }

}