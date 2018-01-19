package com.gu.imageCopier

case class Configuration(ownAccountNumber: String, kmsKeyArn: String, encryptedTagValue: String)

object Configuration {
  def fromEnvironment: Configuration = {
    val accountId = System.getenv("ACCOUNT_ID")
    val kmsKeyArn = System.getenv("KMS_KEY_ARN")
    val encryptedTagValue = System.getenv("ENCRYPTED_TAG_VALUE")
    Configuration(accountId, kmsKeyArn, encryptedTagValue)
  }
}
