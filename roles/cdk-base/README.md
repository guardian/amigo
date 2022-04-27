CDK Base
========

**WARNING: this role is experimental and not recommended for Production use
yet.**

This role includes boot tasks that the Guardian's EC2 CDK patterns and best
practices rely on.

At the moment this means the following:

* fetch instance tags and store under /etc/config
* ship cloud-init logs to a Kinesis stream

We strongly recommend [enabling tag metadata on your
instance](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-launchtemplate-launchtemplatedata-metadataoptions.html#cfn-ec2-launchtemplate-launchtemplatedata-metadataoptions-instancemetadatatags)
as this does not require remote AWS API calls at runtime.

To ship logs, ensure your instance has the following tag:

    LogKinesisStreamName

set to the name of your logging Kinesis Stream.

For more information on behaviour, see
[instance-tag-discovery](https://github.com/guardian/instance-tag-discovery) and
[devx-logs](https://github.com/guardian/devx-logs).
