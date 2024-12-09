# AWS CloudWatch Agent

This role installs the [AWS Cloud Watch agent](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/Install-CloudWatch-Agent.html).
It is available for Ubuntu Linux running on AMD64 or ARM64 architectures.

It does not configure or run the agent.
Both of these actions should be performed in the [User Data](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-as-launchconfig.html#cfn-as-launchconfig-userdata) made available to EC2 instances.

By default, this role will create metrics in the namespace `CWAgent`.
It can be customised in [configuration](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Agent-Configuration-File-Details.html).

The AWS documentation on CloudWatch Agent is fairly comprehensive, but scattered.
For convenience, some relevant resources are listed below:

- Creating the Cloud Watch configuration file:
    - [Manually](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Agent-Configuration-File-Details.html)
    - [Using the wizard](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/create-cloudwatch-agent-configuration-file-wizard.html)
- [Running the Cloud Watch agent](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Agent-common-scenarios.html)

## Example configuration
The following configuration will collect instance memory metrics. The metrics can be aggregated at the ASG level.

```json
{
  "metrics": {
    "append_dimensions": {
      "InstanceId": "${aws:InstanceId}",
      "AutoScalingGroupName":"${aws:AutoScalingGroupName}"
    },
    "aggregation_dimensions": [
      ["AutoScalingGroupName"],
      []
    ],
    "metrics_collected": {
      "mem": {
        "measurement": [
          "available",
          "total",
          "used"
        ]
      }
    }
  }
}
```

With this being the contents of the file `/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json` on an EC2 instance, we can add the following in the UserData to configure and start the agent:

```bash
amazon-cloudwatch-agent-ctl -a fetch-config -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
amazon-cloudwatch-agent-ctl -a start
```

Some example PRs:
- https://github.com/guardian/discussion-modtools/pull/866
- https://github.com/guardian/deploy-tools-platform/pull/843
