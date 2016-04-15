package controllers

import com.gu.googleauth.GoogleAuthConfig
import packer.PackerRunner
import models._
import data._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.libs.EventSource
import play.api.libs.iteratee.{ Enumerator, Concurrent }
import event._

import play.api.mvc._

class Amigo(eventsOut: Enumerator[BakeEvent], eventBus: EventBus, val authConfig: GoogleAuthConfig, val messagesApi: MessagesApi)(implicit dynamo: Dynamo) extends Controller with AuthActions with I18nSupport {
  import Amigo._

  def healthcheck = Action {
    Ok("OK")
  }

  def index = AuthAction {
    Ok(views.html.index())
  }

  def baseImages = AuthAction {
    Ok(views.html.baseImages(BaseImages.list()))
  }

  def showBaseImage(id: BaseImageId) = AuthAction { implicit request =>
    BaseImages.findById(id).fold[Result](NotFound)(image => Ok(views.html.showBaseImage(image)))
  }

  def editBaseImage(id: BaseImageId) = AuthAction {
    BaseImages.findById(id).fold[Result](NotFound) { image =>
      val form = Forms.editBaseImage.fill((image.description, image.amiId))
      Ok(views.html.editBaseImage(image, form, Roles.list))
    }
  }

  def updateBaseImage(id: BaseImageId) = AuthAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    BaseImages.findById(id).fold[Result](NotFound) { image =>
      Forms.editBaseImage.bindFromRequest.fold({ formWithErrors =>
        BadRequest(views.html.editBaseImage(image, formWithErrors, Roles.list))
      }, {
        case (description, amiId) =>
          val customisedRoles = Amigo.parseEnabledRoles(request.body)
          BaseImages.update(image, description, amiId, customisedRoles, modifiedBy = request.user.fullName)
          Redirect(routes.Amigo.showBaseImage(id)).flashing("info" -> "Successfully updated base image")
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
        BaseImages.findById(id) match {
          case Some(existingImage) =>
            val formWithError = Forms.createBaseImage.fill((id, description, amiId)).withError("id", "This base image ID is already in use")
            Conflict(views.html.newBaseImage(formWithError, Roles.list))
          case None =>
            val customisedRoles = Amigo.parseEnabledRoles(request.body)
            BaseImages.create(id, description, amiId, customisedRoles, createdBy = request.user.fullName)
            Redirect(routes.Amigo.showBaseImage(id)).flashing("info" -> "Successfully created base image")
        }
    })
  }

  def roles = AuthAction {
    Ok(views.html.roles(Roles.list))
  }

  def recipes = AuthAction {
    Ok(views.html.recipes(Recipes.list()))
  }

