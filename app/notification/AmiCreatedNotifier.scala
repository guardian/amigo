package notification

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import data.{ Bakes, Dynamo }
import event.BakeEvent
import event.BakeEvent.AmiCreated
import models.{ AmiId, Bake }
import services.Loggable

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class AmiCreatedNotifier(eventsSource: Source[BakeEvent, _],
  amiCreated: (Bake, AmiId) => Unit)(implicit dynamo: Dynamo, materializer: Materializer, ec: ExecutionContext)
    extends Loggable {
  val notifyOnSuccess: Future[Done] = {
    eventsSource.runForeach {
      case AmiCreated(bakeId, amiId) =>
        log.info("Received an AMI created event")
        for {
          bake <- Bakes.findById(bakeId.recipeId, bakeId.buildNumber)
        } {
          log.info(s"Notifying that $amiId exists")
          amiCreated(bake, amiId)
        }
      case _ => // discard
    }
  }
  notifyOnSuccess.onComplete {
    case Success(_) => log.info("AmiCreatedNotifier has completed")
    case Failure(t) => log.warn("AmiCreatedNotifier has totally bailed", t)
  }

}
