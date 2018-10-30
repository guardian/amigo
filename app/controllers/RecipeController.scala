package controllers

import com.gu.googleauth.GoogleAuthConfig
import data._
import models._
import org.quartz.CronExpression
import play.api.data.{ Form, Mapping }
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
    val messagesApi: MessagesApi,
    debugAvailable: Boolean)(implicit dynamo: Dynamo) extends Controller with AuthActions with I18nSupport {
  import RecipeController._

  def listRecipes = AuthAction {
    val recipes: Iterable[Recipe] = Recipes.list()
    val usages: Map[Recipe, RecipeUsage] = RecipeUsage.forAll(recipes, findBakes = recipeId => Bakes.list(recipeId))(prismAgents)
    Ok(views.html.recipes(recipes, usages))
  }

  def showRecipe(id: RecipeId) = AuthAction { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val bakes = Bakes.list(recipe.id)
      val recentBakes = bakes.take(20)
      val recentCopies = prismAgents.copiedImages(recentBakes.flatMap(_.amiId).toSet)
      Ok(
        views.html.showRecipe(
          recipe,
          recentBakes,
          recentCopies,
          prismAgents.accounts,
          RecipeUsage(bakes)(prismAgents),
          Roles.list,
          debugAvailable
        )
      )
    }
  }

  def editRecipe(id: RecipeId) = AuthAction {
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val form = Forms.editRecipe.fill((recipe.description, recipe.baseImage.id, recipe.diskSize, recipe.bakeSchedule, recipe.encryptFor))
      Ok(views.html.editRecipe(recipe, form, BaseImages.list().toSeq, Roles.listIds))
    }
  }

  def updateRecipe(id: RecipeId) = AuthAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      Forms.editRecipe.bindFromRequest.fold({ formWithErrors =>
        BadRequest(views.html.editRecipe(recipe, formWithErrors, BaseImages.list().toSeq, Roles.listIds))
      }, {
        case (description, baseImageId, diskSize, bakeSchedule, encryptFor) =>
          BaseImages.findById(baseImageId) match {
            case Some(baseImage) =>
              val customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
              customisedRoles.fold(
                error => BadRequest(s"Problem parsing roles: $error"),
                roles => {
                  val updatedRecipe = Recipes.update(
                    recipe, description, baseImage, diskSize, roles, modifiedBy = request.user.fullName, bakeSchedule, encryptFor)
                  updatedRecipe.fold(e => InternalServerError(e.toString), { r =>
                    bakeScheduler.reschedule(r)
                    Redirect(routes.RecipeController.showRecipe(id)).flashing("info" -> "Successfully updated recipe")
                  })
                }
              )
            case None =>
              val formWithError = Forms.editRecipe.fill((description, baseImageId, diskSize, bakeSchedule, encryptFor)).withError("baseImageId", "Unknown base image")
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
      case (id, description, baseImageId, diskSize, bakeSchedule, encryptedCopies) =>
        Recipes.findById(id) match {
          case Some(existingRecipe) =>
            val formWithError = Forms.createRecipe.fill((id, description, baseImageId, diskSize, bakeSchedule, encryptedCopies)).withError("id", "This recipe ID is already in use")
            Conflict(views.html.newBaseImage(formWithError, Roles.listIds))
          case None =>
            BaseImages.findById(baseImageId) match {
              case Some(baseImage) =>
                val customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
                customisedRoles.fold(
                  error => BadRequest(s"Problem parsing roles: $error"),
                  roles => {
                    val recipe = Recipes.create(id, description, baseImage, diskSize, roles, createdBy = request.user.fullName, bakeSchedule, encryptedCopies) //TODO: FIX THIS
                    bakeScheduler.reschedule(recipe)
                    Redirect(routes.RecipeController.showRecipe(id)).flashing("info" -> "Successfully created recipe")
                  }
                )
              case None =>
                val formWithError = Forms.createRecipe.fill((id, description, baseImageId, diskSize, bakeSchedule, encryptedCopies)).withError("baseImageId", "Unknown base image")
                BadRequest(views.html.newRecipe(formWithError, BaseImages.list().toSeq, Roles.listIds))
            }
        }
    })
  }

  def showUsages(id: RecipeId) = AuthAction { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val bakes = Bakes.list(recipe.id)
      val recipeUsage: RecipeUsage = RecipeUsage(bakes)(prismAgents)
      Ok(
        views.html.showUsage(
          recipe,
          recipeUsage.bakeUsage,
          prismAgents.accounts,
          prismAgents.baseUrl
        )
      )
    }
  }

  def deleteConfirm(id: RecipeId) = AuthAction { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val bakes = Bakes.list(recipe.id).toSeq
      val recipeUsage: RecipeUsage = RecipeUsage(bakes)(prismAgents)
      Ok(views.html.confirmDelete(recipe, bakes, recipeUsage.bakeUsage))
    }
  }

  def deleteRecipe(id: RecipeId) = AuthAction { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val bakes = Bakes.list(recipe.id)
      val recipeUsage: RecipeUsage = RecipeUsage(bakes)(prismAgents)
      if (recipeUsage.bakeUsage.nonEmpty) {
        Conflict(s"Can't delete recipe $id as it is still used by ${recipeUsage.bakeUsage.size} resources.")
      } else {
        // stop any scheduled build
        bakeScheduler.reschedule(recipe.copy(bakeSchedule = None))

        // delete the AMIgo data
        bakes.foreach { bake =>
          Bakes.markToDelete(bake.bakeId)
        }
        Recipes.delete(recipe)
        // redirect back to the index page
        Redirect(routes.RecipeController.listRecipes())
      }
    }
  }

}

object RecipeController {

  object Forms {

    private val baseImageIdMapping: Mapping[BaseImageId] = nonEmptyText(maxLength = 50).transform[BaseImageId](BaseImageId.apply, _.value)
    private val validQuartzCronExpression = (text: String) => Try(new CronExpression(text)).isSuccess
    private val bakeScheduleMapping = optional(
      text(maxLength = 50)
        .verifying("Invalid Quartz cron expression", validQuartzCronExpression)
        .transform[BakeSchedule](BakeSchedule.apply, _.quartzCronExpression)
    )
    private val accountNumbersMapping = text()
      .verifying(_.forall(c => c.isDigit || c.isWhitespace || c == ','))
      .transform[List[AccountNumber]](
        _.split(',').toList.map(_.trim).filter(_.nonEmpty).map(AccountNumber.apply),
        _.map(_.accountNumber).mkString(",")
      )

    val editRecipe = Form(tuple(
      "description" -> optional(text(maxLength = 10000)),
      "baseImageId" -> baseImageIdMapping,
      "diskSize" -> optional(number),
      "bakeSchedule" -> bakeScheduleMapping,
      "encryptFor" -> accountNumbersMapping
    ))

    val createRecipe = Form(tuple(
      "id" -> text(maxLength = 50).transform[RecipeId](RecipeId.apply, _.value),
      "description" -> optional(text(maxLength = 10000)),
      "baseImageId" -> baseImageIdMapping,
      "diskSize" -> optional(number),
      "bakeSchedule" -> bakeScheduleMapping,
      "encryptFor" -> accountNumbersMapping
    ))

  }

}

