package components

import controllers.Amigo
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.libs.iteratee.Concurrent
import play.api.routing.Router
import router.Routes
import event.{ BakeEvent, ChannelWrapper }

class AppComponents(context: Context)
    extends BuiltInComponentsFromContext(context) {

  val (eventsOut, eventsChannel) = Concurrent.broadcast[BakeEvent]
  val eventBus = new ChannelWrapper(eventsChannel)
  val controller = new Amigo(eventsOut, eventBus)
  val assets = new controllers.Assets(httpErrorHandler)
  lazy val router: Router = new Routes(httpErrorHandler, controller, assets)
}
