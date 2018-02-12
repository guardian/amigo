package services

import akka.agent.Agent
import akka.actor.{Cancellable, Scheduler}
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import play.api.{Environment, Mode}
import prism.Prism
import prism.Prism.{AWSAccount, Image, Instance, LaunchConfiguration}

import scala.collection.SeqLike
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class PrismAgents(prism: Prism,
    lifecycle: ApplicationLifecycle,
    scheduler: Scheduler,
    environment: Environment)(implicit exec: ExecutionContext) {

  private val instancesAgent: Agent[Seq[Instance]] = Agent(Seq.empty)
  private val launchConfigurationsAgent: Agent[Seq[LaunchConfiguration]] = Agent(Seq.empty)
  private val copiedImagesAgent: Agent[Map[String, Seq[Image]]] = Agent(Map.empty)
  private val accountsAgent: Agent[Seq[AWSAccount]] = Agent(Seq.empty)

  def allInstances: Seq[Instance] = instancesAgent.get
  def allLaunchConfigurations: Seq[LaunchConfiguration] = launchConfigurationsAgent.get
  def copiedImages(sourceAmiIds: Set[String]): Map[String, Seq[Image]] = copiedImagesAgent.get.filterKeys(sourceAmiIds.contains)
  def accounts: Seq[AWSAccount] = accountsAgent.get

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
    refresh(prism.findAllInstances(), instancesAgent, "instances")(identity)
    refresh(prism.findAllLaunchConfigurations(), launchConfigurationsAgent, "launch configuration")(identity)
    refresh(prism.findCopiedImages(), copiedImagesAgent, "copied image")(_.groupBy(_.copiedFromAMI))
    refresh(prism.findAllAWSAccounts(), accountsAgent, "aws accounts")(identity)
  }

  private def refresh[T <: SeqLike[_, _], R](source: => Future[T], agent: Agent[R], name: String)(transform: T => R): Future[Unit] = {
    source
      .map { sourceData =>
        Logger.debug(s"Prism: Loaded ${sourceData.length} $name")
        agent.send(transform(sourceData))
      }
      .recover {
        case t =>
          Logger.warn(s"Prism: Failed to update $name: ${t.getLocalizedMessage}")
      }
  }

}
