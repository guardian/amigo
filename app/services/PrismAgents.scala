package services

import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.api.{ Environment, Mode }
import akka.agent.Agent
import prism.Prism
import prism.Prism.{ Instance, LaunchConfiguration }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import rx.lang.scala.{ Observable, Subscription }

class PrismAgents(prism: Prism, lifecycle: ApplicationLifecycle, environment: Environment)(implicit exec: ExecutionContext) {

  private val instancesAgent: Agent[Seq[Instance]] = Agent(Seq.empty)
  private val launchConfigurationsAgent: Agent[Seq[LaunchConfiguration]] = Agent(Seq.empty)

  def allInstances: Seq[Instance] = instancesAgent.get
  def allLaunchConfigurations: Seq[LaunchConfiguration] = launchConfigurationsAgent.get

  if (environment.mode != Mode.Test) {
    refresh

    val prismDataSubscription: Subscription = Observable.interval(1.minutes).subscribe { i =>
      Logger.debug(s"Refreshing Prism data")
      refresh
    }

    lifecycle.addStopHook { () =>
      prismDataSubscription.unsubscribe()
      Future.successful(())
    }
  }

  private def refresh: Future[Unit] = {
    refreshInstances()
    refreshLaunchConfigurations()
  }

  private def refreshInstances(): Future[Unit] = {
    prism.findAllInstances()
      .map { instances =>
        Logger.debug(s"Prism: Loaded ${instances.length} instances")
        instancesAgent.send(instances)
      }
      .recover {
        case t =>
          Logger.warn(s"Prism: Failed to update instances: ${t.getLocalizedMessage}")
      }
  }

  private def refreshLaunchConfigurations(): Future[Unit] = {
    prism.findAllLaunchConfigurations()
      .map { lcs =>
        Logger.debug(s"Prism: Loaded ${lcs.length} launch configurations")
        launchConfigurationsAgent.send(lcs)
      }
      .recover {
        case t =>
          Logger.warn(s"Prism: Failed to update launch configurations: ${t.getLocalizedMessage}")
      }
  }

}
