package com.gu.imageCopier

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.ec2.{AmazonEC2, AmazonEC2ClientBuilder}
import com.gu.imageCopier.attempt.{
  Attempt,
  ConfigurationFailure,
  Failure,
  MessageNotForUsFailure
}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object LambdaEntrypoint {
  val configuration: Configuration = Configuration.fromEnvironment
  implicit val ec2Client: AmazonEC2 = AmazonEC2ClientBuilder.defaultClient()
}

class LambdaEntrypoint {
  import LambdaEntrypoint._

  def run(input: SNSEvent, context: Context): Unit = {
    println("Running AMIgo copier")
    val messages = SNSMessage.fromLambdaEvent(input)
    println(s"Got messages: $messages")
    val amisAttempt = Attempt.traverseWithFailures(messages) { message =>
      for {
        kmsKeyArn <- Attempt.fromOption(
          configuration.kmsKeyArn,
          ConfigurationFailure("KMS key ARN not configured")
        )
        encryptedTagValue <- Attempt.fromOption(
          configuration.encryptedTagValue,
          ConfigurationFailure("Encrypted tag value not configured")
        )
        amiEvent <- AmiEvent.fromJsonString(message.content)
        _ <-
          if (amiEvent.targetAccounts.contains(configuration.ownAccountNumber))
            Attempt.Right(())
          else Attempt.Left(MessageNotForUsFailure)
        copiedAmi <- AmiActions.copyAmi(amiEvent, kmsKeyArn)
        _ <- AmiActions.tagAmi(amiEvent, encryptedTagValue, copiedAmi)
      } yield copiedAmi
    }
    val amis = Await.result(amisAttempt.asFuture, Duration.Inf)
    println(s"Completed: $amis")
  }

  def housekeeping(input: SNSEvent, context: Context): Unit = {
    println("Running AMIgo housekeeper")
    val messages = SNSMessage.fromLambdaEvent(input)
    println(s"Got messages: $messages")
    val deleteAttempt = Attempt.traverseWithFailures(messages) { message =>
      for {
        deleteEvent <- DeleteEvent.fromJsonString(message.content)
        localAmis = deleteEvent.amis.filter(
          _.account == configuration.ownAccountNumber
        )
        actualAmisAndSnapshots <- AmiActions.getImagesAndEbsSnapshots(localAmis)
        deletedAmis <- Attempt.traverseWithFailures(actualAmisAndSnapshots) {
          case (ami, _) => AmiActions.deregisterAmi(ami)
        }
        actualSnapshots = actualAmisAndSnapshots.flatMap {
          case (_, snapshots) => snapshots
        }
        deletedSnapshots <- Attempt.traverseWithFailures(actualSnapshots) {
          snapshot => AmiActions.deleteSnapshot(snapshot)
        }
      } yield (deletedAmis, deletedSnapshots)
    }
    val amis = Await.result(deleteAttempt.asFuture, Duration.Inf)

    val allFailures = Failure.collect(List(amis)) { successfulAmis =>
      Failure.collect(successfulAmis) { case (deletedAmis, deletedSnapshots) =>
        Failure.collect(deletedAmis)(_ => Nil) ::: Failure.collect(
          deletedSnapshots
        )(_ => Nil)
      }
    }

    if (allFailures.nonEmpty) {
      println(s"Failures")
      allFailures.foreach { failure =>
        println(
          s"${failure.msg} ${failure.cause.map(_.getStackTrace.mkString(" "))}"
        )
      }
    }

    println(s"Completed: $amis")
  }
}
