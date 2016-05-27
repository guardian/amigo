package controllers

import cats.data.{ OptionT, Xor, XorT }
import com.gu.googleauth.GoogleAuthConfig
import com.gu.scanamo.ops.ScanamoOps
import data._
import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc._

class BaseImageController(
    val authConfig: GoogleAuthConfig,
    val messagesApi: MessagesApi,
    baseImages: BaseImages)(implicit val dynamo: Dynamo) extends Controller with OpActions with I18nSupport {
  import BaseImageController._

  def listBaseImages = AuthOpAction {
    baseImages.list().map(images => Ok(views.html.baseImages(images)))
  }

  def showBaseImage(id: BaseImageId) = AuthOpAction { implicit request =>
    (for {
      image <- OptionT[ScanamoOps, BaseImage](baseImages.findById(id))
    } yield Ok(views.html.showBaseImage(image))
    ).getOrElse(
      NotFound
    )
  }

  def editBaseImage(id: BaseImageId) = AuthOpAction {
    (for {
      image <- OptionT[ScanamoOps, BaseImage](baseImages.findById(id))
    } yield {
      val form = Forms.editBaseImage.fill((image.description, image.amiId))
      Ok(views.html.editBaseImage(image, form, Roles.list))
    }).getOrElse(
      NotFound
    )
  }

  def updateBaseImage(id: BaseImageId) = AuthOpAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    (for {
      image <- OptionT[ScanamoOps, BaseImage](baseImages.findById(id)).toRight(NotFound)
      descriptionAndId <- XorT.fromXor[ScanamoOps](
        Forms.editBaseImage.bindFromRequest.toXor.leftMap(formWithErrors =>
          BadRequest(views.html.editBaseImage(image, formWithErrors, Roles.list))))
      (description, amiId) = descriptionAndId
      customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
      _ <- XorT.right[ScanamoOps, Result, Unit](
        baseImages.update(image, description, amiId, customisedRoles, modifiedBy = request.user.fullName))
    } yield {
      Redirect(routes.BaseImageController.showBaseImage(id)).flashing("info" -> "Successfully updated base image")
    }).merge
  }

  def newBaseImage = AuthAction {
    Ok(views.html.newBaseImage(Forms.createBaseImage, Roles.list))
  }

  def createBaseImage = AuthOpAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    (for {
      idDescAmiId <- XorT.fromXor[ScanamoOps](
        Forms.createBaseImage.bindFromRequest.toXor.leftMap(formWithErrors =>
          BadRequest(views.html.newBaseImage(formWithErrors, Roles.list))))
      (id, description, amiId) = idDescAmiId

      _ <- OptionT[ScanamoOps, BaseImage](baseImages.findById(id)).toLeft(()).leftMap { _ =>
        val formWithError = Forms.createBaseImage.fill((id, description, amiId))
          .withError("id", "This base image ID is already in use")
        Conflict(views.html.newBaseImage(formWithError, Roles.list))
      }

      _ <- XorT.right[ScanamoOps, Result, BaseImage] {
        val customisedRoles = ControllerHelpers.parseEnabledRoles(request.body)
        baseImages.create(id, description, amiId, customisedRoles, createdBy = request.user.fullName)
      }
    } yield {
      Redirect(routes.BaseImageController.showBaseImage(id)).flashing("info" -> "Successfully created base image")
    }).merge
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

