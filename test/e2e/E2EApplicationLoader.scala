package e2e

import com.gu.googleauth.GoogleAuth
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
    val oauthServer = E2EGoogleAuth.server()
    val prismServer = PekkoHttpServer.fromRouterWithComponents(
      ServerConfig(port = Some(0), address = "localhost", mode = Mode.Dev)
    )(E2EPrismStub.routes)
    try {
      oauthServer.start()
      val prismPort = prismServer.httpPort.getOrElse(
        throw new IllegalStateException("E2E Prism stub has no HTTP port")
      )
      val components =
        new E2EComponents(context, s"http://localhost:$prismPort")
      // play-googleauth hard-codes Google's discovery URL; replace only its test-classloader cache.
      GoogleAuth.discoveryDocumentHolder = Some(
        components.wsClient
          .url(oauthServer.wellKnownUrl(E2EGoogleAuth.issuer).toString)
          .get()
          .map { response =>
            require(response.status == 200, "Mock OAuth discovery failed")
            com.gu.googleauth.DiscoveryDocument.fromJson(response.json)
          }(components.executionContext)
      )
      components.applicationLifecycle.addStopHook { () =>
        Future.successful {
          try oauthServer.shutdown()
          finally prismServer.stop()
        }
      }
      components.application
    } catch {
      case NonFatal(error) =>
        try oauthServer.shutdown()
        finally prismServer.stop()
        throw error
    }
  }
}
