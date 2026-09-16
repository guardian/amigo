import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.gu.{AppIdentity, AwsIdentity}
import components.AppComponents
import play.api.ApplicationLoader.Context
import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Application, ApplicationLoader, Configuration, Mode}
import services.Loggable
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProviderChain,
  InstanceProfileCredentialsProvider,
  ProfileCredentialsProvider
}
import software.amazon.awssdk.regions.Region.EU_WEST_1

import scala.concurrent.Future
import scala.util.{Success, Try}

class AppLoader extends ApplicationLoader with Loggable {
  override def load(context: Context): Application = {
    new LogbackLoggerConfigurator().configure(context.environment)

    val appName = "amigo"

    val credentialsProvider = AwsCredentialsProviderChain.of(
      ProfileCredentialsProvider.create("deployTools"),
      ProfileCredentialsProvider.create(),
      InstanceProfileCredentialsProvider.create()
    )
    val isDev = context.environment.mode == Mode.Dev

    val configAndIdentity = for {
      identity <-
        if (isDev)
          Success(
            AwsIdentity(
              app = appName,
              stack = "deploy",
              stage = "DEV",
              region = EU_WEST_1.id
            )
          )
        else AppIdentity.whoAmI(defaultAppName = appName, credentialsProvider)
      config <- Try(ConfigurationLoader.load(identity, credentialsProvider) {
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
        val components =
          new AppComponents(newContext, identity, credentialsProvider)
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
