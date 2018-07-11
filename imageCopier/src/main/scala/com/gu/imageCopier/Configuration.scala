package com.gu.imageCopier

case class Configuration(ownAccountNumber: String, kmsKeyArn: Option[String], encryptedTagValue: Option[String])

object Configuration {
  def fromEnvironment: Configuration = {
    val accountId = System.getenv("ACCOUNT_ID")
    val kmsKeyArn = Option(System.getenv("KMS_KEY_ARN"))
    val encryptedTagValue = Option(System.getenv("ENCRYPTED_TAG_VALUE"))
    Configuration(accountId, kmsKeyArn, encryptedTagValue)
  }
}
