import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{ Application, ApplicationLoader, Logger }
import components.AppComponents
import play.api.ApplicationLoader.Context
import services.Loggable

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

class AppLoader extends ApplicationLoader with Loggable {
  override def load(context: Context): Application = {
    new LogbackLoggerConfigurator().configure(context.environment)
    try {
      val components = new AppComponents(context)
      log.info("Starting the scheduler")
      components.scheduler.start()
      components.applicationLifecycle.addStopHook { () =>
        log.info("Shutting down scheduler")
        Future.successful(components.scheduler.shutdown())
      }
      components.application
    } catch {
      case err: Throwable =>
        log.error(s"Failed to start application due to $err")
        throw err
    }
  }

}
