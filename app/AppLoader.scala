import play.api.{ Logger, Application, ApplicationLoader }
import components.AppComponents

import play.api.ApplicationLoader.Context

class AppLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    Logger.configure(context.environment)
    val components = new AppComponents(context)
    components.application
  }

}
