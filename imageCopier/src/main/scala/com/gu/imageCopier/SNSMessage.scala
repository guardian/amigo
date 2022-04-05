package com.gu.imageCopier

import com.amazonaws.services.lambda.runtime.events.SNSEvent
import scala.jdk.CollectionConverters._

case class SNSMessage(id: String, content: String)

object SNSMessage {
  def fromLambdaEvent(event: SNSEvent): List[SNSMessage] = {
    event.getRecords.asScala.toList.map { record =>
      SNSMessage(record.getSNS.getMessageId, record.getSNS.getMessage)
    }
  }
}