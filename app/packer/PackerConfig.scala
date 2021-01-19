package packer

/**
 * Environment-specific configuration for running Packer
 */
case class PackerConfig(
  stage: String,
  vpcId: Option[String],
  subnetId: Option[String],
  instanceProfile: Option[String],
  securityGroupId: Option[String])

