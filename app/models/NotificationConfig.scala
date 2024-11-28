package models

import software.amazon.awssdk.services.sns.SnsAsyncClient

case class NotificationConfig(
    baseUrl: String,
    snsTopicArn: String,
    snsClient: SnsAsyncClient,
    amigoStage: String
)
