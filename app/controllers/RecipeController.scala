package controllers

import com.gu.googleauth.AuthAction
import data._
import models._
import org.quartz.CronExpression
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc._
import prism.RecipeUsage
import schedule.BakeScheduler
import services.{Loggable, PrismData}

import scala.util.Try

class RecipeController(
    val authAction: AuthAction[AnyContent],
    bakeScheduler: BakeScheduler,
    prismAgents: PrismData,
    components: ControllerComponents,
    debugAvailable: Boolean
)(implicit dynamo: Dynamo)
    extends AbstractController(components)
    with I18nSupport
    with Loggable {
  import RecipeController._

  def listRecipes = authAction {
    val recipes: Iterable[Recipe] = Recipes.list()
    val usages: Map[Recipe, RecipeUsage] =
      RecipeUsage.getUsagesMap(recipes)(prismAgents, dynamo)
    val (usedRecipes, unusedRecipes) =
      recipes.partition(r => RecipeUsage.hasUsage(r, usages))
    Ok(views.html.recipes(usedRecipes, unusedRecipes, usages))
  }

  def showRecipe(id: RecipeId) = authAction { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val bakes = Bakes.list(recipe.id)
      val recentBakes = bakes.take(20)
      val recentCopies =
        prismAgents.copiedImages(recentBakes.flatMap(_.amiId).toSet)
      Ok(
        views.html.showRecipe(
          recipe,
          recentBakes,
          recentCopies,
          prismAgents.accounts,
          RecipeUsage(bakes)(prismAgents),
          Roles.list,
          debugAvailable,
          Forms.cloneRecipe
        )
      )
    }
  }

  def editRecipe(id: RecipeId) = authAction { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val form = Forms.editRecipe.fill(
        (
          recipe.description,
          recipe.baseImage.id,
          recipe.diskSize,
          recipe.bakeSchedule,
          recipe.encryptFor
        )
      )
      Ok(
        views.html
          .editRecipe(recipe, form, BaseImages.list().toSeq, Roles.listIds)
      )
    }
  }

  def updateRecipe(id: RecipeId) = authAction(parse.formUrlEncoded) {
    implicit request =>
      Recipes.findById(id).fold[Result](NotFound) { recipe =>
        Forms.editRecipe
          .bindFromRequest()
          .fold(
            { formWithErrors =>
              BadRequest(
                views.html.editRecipe(
                  recipe,
                  formWithErrors,
                  BaseImages.list().toSeq,
                  Roles.listIds
                )
              )
            },
            {
              case (
                    description,
                    baseImageId,
                    diskSize,
                    bakeSchedule,
                    encryptFor
                  ) =>
                BaseImages.findById(baseImageId) match {
                  case Some(baseImage) =>
                    log.info(s"Updating recipe ${id} ${recipe.description} - requested by ${request.user.email}")
                    val customisedRoles = controllers.ControllerHelpers
                      .parseEnabledRoles(request.body)
                    customisedRoles.fold(
                      error => BadRequest(s"Problem parsing roles: $error"),
                      roles => {
                        val updatedRecipe = Recipes.update(
                          recipe,
                          description,
                          baseImage,
                          diskSize,
                          roles,
                          modifiedBy = request.user.fullName,
                          bakeSchedule,
                          encryptFor
                        )
                        updatedRecipe.fold(
                          e => InternalServerError(e.toString),
                          { r =>
                            bakeScheduler.reschedule(r)
                            Redirect(routes.RecipeController.showRecipe(id))
                              .flashing("info" -> "Successfully updated recipe")
                          }
                        )
                      }
                    )
                  case None =>
                    val formWithError = Forms.editRecipe
                      .fill(
                        (
                          description,
                          baseImageId,
                          diskSize,
                          bakeSchedule,
                          encryptFor
                        )
                      )
                      .withError("baseImageId", "Unknown base image")
                    BadRequest(
                      views.html.editRecipe(
                        recipe,
                        formWithError,
                        BaseImages.list().toSeq,
                        Roles.listIds
                      )
                    )
                }
            }
          )
      }
  }

  def newRecipe = authAction { implicit request =>
    Ok(
      views.html
        .newRecipe(Forms.createRecipe, BaseImages.list().toSeq, Roles.listIds)
    )
  }

  def createRecipe = authAction(parse.formUrlEncoded) { implicit request =>
    Forms.createRecipe
      .bindFromRequest()
      .fold(
        { formWithErrors =>
          BadRequest(
            views.html
              .newRecipe(formWithErrors, BaseImages.list().toSeq, Roles.listIds)
          )
        },
        {
          case (
                id,
                description,
                baseImageId,
                diskSize,
                bakeSchedule,
                encryptedCopies
              ) =>
            log.info(s"Creating recipe ${id} ${description} - requested by ${request.user.email}")
            Recipes.findById(id) match {
              case Some(existingRecipe) =>
                val formWithError = Forms.createRecipe
                  .fill(
                    (
                      id,
                      description,
                      baseImageId,
                      diskSize,
                      bakeSchedule,
                      encryptedCopies
                    )
                  )
                  .withError("id", "This recipe ID is already in use")
                Conflict(views.html.newBaseImage(formWithError, Roles.listIds))
              case None =>
                BaseImages.findById(baseImageId) match {
                  case Some(baseImage) =>
                    val customisedRoles = controllers.ControllerHelpers
                      .parseEnabledRoles(request.body)
                    customisedRoles.fold(
                      error => BadRequest(s"Problem parsing roles: $error"),
                      roles => {
                        val recipe = Recipes.create(
                          id,
                          description,
                          baseImage,
                          diskSize,
                          roles,
                          createdBy = request.user.fullName,
                          bakeSchedule,
                          encryptedCopies
                        ) // TODO: FIX THIS
                        bakeScheduler.reschedule(recipe)
                        Redirect(routes.RecipeController.showRecipe(id))
                          .flashing("info" -> "Successfully created recipe")
                      }
                    )
                  case None =>
                    val formWithError = Forms.createRecipe
                      .fill(
                        (
                          id,
                          description,
                          baseImageId,
                          diskSize,
                          bakeSchedule,
                          encryptedCopies
                        )
                      )
                      .withError("baseImageId", "Unknown base image")
                    BadRequest(
                      views.html.newRecipe(
                        formWithError,
                        BaseImages.list().toSeq,
                        Roles.listIds
                      )
                    )
                }
            }
        }
      )
  }

  def showUsages(id: RecipeId) = authAction { implicit request =>
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

  def cloneRecipe(id: RecipeId) = authAction { implicit request =>
    Forms.cloneRecipe
      .bindFromRequest()
      .fold(
        { form =>
          Redirect(routes.RecipeController.showRecipe(id)).flashing(
            "info" -> s"Failed to clone recipe: ${form.errors.head.message}"
          )
        },
        { newId =>
          Recipes
            .findById(newId)
            .fold[Result] {
              Recipes.findById(id).fold[Result](NotFound) { recipe =>
                Recipes.create(
                  id = newId,
                  description = recipe.description,
                  baseImage = recipe.baseImage,
                  diskSize = recipe.diskSize,
                  roles = recipe.roles,
                  createdBy = request.user.fullName,
                  bakeSchedule = recipe.bakeSchedule,
                  encryptedCopies = recipe.encryptFor
                )
                Redirect(routes.RecipeController.showRecipe(newId))
                  .flashing("info" -> "Successfully cloned recipe")
              }
            }(_ => Conflict(s"$newId already exists"))
        }
      )
  }

  def deleteConfirm(id: RecipeId) = authAction { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val bakes = Bakes.list(recipe.id).toSeq
      val recipeUsage: RecipeUsage = RecipeUsage(bakes)(prismAgents)
      Ok(views.html.confirmRecipeDelete(recipe, bakes, recipeUsage.bakeUsage))
    }
  }

  def deleteRecipe(id: RecipeId) = authAction { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val bakes = Bakes.list(recipe.id)
      val recipeUsage: RecipeUsage = RecipeUsage(bakes)(prismAgents)
      if (recipeUsage.bakeUsage.nonEmpty) {
        Conflict(
          s"Can't delete recipe $id as it is still used by ${recipeUsage.bakeUsage.size} resources."
        )
      } else {
        log.info(s"Deleting recipe ${id} ${recipe.description} - requested by ${request.user.email}")
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

    private val baseImageIdMapping: Mapping[BaseImageId] =
      nonEmptyText(maxLength = 50)
        .transform[BaseImageId](BaseImageId.apply, _.value)
    private val validQuartzCronExpression = (text: String) =>
      Try(new CronExpression(text)).isSuccess
    private val bakeScheduleMapping = optional(
      text(maxLength = 50)
        .verifying("Invalid Quartz cron expression", validQuartzCronExpression)
        .transform[BakeSchedule](BakeSchedule.apply, _.quartzCronExpression)
    )
    private val accountNumbersMapping = text()
      .verifying(_.forall(c => c.isDigit || c.isWhitespace || c == ','))
      .transform[List[AccountNumber]](
        _.split(',').toList
          .map(_.trim)
          .filter(_.nonEmpty)
          .map(AccountNumber.apply),
        _.map(_.accountNumber).mkString(",")
      )

    val editRecipe = Form(
      tuple(
        "description" -> optional(text(maxLength = 10000)),
        "baseImageId" -> baseImageIdMapping,
        "diskSize" -> optional(number),
        "bakeSchedule" -> bakeScheduleMapping,
        "encryptFor" -> accountNumbersMapping
      )
    )

    val createRecipe = Form(
      tuple(
        "id" -> text(minLength = 3, maxLength = 50)
          .transform[RecipeId](RecipeId.apply, _.value),
        "description" -> optional(text(maxLength = 10000)),
        "baseImageId" -> baseImageIdMapping,
        "diskSize" -> optional(number),
        "bakeSchedule" -> bakeScheduleMapping,
        "encryptFor" -> accountNumbersMapping
      )
    )

    val cloneRecipe = Form(
      "newId" -> text(minLength = 3, maxLength = 50)
        .transform[RecipeId](RecipeId.apply, _.value)
    )

  }

}
