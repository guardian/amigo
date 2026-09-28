package e2e

import play.api.ApplicationLoader.Context
import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Application, ApplicationLoader}
import services.Loggable

final class E2EAppLoader extends ApplicationLoader with Loggable {
  override def load(context: Context): Application = {
    new LogbackLoggerConfigurator().configure(context.environment)

    val startupConfig = E2EStartupConfig
      .fromEnvironment(sys.env)
      .fold(message => throw new IllegalStateException(message), identity)

    log.info(
      s"Starting isolated E2E application with '${startupConfig.scenario.name}' fixtures"
    )
    new E2EComponents(context, startupConfig).application
  }
}
