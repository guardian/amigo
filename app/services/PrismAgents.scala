package services

import akka.agent.Agent
import akka.actor.{ Cancellable, Scheduler }
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.api.{ Environment, Mode }
import prism.Prism
import prism.Prism.{ Instance, LaunchConfiguration }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

class PrismAgents(prism: Prism,
    lifecycle: ApplicationLifecycle,
    scheduler: Scheduler,
    environment: Environment)(implicit exec: ExecutionContext) {

  private val instancesAgent: Agent[Seq[Instance]] = Agent(Seq.empty)
  private val launchConfigurationsAgent: Agent[Seq[LaunchConfiguration]] = Agent(Seq.empty)

  def allInstances: Seq[Instance] = instancesAgent.get
  def allLaunchConfigurations: Seq[LaunchConfiguration] = launchConfigurationsAgent.get

  if (environment.mode != Mode.Test) {

    val prismDataSchedule: Cancellable = scheduler.schedule(0.seconds, 1.minutes) {
      Logger.debug(s"Refreshing Prism data")
      refresh
    }

    lifecycle.addStopHook { () =>
      prismDataSchedule.cancel()
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
