package notification

import com.amazonaws.services.sns.model.PublishRequest
import models.{ AmiId, Recipe }
import play.api.libs.json.Json

class NotificationSender(sns: SNS) {
  def sendTopicMessage(recipe: Recipe, amiId: AmiId): Unit = {
    val message = Json.obj(
      "bakedAmi" -> amiId.value,
      "targetAccounts" -> Json.toJson(recipe.encryptFor.map(_.accountNumber))
    // TODO: do we include more data or let the lambda look it up from the AMI tags?
    )
    val messageStr = Json.stringify(message)
    sns.client.publish(new PublishRequest().withTopicArn(sns.topicArn).withMessage(messageStr))
  }
}
