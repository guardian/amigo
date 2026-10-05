package e2e

import play.api.ApplicationLoader.Context
import play.api.libs.logback.LogbackLoggerConfigurator
import play.api.{Application, ApplicationLoader, Mode}
import play.core.server.{PekkoHttpServer, ServerConfig}

import scala.concurrent.Future
import scala.util.control.NonFatal

class E2EApplicationLoader extends ApplicationLoader {
  override def load(context: Context): Application = {
    require(
      context.environment.mode == Mode.Dev,
      "E2E bootstrap requires development mode"
    )
    new LogbackLoggerConfigurator().configure(context.environment)
    val prismServer = PekkoHttpServer.fromRouterWithComponents(
      ServerConfig(port = Some(0), address = "localhost", mode = Mode.Dev)
    )(E2EPrismStub.routes)
    try {
      val prismPort = prismServer.httpPort.getOrElse(
        throw new IllegalStateException("E2E Prism stub has no HTTP port")
      )
      val components =
        new E2EComponents(context, s"http://localhost:$prismPort")
      components.applicationLifecycle.addStopHook { () =>
        Future.successful(prismServer.stop())
      }
      components.application
    } catch {
      case NonFatal(error) =>
        prismServer.stop()
        throw error
    }
  }
}
