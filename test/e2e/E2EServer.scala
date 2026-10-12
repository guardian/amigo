package e2e

import com.google.api.client.googleapis.GoogleUtils
import play.api.{Configuration, Mode}
import play.core.server.{Server, ServerConfig}

import java.io.FileInputStream
import java.security.cert.CertificateFactory
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.annotation.nowarn
import scala.util.Using

/** Test-classpath-only entry point; never packaged with the deployed app. */
object E2EServer {
  private def required(name: String): String =
    sys.env.getOrElse(name, sys.error(s"Missing E2E setting: $name"))

  private def configureProxy(): Unit = {
    val proxyPort = required("E2E_PROXY_PORT")
    for (scheme <- Seq("http", "https")) {
      System.setProperty(s"$scheme.proxyHost", "127.0.0.1")
      System.setProperty(s"$scheme.proxyPort", proxyPort)
    }
    System.setProperty("http.nonProxyHosts", "localhost|127.*")
    System.setProperty("javax.net.ssl.trustStore", required("E2E_TRUST_STORE"))
    System.setProperty(
      "javax.net.ssl.trustStorePassword",
      required("E2E_TRUST_PASSWORD")
    )
    configureGoogleTrust()
  }

  // The pinned group checker constructs this legacy transport internally.
  @nowarn("cat=deprecation")
  private def configureGoogleTrust(): Unit = {
    val certificate = Using.resource(
      new FileInputStream(required("E2E_PROXY_CERT"))
    )(CertificateFactory.getInstance("X.509").generateCertificate)
    GoogleUtils.getCertificateTrustStore.setCertificateEntry("e2e", certificate)
  }

  private def configuration(port: Int): Configuration =
    Configuration(
      "play.http.secret.key" -> required("E2E_SESSION_KEY"),
      "google.redirectUrl" -> s"http://127.0.0.1:$port/oauth2callback",
      "e2e.serviceAccountPath" -> required("E2E_SERVICE_ACCOUNT"),
      "play.ws.ahc.useProxyProperties" -> true,
      "play.ws.ssl.trustManager.stores" -> Seq(
        Map(
          "type" -> "PKCS12",
          "path" -> required("E2E_TRUST_STORE"),
          "password" -> required("E2E_TRUST_PASSWORD")
        )
      )
    )

  def main(args: Array[String]): Unit = {
    configureProxy()
    val port = required("E2E_PORT").toInt
    val shutdown = Promise[Unit]()
    val stopped = Promise[Unit]()
    sys.addShutdownHook {
      shutdown.trySuccess(())
      Await.ready(stopped.future, 15.seconds)
      ()
    }
    try {
      Server.withRouterFromComponents(
        ServerConfig(port = Some(0), address = "127.0.0.1", mode = Mode.Dev)
      )(E2EPrismStub.routes) { prismPort =>
        Server.withApplicationFromContext(
          ServerConfig(
            port = Some(port),
            address = "127.0.0.1",
            mode = Mode.Dev
          )
        ) { context =>
          val configured = context.copy(initialConfiguration =
            configuration(port).withFallback(context.initialConfiguration)
          )
          new E2EComponents(
            configured,
            s"http://127.0.0.1:$prismPort"
          ).application
        } { _ =>
          println(s"Google-mocked AMIgo listening at http://127.0.0.1:$port")
          Await.result(shutdown.future, Duration.Inf)
        }
      }
    } finally {
      stopped.trySuccess(())
      ()
    }
  }
}
