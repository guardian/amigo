package prism

import org.scalatest.{ FlatSpec, Matchers }
import play.api.mvc.{ Action, Results }
import play.api.test.WsTestClient
import play.core.server.Server
import play.api.routing.sird._
import prism.Prism.{ Instance, LaunchConfiguration }
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class PrismSpec extends FlatSpec with Matchers {

  def withPrismClient[T](block: Prism => T): T = {
    Server.withRouter() {
      case GET(p"/sources") => Action {
        Results.Ok.sendResource("prism/sources.json")
      }
      case GET(p"/instances") => Action {
        Results.Ok.sendResource("prism/instances.json")
      }
      case GET(p"/launch-configurations") => Action {
        Results.Ok.sendResource("prism/launch-configurations.json")
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

  it should "fetch all instances" in {
    withPrismClient { prism =>
      val instances = Await.result(prism.findAllInstances(), 10.seconds)
      instances should be(Seq(Instance("ami-fa123456"), Instance("ami-abcd4321")))
    }
  }

  it should "fetch all launch configurations" in {
    withPrismClient { prism =>
      val launchConfigurations = Await.result(prism.findAllLaunchConfigurations(), 10.seconds)
      launchConfigurations should be(Seq(LaunchConfiguration("ami-abcdefg1"), LaunchConfiguration("ami-gfedcba2")))
    }
  }

}
