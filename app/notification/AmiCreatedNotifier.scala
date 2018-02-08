package notification

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import data.{ Bakes, Dynamo }
import event.BakeEvent
import event.BakeEvent.AmiCreated
import models.{ AmiId, Bake }
import play.api.Logger

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class AmiCreatedNotifier(eventsSource: Source[BakeEvent, _],
    amiCreated: (Bake, AmiId) => Unit)(implicit dynamo: Dynamo, materializer: Materializer, ec: ExecutionContext) {
  val notifyOnSuccess: Future[Done] = {
    eventsSource.runForeach {
      case AmiCreated(bakeId, amiId) =>
        Logger.info("Received an AMI created event")
        for {
          bake <- Bakes.findById(bakeId.recipeId, bakeId.buildNumber)
        } {
          Logger.info(s"Notifying that $amiId exists")
          amiCreated(bake, amiId)
        }
      case _ => // discard
    }
  }
  notifyOnSuccess.onComplete {
    case Success(_) => Logger.info("AmiCreatedNotifier has completed")
    case Failure(t) => Logger.warn("AmiCreatedNotifier has totally bailed", t)
  }

}
