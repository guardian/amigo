import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{ Application, ApplicationLoader, Logger }
import components.AppComponents
import play.api.ApplicationLoader.Context
import services.Loggable

import scala.concurrent.Future

class AppLoader extends ApplicationLoader with Loggable {
  override def load(context: Context): Application = {
    new LogbackLoggerConfigurator().configure(context.environment)
    val components = new AppComponents(context)

    log.info("Starting the scheduler")
    components.scheduler.start()
    components.applicationLifecycle.addStopHook { () =>
      log.info("Shutting down scheduler")
      Future.successful(components.scheduler.shutdown())
    }

    components.application
  }

}
