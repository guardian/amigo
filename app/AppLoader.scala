import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, InstanceProfileCredentialsProvider}
import com.gu.conf.{ConfigurationLoader, SSMConfigurationLocation}
import com.gu.{AppIdentity, AwsIdentity}
import components.AppComponents
import play.api.ApplicationLoader.Context
import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Application, ApplicationLoader, Configuration}
import services.Loggable
import software.amazon.awssdk.auth.credentials.{AwsCredentialsProviderChain => AwsCredentialsProviderChainV2, InstanceProfileCredentialsProvider => InstanceProfileCredentialsProviderV2, ProfileCredentialsProvider => ProfileCredentialsProviderV2}

import scala.concurrent.Future

class AppLoader extends ApplicationLoader with Loggable {
  override def load(context: Context): Application = {
    new LogbackLoggerConfigurator().configure(context.environment)
    try {
      val awsCredsForV1 = new AWSCredentialsProviderChain(
        new ProfileCredentialsProvider("deployTools"),
        new ProfileCredentialsProvider(),
        InstanceProfileCredentialsProvider.getInstance())

      val awsCredsForV2 = AwsCredentialsProviderChainV2
        .builder()
        .credentialsProviders(
          ProfileCredentialsProviderV2.create("deployTools"),
          ProfileCredentialsProviderV2.create(),
          InstanceProfileCredentialsProviderV2.create())
        .build()

      val identity = AppIdentity.whoAmI(defaultAppName = "amigo", awsCredsForV2).get
      val loadedConfig = ConfigurationLoader.load(identity) {
        case identity: AwsIdentity => SSMConfigurationLocation.default(identity)
      }
      val newContext = context.copy(initialConfiguration = Configuration(loadedConfig).withFallback(context.initialConfiguration))
      val components = new AppComponents(newContext, identity, awsCredsForV1, awsCredsForV2)
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
