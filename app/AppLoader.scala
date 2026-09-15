import com.gu.{AppIdentity, AwsIdentity, DevIdentity}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Application, ApplicationLoader, Configuration, Mode}
import components.AppComponents
import play.api.ApplicationLoader.Context
import services.Loggable
import software.amazon.awssdk.auth.credentials.{
  AwsCredentialsProvider,
  AwsCredentialsProviderChain,
  DefaultCredentialsProvider,
  InstanceProfileCredentialsProvider,
  ProfileCredentialsProvider
}

import scala.concurrent.Future
import scala.util.{Success, Try}

class AppLoader extends ApplicationLoader with Loggable {
  override def load(context: Context): Application = {
    new LogbackLoggerConfigurator().configure(context.environment)

    // TODO: use this for both cases
    val credentialsProvider = DefaultCredentialsProvider.builder().build()
    val isDev = context.environment.mode == Mode.Dev

    val configAndIdentity = for {
      identity <-
        if (isDev)
          Success(
            AwsIdentity(
              app = "amigo",
              stack = "deploy",
              stage = "DEV",
              region = "eu-west-1"
            )
          )
        else AppIdentity.whoAmI(defaultAppName = "amigo", credentialsProvider)
      config <- Try(
        ConfigurationLoader.load(
          identity,
          AwsCredentialsProviderChain
            .builder()
            .addCredentialsProvider(InstanceProfileCredentialsProvider.create())
            .addCredentialsProvider(
              ProfileCredentialsProvider.create("deployTools")
            )
            .build()
        ) { case identity: AwsIdentity =>
          println(s"***/${identity.stage}/${identity.stack}/${identity.app}")
          SSMConfigurationLocation.default(identity)
        }
      )
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
