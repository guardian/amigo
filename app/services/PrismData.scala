package services

import akka.actor.{Cancellable, Scheduler}
import models.AmiId
import org.joda.time.DateTime
import play.api.inject.ApplicationLifecycle
import play.api.{Environment, Mode}
import prism.Prism
import prism.Prism.{AWSAccount, Image, Instance, LaunchConfiguration}

import java.util.concurrent.atomic.AtomicReference
import scala.collection.{MapView, SeqLike, SeqOps}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object PrismData {
  val MAX_AGE: Long = 15 * 60 * 1000

  trait Failure
  case object NotInitialised extends Failure

  type CacheData[T] = Either[Failure, (T, DateTime)]
  def dataToResult[T](data: CacheData[T], now: DateTime)(implicit
      exec: ExecutionContext
  ): T = data match {
    case Left(NotInitialised) =>
      throw new IllegalStateException(
        s"AMIgo internal data cache is not yet populated"
      )
    case Left(_) =>
      throw new IllegalStateException(s"CacheData failed for unknown reason")
    case Right((_, staleTimeStamp))
        if (now.getMillis - staleTimeStamp.getMillis) > MAX_AGE =>
      throw new IllegalStateException(
        s"AMIgo internal data cache is stale - last update at $staleTimeStamp"
      )
    case Right((t, _)) => t
  }
}

class PrismData(
    prism: Prism,
    lifecycle: ApplicationLifecycle,
    scheduler: Scheduler,
    environment: Environment
)(implicit exec: ExecutionContext)
    extends Loggable {

  import PrismData._

  private val instancesAgent: AtomicReference[CacheData[Seq[Instance]]] =
    new AtomicReference(Left(NotInitialised))
  private val launchConfigurationsAgent
      : AtomicReference[CacheData[Seq[LaunchConfiguration]]] =
    new AtomicReference(Left(NotInitialised))
  private val copiedImagesAgent
      : AtomicReference[CacheData[Map[AmiId, Seq[Image]]]] =
    new AtomicReference(Left(NotInitialised))
  private val accountsAgent: AtomicReference[CacheData[Seq[AWSAccount]]] =
    new AtomicReference(Left(NotInitialised))

  val baseUrl: String = prism.baseUrl

  def allInstances: Seq[Instance] =
    dataToResult(instancesAgent.get, DateTime.now)
  def allLaunchConfigurations: Seq[LaunchConfiguration] =
    dataToResult(launchConfigurationsAgent.get, DateTime.now)
  def copiedImages(sourceAmiIds: Set[AmiId]): Map[AmiId, Seq[Image]] =
    dataToResult(copiedImagesAgent.get, DateTime.now).view
      .filterKeys(sourceAmiIds.contains)
      .toMap
  def accounts: Seq[AWSAccount] = dataToResult(accountsAgent.get, DateTime.now)

  if (environment.mode != Mode.Test) {

    val prismDataSchedule: Cancellable =
      scheduler.scheduleWithFixedDelay(0.seconds, 1.minutes) { () =>
        {
          log.debug(s"Refreshing Prism data")
          refresh(prism.findAllInstances(), instancesAgent, "instances")(
            identity
          )
          refresh(
            prism.findAllLaunchConfigurations(),
            launchConfigurationsAgent,
            "launch configuration"
          )(identity)
          refresh(prism.findCopiedImages(), copiedImagesAgent, "copied image")(
            _.groupBy(_.copiedFromAMI)
          )
          refresh(prism.findAllAWSAccounts(), accountsAgent, "aws accounts")(
            identity
          )
        }
      }

    lifecycle.addStopHook { () =>
      prismDataSchedule.cancel()
      Future.successful(())
    }
  }

  private def refresh[T <: SeqOps[_, Seq, _], R](
      source: => Future[T],
      reference: AtomicReference[CacheData[R]],
      name: String
  )(transform: T => R): Future[Unit] = {
    source
      .map { sourceData =>
        log.debug(s"Prism: Loaded ${sourceData.length} $name")
        reference.set(Right(transform(sourceData) -> DateTime.now))
      }
      .recover { case t =>
        log.warn(s"Prism: Failed to update $name: ${t.getLocalizedMessage}")
      }
  }

}
