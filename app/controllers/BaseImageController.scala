package controllers

import com.gu.googleauth.AuthAction
import controllers.ControllerHelpers.parseEnabledRoles
import data._
import models._
import org.joda.time.DateTime
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc._
import prism.RecipeUsage
import services.PrismData

class BaseImageController(
    val authAction: AuthAction[AnyContent],
    prismAgents: PrismData,
    components: ControllerComponents
)(implicit dynamo: Dynamo)
    extends AbstractController(components)
    with I18nSupport {
  import BaseImageController._

  def listBaseImages = authAction {
    val baseImages = BaseImages.list()
    val usageMap =
      baseImages.map(base => (base, Recipes.findByBaseImage(base.id))).toMap
    Ok(views.html.baseImages(usageMap))
  }

  def showBaseImage(id: BaseImageId) = authAction { implicit request =>
    BaseImages.findById(id).fold[Result](NotFound) { image =>
      val usedByRecipes = Recipes.findByBaseImage(id)
      val usages: Map[Recipe, RecipeUsage] =
        RecipeUsage.getUsagesMap(usedByRecipes)(prismAgents, dynamo)
      val (usedRecipes, unusedRecipes) =
        usedByRecipes.partition(r => RecipeUsage.hasUsage(r, usages))
      Ok(
        views.html.showBaseImage(
          image,
          Roles.list,
          usedRecipes.toSeq,
          unusedRecipes.toSeq,
          Forms.cloneBaseImage,
          usages
        )
      )
    }
  }

  def editBaseImage(id: BaseImageId) = authAction { implicit request =>
    BaseImages.findById(id).fold[Result](NotFound) { image =>
      val form = Forms.editBaseImage.fill(
        (
          image.description,
          image.amiId,
          image.linuxDist.getOrElse(Ubuntu),
          image.eolDate.getOrElse(DateTime.now).toLocalDate.toDate,
          image.requiresXLargeBuilder
        )
      )
      Ok(views.html.editBaseImage(image, form, Roles.listIds))
    }
  }

  def updateBaseImage(id: BaseImageId) = authAction(parse.formUrlEncoded) {
    implicit request =>
      BaseImages.findById(id).fold[Result](NotFound) { image =>
        Forms.editBaseImage
          .bindFromRequest()
          .fold(
            { formWithErrors =>
              BadRequest(
                views.html.editBaseImage(image, formWithErrors, Roles.listIds)
              )
            },
            {
              case (
                    description,
                    amiId,
                    linuxDist,
                    eolDate,
                    requiresXLargeBuilder
                  ) =>
                val customisedRoles = parseEnabledRoles(request.body)
                customisedRoles.fold(
                  error => BadRequest(s"Problem parsing roles: $error"),
                  roles => {
                    BaseImages.update(
                      image,
                      description,
                      amiId,
                      linuxDist,
                      roles,
                      modifiedBy = request.user.fullName,
                      new DateTime(eolDate),
                      requiresXLargeBuilder
                    )
                    Redirect(routes.BaseImageController.showBaseImage(id))
                      .flashing("info" -> "Successfully updated base image")
                  }
                )
            }
          )
      }
  }

  def newBaseImage = authAction { implicit request =>
    Ok(views.html.newBaseImage(Forms.createBaseImage, Roles.listIds))
  }

  def createBaseImage = authAction(parse.formUrlEncoded) { implicit request =>
    Forms.createBaseImage
      .bindFromRequest()
      .fold(
        { formWithErrors =>
          BadRequest(views.html.newBaseImage(formWithErrors, Roles.listIds))
        },
        {
          case (
                id,
                description,
                amiId,
                linuxDist,
                eolDate,
                requiresXLargeBuilder
              ) =>
            BaseImages.findById(id) match {
              case Some(existingImage) =>
                val formWithError = Forms.createBaseImage
                  .fill(
                    (
                      id,
                      description,
                      amiId,
                      linuxDist,
                      eolDate,
                      requiresXLargeBuilder
                    )
                  )
                  .withError("id", "This base image ID is already in use")
                Conflict(views.html.newBaseImage(formWithError, Roles.listIds))
              case None =>
                val customisedRoles = parseEnabledRoles(request.body)
                customisedRoles.fold(
                  error => BadRequest(s"Problem parsing roles: $error"),
                  roles => {
                    BaseImages.create(
                      id,
                      description,
                      amiId,
                      roles,
                      createdBy = request.user.fullName,
                      linuxDist,
                      Some(new DateTime(eolDate)),
                      requiresXLargeBuilder
                    )
                    Redirect(routes.BaseImageController.showBaseImage(id))
                      .flashing("info" -> "Successfully created base image")
                  }
                )
            }
        }
      )
  }

  def cloneBaseImage(id: BaseImageId) = authAction { implicit request =>
    Forms.cloneBaseImage
      .bindFromRequest()
      .fold(
        { form =>
          Redirect(routes.BaseImageController.showBaseImage(id)).flashing(
            "error" -> s"Failed to clone base image: ${form.errors.head.message}"
          )
        },
        { newId =>
          BaseImages
            .findById(newId)
            .fold[Result] {
              BaseImages.findById(id).fold[Result](NotFound) { baseImage =>
                baseImage.linuxDist match {
                  case Some(linuxDist) =>
                    BaseImages.create(
                      id = newId,
                      description = baseImage.description,
                      amiId = baseImage.amiId,
                      builtinRoles = baseImage.builtinRoles,
                      createdBy = request.user.fullName,
                      linuxDist = linuxDist,
                      eolDate = baseImage.eolDate,
                      requiresXLargeBuilder = baseImage.requiresXLargeBuilder
                    )
                    Redirect(routes.BaseImageController.showBaseImage(newId))
                      .flashing("info" -> "Successfully cloned base image")
                  case None =>
                    Redirect(routes.BaseImageController.showBaseImage(id))
                      .flashing(
                        "error" -> "Failed to clone base image as it does not have a linux distribution set"
                      )
                }
              }
            }(_ => Conflict(s"$newId already exists"))
        }
      )
  }

  def deleteConfirm(id: BaseImageId) = authAction { implicit request =>
    BaseImages.findById(id).fold[Result](NotFound) { recipe =>
      val usedByRecipes = Recipes.findByBaseImage(id).toSeq
      Ok(views.html.confirmBaseImageDelete(recipe, usedByRecipes))
    }
  }

  def deleteBaseImage(id: BaseImageId) = authAction { implicit request =>
    BaseImages.findById(id).fold[Result](NotFound) { image =>
      val usedByRecipes = Recipes.findByBaseImage(id)
      if (usedByRecipes.isEmpty) {
        BaseImages.delete(image)
        Redirect(routes.BaseImageController.listBaseImages())
          .flashing("info" -> s"Successfully deleted base image ${id.value}")
      } else {
        Redirect(routes.BaseImageController.showBaseImage(id))
          .flashing("error" -> s"Failed to delete base image as it is in use")
      }
    }
  }

}

