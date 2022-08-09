import com.gu.{ AppIdentity, AwsIdentity }
import com.gu.conf.{ ConfigurationLoader, SSMConfigurationLocation }
import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{ Application, ApplicationLoader, Configuration }
import components.AppComponents
import play.api.ApplicationLoader.Context
import services.Loggable

import scala.concurrent.Future

class AppLoader extends ApplicationLoader with Loggable {
  override def load(context: Context): Application = {
    new LogbackLoggerConfigurator().configure(context.environment)
    try {
      val identity = AppIdentity.whoAmI(defaultAppName = "amigo")
      val loadedConfig = ConfigurationLoader.load(identity) {
        case identity: AwsIdentity => SSMConfigurationLocation.default(identity)
      }
      val newContext = context.copy(initialConfiguration = Configuration(loadedConfig).withFallback(context.initialConfiguration))
      val components = new AppComponents(newContext, identity)
      log.info("Starting the scheduler")
      components.quartzScheduler.start()
      components.applicationLifecycle.addStopHook { () =>
        log.info("Shutting down scheduler")
        Future.successful(components.quartzScheduler.shutdown())
      }
      components.application
    } catch {
      case err: Throwable =>
        log.error(s"Failed to start application due to $err")
        throw err
    }
  }

}
