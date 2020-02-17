This role installs the [AWS Cloud Watch agent](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Install-CloudWatch-Agent.html).

Currently the role does not assume anything about how the agent should be configured, nor does the role run the agent.
Typically both of these actions would be performed in the [User Data](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-as-launchconfig.html#cfn-as-launchconfig-userdata)
made available to EC2 instances.

At the moment, the role is only available for Ubuntu Linux running on AMD64 architecture, though this can be expanded
as and when needed; for example by following the pattern in the `aws-tools` role.

The AWS documentation on Cloud Watch agent is fairly comprehensive, but scattered; for convenience, some relevant
resources are listed below:

- creating the Cloud Watch configuration file:
    - [manually](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Agent-Configuration-File-Details.html)
    - [using the wizard](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/create-cloudwatch-agent-configuration-file-wizard.html)
- [running the Cloud Watch agent](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Agent-common-scenarios.html)
 