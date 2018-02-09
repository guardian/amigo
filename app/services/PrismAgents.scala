package services

import akka.agent.Agent
import akka.actor.{ Cancellable, Scheduler }
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.api.{ Environment, Mode }
import prism.Prism
import prism.Prism.{ Image, Instance, LaunchConfiguration }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

class PrismAgents(prism: Prism,
    lifecycle: ApplicationLifecycle,
    scheduler: Scheduler,
    environment: Environment)(implicit exec: ExecutionContext) {

  private val instancesAgent: Agent[Seq[Instance]] = Agent(Seq.empty)
  private val launchConfigurationsAgent: Agent[Seq[LaunchConfiguration]] = Agent(Seq.empty)
  private val copiedImagesAgent: Agent[Map[String, Seq[Image]]] = Agent(Map.empty)

  def allInstances: Seq[Instance] = instancesAgent.get
  def allLaunchConfigurations: Seq[LaunchConfiguration] = launchConfigurationsAgent.get
  def copiedImages(sourceAmiIds: Set[String]): Map[String, Seq[Image]] = copiedImagesAgent.get.filterKeys(sourceAmiIds.contains)

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
    refreshCopiedImages()
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

  private def refreshCopiedImages(): Future[Unit] = {
    prism.findCopiedImages()
      .map { copiedImages =>
        Logger.debug(s"Prism: Loaded ${copiedImages.length} copied images")
        copiedImagesAgent.send(copiedImages.groupBy(_.copiedFromAMI))
      }
      .recover {
        case t =>
          Logger.warn(s"Prism: Failed to update copied images: ${t.getLocalizedMessage}")
      }
  }

}
