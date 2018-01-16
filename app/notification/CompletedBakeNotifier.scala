package notification

import akka.Done
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import data.{ Bakes, Dynamo, Recipes }
import event.BakeEvent
import event.BakeEvent.PackerProcessExited
import models.{ AmiId, Recipe }

import scala.concurrent.Future

class CompletedBakeNotifier(eventsSource: Source[BakeEvent, _], notifyComplete: (Recipe, AmiId) => Unit)(implicit dynamo: Dynamo, materializer: Materializer) {
  val notifyOnSuccess: Future[Done] = {
    eventsSource.runForeach {
      case PackerProcessExited(bakeId, exitCode) if exitCode == 0 =>
        for {
          recipe <- Recipes.findById(bakeId.recipeId)
          bake <- Bakes.findById(bakeId.recipeId, bakeId.buildNumber)
          amiId <- bake.amiId
        } {
          notifyComplete(recipe, amiId)
        }
    }
  }
}
