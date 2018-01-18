package notification

import com.amazonaws.services.sns.model.PublishRequest
import models.{ AmiId, Bake, Recipe }
import models.packer.PackerVariablesConfig
import _root_.packer.ImageTags
import play.api.Logger
import play.api.libs.json.Json

class NotificationSender(sns: SNS, stage: String) {
  def sendTopicMessage(recipe: Recipe, bake: Bake, amiId: AmiId): Unit = {
    val vars = PackerVariablesConfig(bake)
    val tags = ImageTags.tags(vars, stage)
    val message = Json.obj(
      "bakedAmi" -> amiId.value,
      "targetAccounts" -> Json.toJson(recipe.encryptFor.map(_.accountNumber)),
      "tags" -> tags
    )
    val messageStr = Json.stringify(message)
    Logger.info(s"Sending message: $messageStr")
    sns.client.publish(new PublishRequest().withTopicArn(sns.topicArn).withMessage(messageStr))
  }
}
