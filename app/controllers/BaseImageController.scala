package controllers

import com.gu.googleauth.GoogleAuthConfig
import data._
import models._
import play.api.data.{ Form, Mapping }
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._

class BaseImageController(
    val authConfig: GoogleAuthConfig,
    val messagesApi: MessagesApi)(implicit dynamo: Dynamo) extends Controller with AuthActions with I18nSupport {
  import BaseImageController._

  def listBaseImages = AuthAction {
    Ok(views.html.baseImages(BaseImages.list()))
  }

  def showBaseImage(id: BaseImageId) = AuthAction { implicit request =>
    BaseImages.findById(id).fold[Result](NotFound) { image =>
      val usedByRecipes = Recipes.findByBaseImage(id)
      Ok(views.html.showBaseImage(image, Roles.list, usedByRecipes.toSeq))
    }
  }

  def editBaseImage(id: BaseImageId) = AuthAction {
    BaseImages.findById(id).fold[Result](NotFound) { image =>
      val form = Forms.editBaseImage.fill((
        image.description,
        image.amiId,
        image.linuxDist.getOrElse(Ubuntu),
        image.bakeInstance.getOrElse(BakeInstances.x86)
      ))
      Ok(views.html.editBaseImage(image, form, Roles.listIds))
    }
  }

  def updateBaseImage(id: BaseImageId) = AuthAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    BaseImages.findById(id).fold[Result](NotFound) { image =>
      Forms.editBaseImage.bindFromRequest.fold({ formWithErrors =>
        BadRequest(views.html.editBaseImage(image, formWithErrors, Roles.listIds))
      }, {
        case (description, amiId, linuxDist, bakeInstance) =>
          val customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
          customisedRoles.fold(
            error => BadRequest(s"Problem parsing roles: $error"),
            roles => {
              BaseImages.update(image, description, amiId, linuxDist, roles, modifiedBy = request.user.fullName, bakeInstance)
              Redirect(routes.BaseImageController.showBaseImage(id)).flashing("info" -> "Successfully updated base image")
            }
          )
      })
    }
  }

  def newBaseImage = AuthAction {
    Ok(views.html.newBaseImage(Forms.createBaseImage, Roles.listIds))
  }

  def createBaseImage = AuthAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    Forms.createBaseImage.bindFromRequest.fold({ formWithErrors =>
      BadRequest(views.html.newBaseImage(formWithErrors, Roles.listIds))
    }, {
      case (id, description, amiId, linuxDist, bakeInstance) =>
        BaseImages.findById(id) match {
          case Some(existingImage) =>
            val formWithError = Forms.createBaseImage.fill((id, description, amiId, linuxDist, bakeInstance)).withError("id", "This base image ID is already in use")
            Conflict(views.html.newBaseImage(formWithError, Roles.listIds))
          case None =>
            val customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
            customisedRoles.fold(
              error => BadRequest(s"Problem parsing roles: $error"),
              roles => {
                BaseImages.create(id, description, amiId, roles, createdBy = request.user.fullName, linuxDist, bakeInstance)
                Redirect(routes.BaseImageController.showBaseImage(id)).flashing("info" -> "Successfully created base image")
              }
            )
        }
    })
  }

}

object BaseImageController {

  object Forms {

    val amiId = nonEmptyText(maxLength = 21).transform[AmiId](AmiId.apply, _.value)

    val linuxDist: Mapping[LinuxDist] =
      nonEmptyText(maxLength = 16)
        .verifying(s"Must be one of ${LinuxDist.all.keys}", LinuxDist.create(_).nonEmpty)
        .transform(LinuxDist.create(_).get, _.name)

    val bakeInstance: Mapping[BakeInstance] =
      nonEmptyText(maxLength = 16)
        .verifying(s"Must be one of ${BakeInstances.all}", a => BakeInstances.bakeInstanceForArchitecture(a).nonEmpty)
        .transform(BakeInstances.bakeInstanceForArchitecture(_).get, _.architecture)

    val editBaseImage = Form(tuple(
      "description" -> text(maxLength = 10000),
      "amiId" -> amiId,
      "linuxDist" -> linuxDist,
      "bakeInstance" -> bakeInstance
    ))

    val createBaseImage = Form(tuple(
      "id" -> text(maxLength = 50).transform[BaseImageId](BaseImageId.apply, _.value),
      "description" -> text(maxLength = 10000),
      "amiId" -> amiId,
      "linuxDist" -> linuxDist,
      "bakeInstance" -> bakeInstance
    ))
  }
}

