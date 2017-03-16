package prism

import org.scalatest.{ FlatSpec, Matchers }
import play.api.mvc.{ Action, Results }
import play.api.test.WsTestClient
import play.core.server.Server
import play.api.routing.sird._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class PrismSpec extends FlatSpec with Matchers {

  def withPrismClient[T](block: Prism => T): T = {
    Server.withRouter() {
      case GET(p"/sources") => Action {
        Results.Ok.sendResource("prism/sources.json")
      }
    } { implicit port =>
      WsTestClient.withClient { client =>
        block(new Prism(client, baseUrl = ""))
      }
    }
  }

  it should "fetch all AWS account numbers" in {
    withPrismClient { prism =>
      val accounts = Await.result(prism.findAllAWSAccountNumbers(), 10.seconds)
      accounts should be(Seq("1234", "5678"))
    }
  }

}
