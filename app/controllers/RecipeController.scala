package controllers

import cats.data.{ OptionT, XorT }
import com.gu.googleauth.GoogleAuthConfig
import com.gu.scanamo.ops._
import data._
import models._
import org.quartz.CronExpression
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._
import schedule.BakeScheduler

import scala.util.Try

class RecipeController(
    bakeScheduler: BakeScheduler,
    val authConfig: GoogleAuthConfig,
    val messagesApi: MessagesApi,
    recipes: Recipes,
    bakes: Bakes,
    baseImages: BaseImages)(implicit val dynamo: Dynamo) extends Controller with OpActions with I18nSupport {
  import RecipeController._

  def listRecipes = AuthOpAction {
    recipes.list().map(rs => Ok(views.html.recipes(rs)))
  }

  def showRecipe(id: RecipeId) = AuthOpAction { implicit request =>
    (for {
      recipe <- OptionT(recipes.findById(id))
      recentBakes <- OptionT.liftF(bakes.list(id, limit = 20))
    } yield Ok(views.html.showRecipe(recipe, recentBakes))
    ).getOrElse(
      NotFound
    )
  }

  def editRecipe(id: RecipeId) = AuthOpAction {
    (for {
      recipe <- OptionT(recipes.findById(id))
      images <- OptionT.liftF(baseImages.list())
    } yield {
      val form = Forms.editRecipe.fill((recipe.description, recipe.baseImage.id, recipe.bakeSchedule))
      Ok(views.html.editRecipe(recipe, form, images.toSeq, Roles.list))
    }).getOrElse(NotFound)
  }

  def updateRecipe(id: RecipeId) = AuthOpAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    (for {
      recipe <- OptionT(recipes.findById(id)).toRight(NotFound)
      images <- xorTright(baseImages.list)
      formValues <- XorT.fromXor[ScanamoOps](Forms.editRecipe.bindFromRequest.toXor).leftMap(formWithErrors =>
        BadRequest(views.html.editRecipe(recipe, formWithErrors, images.toSeq, Roles.list)))
      (description, baseImageId, bakeSchedule) = formValues
      baseImage <- OptionT(baseImages.findById(baseImageId)).toRight {
        val formWithError = Forms.editRecipe.fill(formValues)
          .withError("baseImageId", "Unknown base image")
        BadRequest(views.html.editRecipe(recipe, formWithError, images.toSeq, Roles.list))
      }
      customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
      updatedRecipe <- xorTright(
        recipes.update(recipe, description, baseImage, customisedRoles, modifiedBy = request.user.fullName, bakeSchedule))
    } yield {
      bakeScheduler.reschedule(updatedRecipe)
      Redirect(routes.RecipeController.showRecipe(id)).flashing("info" -> "Successfully updated recipe")
    }).merge
  }

  def newRecipe = AuthOpAction {
    baseImages.list().map(images =>
      Ok(views.html.newRecipe(Forms.createRecipe, images.toSeq, Roles.list)))
  }

  def createRecipe = AuthOpAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    (for {
      images <- xorTright(baseImages.list)
      formValues <- XorT.fromXor[ScanamoOps](Forms.createRecipe.bindFromRequest.toXor).leftMap(formWithErrors =>
        BadRequest(views.html.newRecipe(formWithErrors, images.toSeq, Roles.list)))
      (id, description, baseImageId, bakeSchedule) = formValues
      _ <- OptionT(recipes.findById(id)).toLeft(()).leftMap { _ =>
        val formWithError = Forms.createRecipe.fill(formValues).withError("id", "This recipe ID is already in use")
        Conflict(views.html.newBaseImage(formWithError, Roles.list))
      }
      baseImage <- OptionT(baseImages.findById(baseImageId)).toRight {
        val formWithError = Forms.createRecipe.fill(formValues).withError("baseImageId", "Unknown base image")
        BadRequest(views.html.newRecipe(formWithError, images.toSeq, Roles.list))
      }
      customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
      recipe <- xorTright(
        recipes.create(id, description, baseImage, customisedRoles, createdBy = request.user.fullName, bakeSchedule))
    } yield {
      bakeScheduler.reschedule(recipe)
      Redirect(routes.RecipeController.showRecipe(id)).flashing("info" -> "Successfully created recipe")
    }).merge
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
      "description" -> text(maxLength = 10000),
      "baseImageId" -> nonEmptyText(maxLength = 50).transform[BaseImageId](BaseImageId.apply, _.value),
      "bakeSchedule" -> bakeScheduleMapping
    ))

    val createRecipe = Form(tuple(
      "id" -> text(maxLength = 50).transform[RecipeId](RecipeId.apply, _.value),
      "description" -> text(maxLength = 10000),
      "baseImageId" -> nonEmptyText(maxLength = 50).transform[BaseImageId](BaseImageId.apply, _.value),
      "bakeSchedule" -> bakeScheduleMapping
    ))

  }

}

