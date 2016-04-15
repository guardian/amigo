package packer

/**
 * Environment-specific configuration for running Packer
 */
case class PackerConfig(
  vpcId: Option[String],
  subnetId: Option[String])

