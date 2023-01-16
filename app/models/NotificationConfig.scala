package models

import com.amazonaws.services.sns.AmazonSNSAsync

case class NotificationConfig(
    baseUrl: String,
    snsTopicArn: String,
    snsClient: AmazonSNSAsync,
    amigoStage: String
)
