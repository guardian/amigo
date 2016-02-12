package components

import controllers.Amigo
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import router.Routes

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context) {
  val controller = new Amigo
  val assets = new controllers.Assets(httpErrorHandler)
  val router = new Routes(httpErrorHandler, controller, assets)
}
