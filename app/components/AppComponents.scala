package components

import akka.actor.Props
import controllers.Amigo
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.inject.{ Injector, SimpleInjector, NewInstanceInjector }
import play.api.routing.Router
import router.Routes
import websockets.WebSocketMaster

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context) {
  val webSocketMaster = actorSystem.actorOf(Props(new WebSocketMaster))
  val controller = new Amigo(webSocketMaster, () => this.application)
  val assets = new controllers.Assets(httpErrorHandler)
  lazy val router: Router = new Routes(httpErrorHandler, controller, assets)

  // Play, I hate you sometimes. Workaround for https://github.com/playframework/playframework/pull/4618
  override lazy val injector: Injector =
    new SimpleInjector(NewInstanceInjector) + router + crypto + httpConfiguration + tempFileCreator + actorSystem
}
