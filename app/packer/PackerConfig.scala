package packer

/** Environment-specific configuration for running Packer
  */
case class PackerConfig(
    stage: String,
    vpcId: Option[String],
    subnetId: String,
    instanceProfile: Option[String],
    securityGroupId: Option[String]
)