object BaseImageController {

  object Forms {

    val amiId =
      nonEmptyText(maxLength = 21).transform[AmiId](AmiId.apply, _.value)

    val linuxDist: Mapping[LinuxDist] =
      nonEmptyText(maxLength = 16)
        .verifying(
          s"Must be one of ${LinuxDist.all.keys}",
          LinuxDist.create(_).nonEmpty
        )
        .transform(LinuxDist.create(_).get, _.name)

    val editBaseImage = Form(
      tuple(
        "description" -> text(maxLength = 10000),
        "amiId" -> amiId,
        "linuxDist" -> linuxDist,
        "eolDate" -> date("yyyy-MM-dd"),
        "requiresXLargeBuilder" -> boolean
      )
    )

    val createBaseImage = Form(
      tuple(
        "id" -> text(minLength = 3, maxLength = 50)
          .transform[BaseImageId](BaseImageId.apply, _.value),
        "description" -> text(maxLength = 10000),
        "amiId" -> amiId,
        "linuxDist" -> linuxDist,
        "eolDate" -> date("yyyy-MM-dd"),
        "requiresXLargeBuilder" -> boolean
      )
    )

    val cloneBaseImage = Form(
      "newId" -> text(minLength = 3, maxLength = 50)
        .transform[BaseImageId](BaseImageId.apply, _.value)
    )
  }
}
