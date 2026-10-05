package e2e

import play.api.Mode
import play.core.server.{Server, ServerConfig}

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

/** Test-classpath-only entry point; never packaged with the deployed app. */
object E2EServer {
  def main(args: Array[String]): Unit = {
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
            port = Some(9000),
            address = "127.0.0.1",
            mode = Mode.Dev
          )
        ) { context =>
          new E2EComponents(context, s"http://127.0.0.1:$prismPort").application
        } { _ =>
          println("Offline AMIgo E2E server listening at http://127.0.0.1:9100")
          Await.result(shutdown.future, Duration.Inf)
        }
      }
    } finally {
      stopped.trySuccess(())
      ()
    }
  }
}
