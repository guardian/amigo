package notification

import com.amazonaws.services.sns.model.PublishRequest
import models.{ AmiId, Bake }
import models.packer.PackerVariablesConfig
import _root_.packer.ImageDetails
import play.api.libs.json.{ Json, JsString, JsValue, Writes }
import prism.Ami
import services.Loggable

class NotificationSender(sns: SNS, region: String, stage: String) extends Loggable {
  def sendTopicMessage(bake: Bake, amiId: AmiId): Unit = {
    val vars = PackerVariablesConfig(bake)
    val imageDetails = ImageDetails.apply(vars, stage)
    val message = Json.obj(
      "sourceAmi" -> amiId.value,
      "sourceRegion" -> region,
      "targetAccounts" -> Json.toJson(bake.recipe.encryptFor.map(_.accountNumber)),
      "name" -> imageDetails.name,
      "description" -> imageDetails.description,
      "tags" -> imageDetails.tags)
    val messageStr = Json.stringify(message)
    log.info(s"Sending message to topic ${sns.topicArn}: $messageStr")
    sns.client.publish(new PublishRequest().withTopicArn(sns.topicArn).withMessage(messageStr))
  }

  def sendHousekeepingTopicMessage(amisToDelete: List[Ami]): Unit = {
    implicit val amiIdWrites: Writes[AmiId] = new Writes[AmiId] {
      def writes(o: AmiId) = JsString(o.value)
    }
    implicit val amiWrites: Writes[Ami] = Json.writes[Ami]
    // don't overwhelm the receiver with more than 10 AMIs per message
    amisToDelete.grouped(10).foreach { batchToDelete =>
      val message = Json.obj("amis" -> batchToDelete)
      val messageStr = Json.stringify(message)
      log.info(s"Sending message to topic ${sns.housekeepingTopicArn}: $messageStr")
      sns.client.publish(new PublishRequest().withTopicArn(sns.housekeepingTopicArn).withMessage(messageStr))
    }
  }
}
