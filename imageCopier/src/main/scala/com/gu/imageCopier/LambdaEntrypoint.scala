package com.gu.imageCopier

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.ec2.{AmazonEC2, AmazonEC2ClientBuilder}
import com.gu.imageCopier.attempt.{Attempt, MessageNotForUsFailure}

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
    val amisAttempt = Attempt.traverseWithFailures(messages){ message =>
      for {
        amiEvent <- AmiEvent.fromJsonString(message.content)
        _ <- if (amiEvent.targetAccounts.contains(configuration.ownAccountNumber)) Attempt.Right(()) else Attempt.Left(MessageNotForUsFailure)
        copiedAmi <- AmiActions.copyAmi(amiEvent, configuration.kmsKeyArn)
        _ <- AmiActions.tagAmi(amiEvent, configuration.encryptedTagValue, copiedAmi)
      } yield copiedAmi
    }
    val amis = Await.result(amisAttempt.asFuture, Duration.Inf)
    println(s"Completed: $amis")
  }
}