  def showRecipe(id: RecipeId) = AuthAction { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val recentBakes = Bakes.list(id, limit = 20)
      Ok(views.html.showRecipe(recipe, recentBakes))
    }
  }

  def editRecipe(id: RecipeId) = AuthAction {
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      val form = Forms.editRecipe.fill((recipe.description, recipe.baseImage.id))
      Ok(views.html.editRecipe(recipe, form, BaseImages.list().toSeq, Roles.list))
    }
  }

  def updateRecipe(id: RecipeId) = AuthAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    Recipes.findById(id).fold[Result](NotFound) { recipe =>
      Forms.editRecipe.bindFromRequest.fold({ formWithErrors =>
        BadRequest(views.html.editRecipe(recipe, formWithErrors, BaseImages.list().toSeq, Roles.list))
      }, {
        case (description, baseImageId) =>
          BaseImages.findById(baseImageId) match {
            case Some(baseImage) =>
              val customisedRoles = Amigo.parseEnabledRoles(request.body)
              Recipes.update(recipe, description, baseImage, customisedRoles, modifiedBy = request.user.fullName)
              Redirect(routes.Amigo.showRecipe(id)).flashing("info" -> "Successfully updated recipe")
            case None =>
              val formWithError = Forms.editRecipe.fill((description, baseImageId)).withError("baseImageId", "Unknown base image")
              BadRequest(views.html.editRecipe(recipe, formWithError, BaseImages.list().toSeq, Roles.list))
          }
      })
    }
  }

  def newRecipe = AuthAction {
    Ok(views.html.newRecipe(Forms.createRecipe, BaseImages.list().toSeq, Roles.list))
  }

  def createRecipe = AuthAction(BodyParsers.parse.urlFormEncoded) { implicit request =>
    Forms.createRecipe.bindFromRequest.fold({ formWithErrors =>
      BadRequest(views.html.newRecipe(formWithErrors, BaseImages.list().toSeq, Roles.list))
    }, {
      case (id, description, baseImageId) =>
        Recipes.findById(id) match {
          case Some(existingRecipe) =>
            val formWithError = Forms.createRecipe.fill((id, description, baseImageId)).withError("id", "This recipe ID is already in use")
            Conflict(views.html.newBaseImage(formWithError, Roles.list))
          case None =>
            BaseImages.findById(baseImageId) match {
              case Some(baseImage) =>
                val customisedRoles = Amigo.parseEnabledRoles(request.body)
                Recipes.create(id, description, baseImage, customisedRoles, createdBy = request.user.fullName)
                Redirect(routes.Amigo.showRecipe(id)).flashing("info" -> "Successfully created recipe")
              case None =>
                val formWithError = Forms.createRecipe.fill((id, description, baseImageId)).withError("baseImageId", "Unknown base image")
                BadRequest(views.html.newRecipe(formWithError, BaseImages.list().toSeq, Roles.list))
            }
        }
    })
  }

  def startBaking(recipeId: RecipeId) = AuthAction { request =>
    Recipes.findById(RecipeId("ubuntu-wily-java8")).fold[Result](NotFound) { recipe =>
      val buildNumber = Recipes.incrementAndGetBuildNumber(recipe.id).get
      val theBake = Bakes.create(recipe, buildNumber, startedBy = request.user.fullName)
      PackerRunner.createImage(theBake, eventBus)
      Redirect(routes.Amigo.showBake(recipeId, buildNumber))
    }
  }

  def showBake(recipeId: RecipeId, buildNumber: Int) = AuthAction {
    Bakes.findById(recipeId, buildNumber).fold[Result](NotFound) { bake =>
      val bakeLogs = BakeLogs.list(BakeId(recipeId, buildNumber))
      Ok(views.html.showBake(bake, bakeLogs))
    }
  }

  def bakeEvents(recipeId: RecipeId, buildNumber: Int) = AuthAction { implicit req =>
    val bakeId = BakeId(recipeId, buildNumber)
    Ok.feed(eventsOut
      &> Concurrent.buffer(50) // TODO filter by bakeId
      &> EventSource[BakeEvent]()).as("text/event-stream")
  }
}

object Amigo {

  def parseEnabledRoles(form: Map[String, Seq[String]]): List[CustomisedRole] = {
    val enabledRoles = form.getOrElse("roles", Nil)
    enabledRoles.map { roleName =>
      val variablesString = form.get(s"role-$roleName-variables").flatMap(_.headOption).getOrElse("")
      val variables = CustomisedRole.formInputTextToVariables(variablesString)
      CustomisedRole(RoleId(roleName), variables)
    }.toList
  }

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

    val editRecipe = Form(tuple(
      "description" -> text(maxLength = 10000),
      "baseImageId" -> nonEmptyText(maxLength = 50).transform[BaseImageId](BaseImageId.apply, _.value)
    ))

    val createRecipe = Form(tuple(
      "id" -> text(maxLength = 50).transform[RecipeId](RecipeId.apply, _.value),
      "description" -> text(maxLength = 10000),
      "baseImageId" -> nonEmptyText(maxLength = 50).transform[BaseImageId](BaseImageId.apply, _.value)
    ))

  }

}
