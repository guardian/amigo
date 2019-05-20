package prism

import models.AmiId
import org.scalatest.{ FlatSpec, Matchers }
import play.api.mvc.{ Action, Results }
import play.api.routing.sird._
import play.api.test.WsTestClient
import play.core.server.Server
import prism.Prism.{ AWSAccount, Instance, LaunchConfiguration }
import test.AttemptValues

import scala.concurrent.ExecutionContext.Implicits.global

class PrismSpec extends FlatSpec with Matchers with AttemptValues {

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
      val accounts = prism.findAllAWSAccounts().successValue
      accounts should be(Seq(AWSAccount("foo", "1234"), AWSAccount("bar", "5678")))
    }
  }

  it should "fetch all instances" in {
    withPrismClient { prism =>
      val instances = prism.findAllInstances().successValue
      instances should be(Seq(
        Instance("i-123456", AmiId("ami-fa123456"), AWSAccount("MyAccount", "1234567890")),
        Instance("i-b123456", AmiId("ami-abcd4321"), AWSAccount("MyAccount", "1234567890"))
      ))
    }
  }

  it should "fetch all launch configurations" in {
    withPrismClient { prism =>
      val launchConfigurations = prism.findAllLaunchConfigurations().successValue
      launchConfigurations should be(Seq(
        LaunchConfiguration("MyName", AmiId("ami-abcdefg1"), AWSAccount("MyAccount", "1234567890")),
        LaunchConfiguration("MyId", AmiId("ami-gfedcba2"), AWSAccount("MyAccount", "1234567890"))
      ))
    }
  }

}
