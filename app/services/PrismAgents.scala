package services

import akka.agent.Agent
import akka.actor.{Cancellable, Scheduler}
import attempt._
import models.AmiId
import org.joda.time.DateTime
import play.api.inject.ApplicationLifecycle
import play.api.{Environment, Mode}
import prism.Prism
import prism.Prism.{AWSAccount, Image, Instance, LaunchConfiguration}

import scala.collection.SeqLike
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

import scala.language.postfixOps

object PrismAgents {
  val MAX_AGE: Long = 15 * 60 * 1000

  type CacheData[T] = Attempt[(T, DateTime)]
  def dataToResult[T](data: CacheData[T], now: DateTime)(implicit exec: ExecutionContext): Attempt[T] = {
    data.flatMap { case (t, timestamp) =>
      val age = now.getMillis - timestamp.getMillis
      if (age > MAX_AGE) {
        Attempt.Left(PrismStaleFailure(s"AMIgo internal data cache is stale - last update at $timestamp"))
      } else {
        Attempt.Right(t)
      }
    }
  }
}

class PrismAgents(prism: Prism,
    lifecycle: ApplicationLifecycle,
    scheduler: Scheduler,
    environment: Environment)(implicit exec: ExecutionContext) extends Loggable {

  import PrismAgents._

  private val instancesAgent: Agent[CacheData[Seq[Instance]]] = Agent(Attempt.Left(PrismNotInitialisedFailure))
  private val launchConfigurationsAgent: Agent[CacheData[Seq[LaunchConfiguration]]] = Agent(Attempt.Left(PrismNotInitialisedFailure))
  private val copiedImagesAgent: Agent[CacheData[Map[AmiId, Seq[Image]]]] = Agent(Attempt.Left(PrismNotInitialisedFailure))
  private val accountsAgent: Agent[CacheData[Seq[AWSAccount]]] = Agent(Attempt.Left(PrismNotInitialisedFailure))

  val baseUrl: String = prism.baseUrl

  def allInstances: Attempt[Seq[Instance]] = dataToResult(instancesAgent.get, DateTime.now)
  def allLaunchConfigurations: Attempt[Seq[LaunchConfiguration]] = dataToResult(launchConfigurationsAgent.get, DateTime.now)
  def copiedImages(sourceAmiIds: Set[AmiId]): Attempt[Map[AmiId, Seq[Image]]] = dataToResult(copiedImagesAgent.get, DateTime.now).map(_.filterKeys(sourceAmiIds.contains))
  def accounts: Attempt[Seq[AWSAccount]] = dataToResult(accountsAgent.get, DateTime.now)

  if (environment.mode != Mode.Test) {
    val prismDataSchedule: Cancellable = scheduler.schedule(0 seconds, 1 minute) {
      log.debug(s"Refreshing Prism data")
      refresh
    }

    lifecycle.addStopHook { () =>
      prismDataSchedule.cancel()
      Future.successful(())
    }
  }

  private def refresh: Unit = {
    val results = Await.result(Attempt.sequenceWithFailures(List(
      refresh(prism.findAllInstances(), instancesAgent, "instances")(identity),
      refresh(prism.findAllLaunchConfigurations(), launchConfigurationsAgent, "launch configuration")(identity),
      refresh(prism.findCopiedImages(), copiedImagesAgent, "copied image")(_.groupBy(_.copiedFromAMI)),
      refresh(prism.findAllAWSAccounts(), accountsAgent, "aws accounts")(identity)
    )).asFuture, 1 minute)
    results match {
      case Left(failure) => log.warn(s"Failure whilst refreshing Prism: $failure")
      case Right(nestedResults) => nestedResults.foreach {
        case Left(failure) => log.warn(s"Failure when refreshing Prism: $failure")
        case _ => // don't log success
      }
    }
  }

  private def refresh[T <: SeqLike[_, _], R](source: => Attempt[T], agent: Agent[CacheData[R]], name: String)(transform: T => R): Attempt[Unit] = {
    source
      .map { sourceData =>
        log.debug(s"Prism: Loaded ${sourceData.length} $name")
        agent.send(Attempt.Right(transform(sourceData) -> DateTime.now))
      }
  }

}
