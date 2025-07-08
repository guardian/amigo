import com.gu.{AppIdentity, AwsIdentity, DevIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Application, ApplicationLoader, Configuration, Mode}
import components.AppComponents
import play.api.ApplicationLoader.Context
import services.Loggable
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider

import scala.concurrent.Future
import scala.util.{Success, Try}

class AppLoader extends ApplicationLoader with Loggable {
  override def load(context: Context): Application = {
    new LogbackLoggerConfigurator().configure(context.environment)

    val credentialsProvider = DefaultCredentialsProvider.builder().build()
    val isDev = context.environment.mode == Mode.Dev

    val configAndIdentity = for {
      identity <-
        if (isDev) Success(DevIdentity("amigo"))
        else AppIdentity.whoAmI(defaultAppName = "amigo", credentialsProvider)
      config <- Try(ConfigurationLoader.load(identity) {
        case identity: AwsIdentity => SSMConfigurationLocation.default(identity)
      })
    } yield (config, identity)

    configAndIdentity.fold(
      err => {
        log.error(s"Failed to start application due to $err")
        throw err
      },
      configAndIdentity => {
        val (config, identity) = configAndIdentity
        val newContext = context.copy(initialConfiguration =
          Configuration(config).withFallback(context.initialConfiguration)
        )
        val components = new AppComponents(newContext, identity)
        log.info("Starting the scheduler")
        components.quartzScheduler.start()
        components.applicationLifecycle.addStopHook { () =>
          log.info("Shutting down scheduler")
          Future.successful(components.quartzScheduler.shutdown())
        }
        components.application
      }
    )
  }
}
