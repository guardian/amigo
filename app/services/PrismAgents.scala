package services

import akka.agent.Agent
import akka.actor.{Cancellable, Scheduler}
import models.AmiId
import org.joda.time.DateTime
import play.api.inject.ApplicationLifecycle
import play.api.{Environment, Mode}
import prism.Prism
import prism.Prism.{AWSAccount, Image, Instance, LaunchConfiguration}

import scala.collection.SeqLike
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object PrismAgents {
  val MAX_AGE: Long = 15 * 60 * 1000

  trait Failure
  case object NotInitialised extends Failure

  type CacheData[T] = Either[Failure, (T, DateTime)]
  def dataToResult[T](data: CacheData[T], now: DateTime)(implicit exec: ExecutionContext): T = data match {
    case Left(NotInitialised) =>
      throw new IllegalStateException(s"AMIgo internal data cache is not yet populated")
    case Right((_ , staleTimeStamp)) if (now.getMillis - staleTimeStamp.getMillis) > MAX_AGE =>
      throw new IllegalStateException(s"AMIgo internal data cache is stale - last update at $staleTimeStamp")
    case Right((t, _)) => t
  }
}



class PrismAgents(prism: Prism,
    lifecycle: ApplicationLifecycle,
    scheduler: Scheduler,
    environment: Environment)(implicit exec: ExecutionContext) extends Loggable {

  import PrismAgents._

  private val instancesAgent: Agent[CacheData[Seq[Instance]]] = Agent(Left(NotInitialised))
  private val launchConfigurationsAgent: Agent[CacheData[Seq[LaunchConfiguration]]] = Agent(Left(NotInitialised))
  private val copiedImagesAgent: Agent[CacheData[Map[AmiId, Seq[Image]]]] = Agent(Left(NotInitialised))
  private val accountsAgent: Agent[CacheData[Seq[AWSAccount]]] = Agent(Left(NotInitialised))

  val baseUrl: String = prism.baseUrl

  def allInstances: Seq[Instance] = dataToResult(instancesAgent.get, DateTime.now)
  def allLaunchConfigurations: Seq[LaunchConfiguration] = dataToResult(launchConfigurationsAgent.get, DateTime.now)
  def copiedImages(sourceAmiIds: Set[AmiId]): Map[AmiId, Seq[Image]] = dataToResult(copiedImagesAgent.get, DateTime.now).filterKeys(sourceAmiIds.contains)
  def accounts: Seq[AWSAccount] = dataToResult(accountsAgent.get, DateTime.now)

  if (environment.mode != Mode.Test) {

    val prismDataSchedule: Cancellable = scheduler.schedule(0.seconds, 1.minutes) {
      log.debug(s"Refreshing Prism data")
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

  private def refresh[T <: SeqLike[_, _], R](source: => Future[T], agent: Agent[CacheData[R]], name: String)(transform: T => R): Future[Unit] = {
    source
      .map { sourceData =>
        log.debug(s"Prism: Loaded ${sourceData.length} $name")
        agent.send(Right(transform(sourceData) -> DateTime.now))
      }
      .recover {
        case t =>
          log.warn(s"Prism: Failed to update $name: ${t.getLocalizedMessage}")
      }
  }

}
