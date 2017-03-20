package controllers

import com.gu.googleauth.GoogleAuthConfig
import data._
import models._
import org.quartz.CronExpression
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._
import prism.RecipeUsage
import schedule.BakeScheduler
import services.PrismAgents
import scala.util.Try

class RecipeController(
    bakeScheduler: BakeScheduler,
    prismAgents: PrismAgents,
    val authConfig: GoogleAuthConfig,
    val messagesApi: MessagesApi)(implicit dynamo: Dynamo) extends Controller with AuthActions with I18nSupport {
  import RecipeController._

  def listRecipes = AuthAction {
    val recipes: Iterable[Recipe] = Recipes.list()
    val usages: Map[Recipe, RecipeUsage] = RecipeUsage.forAll(recipes, findBakes = recipeId => Bakes.list(recipeId))(prismAgents)
    Ok(views.html.recipes(recipes, usages))
  }

  def showRecipe(id: RecipeId) = AuthAction { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val bakes = Bakes.list(recipe.id)
      Ok(
        views.html.showRecipe(
          recipe,
          bakes.take(20),
          RecipeUsage(recipe, bakes)(prismAgents)
        )
      )
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

