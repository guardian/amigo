import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{ Logger, Application, ApplicationLoader }
import components.AppComponents

import play.api.ApplicationLoader.Context

class AppLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    new LogbackLoggerConfigurator().configure(context.environment)
    val components = new AppComponents(context)

    Logger.info("Starting the scheduler")
    components.bakeScheduler.start()

    components.application
  }

}
