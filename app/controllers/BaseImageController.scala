package controllers

import com.gu.googleauth.GoogleAuthConfig

import data._
import models._

import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._

class BaseImageController(
    val authConfig: GoogleAuthConfig,
    val messagesApi: MessagesApi,
    baseImages: BaseImages)(implicit dynamo: Dynamo) extends Controller with AuthActions with I18nSupport {
  import BaseImageController._

  import dynamo.exec

  def listBaseImages = AuthAction {
    Ok(views.html.baseImages(exec(baseImages.list())))
  }

  def showBaseImage(id: BaseImageId) = AuthAction { implicit request =>
    exec(baseImages.findById(id)).fold[Result](NotFound)(image => Ok(views.html.showBaseImage(image)))
  }

  def editBaseImage(id: BaseImageId) = AuthAction {
    exec(baseImages.findById(id)).fold[Result](NotFound) { image =>
      val form = Forms.editBaseImage.fill((image.description, image.amiId))
      Ok(views.html.editBaseImage(image, form, Roles.list))
    }
  }

  def updateBaseImage(id: BaseImageId) = AuthAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    exec(baseImages.findById(id)).fold[Result](NotFound) { image =>
      Forms.editBaseImage.bindFromRequest.fold({ formWithErrors =>
        BadRequest(views.html.editBaseImage(image, formWithErrors, Roles.list))
      }, {
        case (description, amiId) =>
          val customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
          baseImages.update(image, description, amiId, customisedRoles, modifiedBy = request.user.fullName)
          Redirect(routes.BaseImageController.showBaseImage(id)).flashing("info" -> "Successfully updated base image")
      })
    }
  }

  def newBaseImage = AuthAction {
    Ok(views.html.newBaseImage(Forms.createBaseImage, Roles.list))
  }

  def createBaseImage = AuthAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    Forms.createBaseImage.bindFromRequest.fold({ formWithErrors =>
      BadRequest(views.html.newBaseImage(formWithErrors, Roles.list))
    }, {
      case (id, description, amiId) =>
        exec(baseImages.findById(id)) match {
          case Some(existingImage) =>
            val formWithError = Forms.createBaseImage.fill((id, description, amiId)).withError("id", "This base image ID is already in use")
            Conflict(views.html.newBaseImage(formWithError, Roles.list))
          case None =>
            val customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
            baseImages.create(id, description, amiId, customisedRoles, createdBy = request.user.fullName)
            Redirect(routes.BaseImageController.showBaseImage(id)).flashing("info" -> "Successfully created base image")
        }
    })
  }

}

object BaseImageController {

  object Forms {

    val editBaseImage = Form(tuple(
      "description" -> text(maxLength = 10000),
      "amiId" -> nonEmptyText(maxLength = 16).transform[AmiId](AmiId.apply, _.value)
    ))

    val createBaseImage = Form(tuple(
      "id" -> text(maxLength = 50).transform[BaseImageId](BaseImageId.apply, _.value),
      "description" -> text(maxLength = 10000),
      "amiId" -> nonEmptyText(maxLength = 16).transform[AmiId](AmiId.apply, _.value)
    ))

  }
}

