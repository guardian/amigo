package controllers

import com.gu.googleauth.GoogleAuthConfig
import data._
import models._
import org.quartz.CronExpression
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._
import prism.Prism
import schedule.BakeScheduler

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class RecipeController(
    bakeScheduler: BakeScheduler,
    prism: Prism,
    val authConfig: GoogleAuthConfig,
    val messagesApi: MessagesApi)(implicit dynamo: Dynamo) extends Controller with AuthActions with I18nSupport {
  import RecipeController._

  def listRecipes = AuthAction.async { implicit request =>
    val recipes = Recipes.list().toSeq
    val fInstances = prism.findAllInstances()
    val fLaunchConfigurations = prism.findAllLaunchConfigurations()
    for {
      instances <- fInstances
      launchConfigurations <- fLaunchConfigurations
      inUseAmis = instances.map(_.imageId) ++ launchConfigurations.map(_.imageId)
      inUseRecipes = recipes.filter { recipe =>
        Bakes
          .list(recipe.id)
          .exists(_.amiId.exists(amiId => inUseAmis.contains(amiId.value)))
      }
    } yield Ok(views.html.recipes(recipes, inUseRecipes))
  }

  def showRecipe(id: RecipeId) = AuthAction.async { implicit request =>
    Recipes.findById(id).fold[Future[Result]](Future.successful(NotFound)) { recipe =>
      val bakes = Bakes.list(recipe.id)
      val recipeAmiIds = bakes.flatMap(_.amiId.map(_.value)).toList
      val fInstances = prism.findAllInstances()
      val fLaunchConfigurations = prism.findAllLaunchConfigurations()
      for {
        allInstances <- fInstances
        allLaunchConfigurations <- fLaunchConfigurations
        instanceCount = allInstances.count(instance => recipeAmiIds.contains(instance.imageId))
        launchConfigurationCount = allLaunchConfigurations.count(lc => recipeAmiIds.contains(lc.imageId))
      } yield Ok(views.html.showRecipe(recipe, bakes.take(20), instanceCount, launchConfigurationCount))
    }
  }

  def editRecipe(id: RecipeId) = AuthAction {
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val form = Forms.editRecipe.fill((recipe.description, recipe.baseImage.id, recipe.bakeSchedule))
      Ok(views.html.editRecipe(recipe, form, BaseImages.list().toSeq, Roles.listIds))
    }
  }

  def updateRecipe(id: RecipeId) = AuthAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      Forms.editRecipe.bindFromRequest.fold({ formWithErrors =>
        BadRequest(views.html.editRecipe(recipe, formWithErrors, BaseImages.list().toSeq, Roles.listIds))
      }, {
        case (description, baseImageId, bakeSchedule) =>
          BaseImages.findById(baseImageId) match {
            case Some(baseImage) =>
              val customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
              val updatedRecipe = Recipes.update(recipe, description, baseImage, customisedRoles, modifiedBy = request.user.fullName, bakeSchedule)
              bakeScheduler.reschedule(updatedRecipe)
              Redirect(routes.RecipeController.showRecipe(id)).flashing("info" -> "Successfully updated recipe")
            case None =>
              val formWithError = Forms.editRecipe.fill((description, baseImageId, bakeSchedule)).withError("baseImageId", "Unknown base image")
              BadRequest(views.html.editRecipe(recipe, formWithError, BaseImages.list().toSeq, Roles.listIds))
          }
      })
    }
  }

  def newRecipe = AuthAction {
    Ok(views.html.newRecipe(Forms.createRecipe, BaseImages.list().toSeq, Roles.listIds))
  }

  def createRecipe = AuthAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    Forms.createRecipe.bindFromRequest.fold({ formWithErrors =>
      BadRequest(views.html.newRecipe(formWithErrors, BaseImages.list().toSeq, Roles.listIds))
    }, {
      case (id, description, baseImageId, bakeSchedule) =>
        Recipes.findById(id) match {
          case Some(existingRecipe) =>
            val formWithError = Forms.createRecipe.fill((id, description, baseImageId, bakeSchedule)).withError("id", "This recipe ID is already in use")
            Conflict(views.html.newBaseImage(formWithError, Roles.listIds))
          case None =>
            BaseImages.findById(baseImageId) match {
              case Some(baseImage) =>
                val customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
                val recipe = Recipes.create(id, description, baseImage, customisedRoles, createdBy = request.user.fullName, bakeSchedule)
                bakeScheduler.reschedule(recipe)
                Redirect(routes.RecipeController.showRecipe(id)).flashing("info" -> "Successfully created recipe")
              case None =>
                val formWithError = Forms.createRecipe.fill((id, description, baseImageId, bakeSchedule)).withError("baseImageId", "Unknown base image")
                BadRequest(views.html.newRecipe(formWithError, BaseImages.list().toSeq, Roles.listIds))
            }
        }
    })
  }

}

object RecipeController {

  object Forms {

    private val validQuartzCronExpression = (text: String) => Try(new CronExpression(text)).isSuccess
    private val bakeScheduleMapping = optional(
      text(maxLength = 50)
        .verifying("Invalid Quartz cron expression", validQuartzCronExpression)
        .transform[BakeSchedule](BakeSchedule.apply, _.quartzCronExpression)
    )

    val editRecipe = Form(tuple(
      "description" -> optional(text(maxLength = 10000)),
      "baseImageId" -> nonEmptyText(maxLength = 50).transform[BaseImageId](BaseImageId.apply, _.value),
      "bakeSchedule" -> bakeScheduleMapping
    ))

    val createRecipe = Form(tuple(
      "id" -> text(maxLength = 50).transform[RecipeId](RecipeId.apply, _.value),
      "description" -> optional(text(maxLength = 10000)),
      "baseImageId" -> nonEmptyText(maxLength = 50).transform[BaseImageId](BaseImageId.apply, _.value),
      "bakeSchedule" -> bakeScheduleMapping
    ))

  }

}

