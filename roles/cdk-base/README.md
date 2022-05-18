# CDK Base

**Note: this role is experimental. It is safe* to use but the precise behaviour
and required tags are still subject to change.**

This role includes boot tasks that the Guardian's EC2 CDK patterns and best
practices rely on.

At the moment this means the following:

- fetch instance tags and store under /etc/config
- ship cloud-init logs to a Kinesis stream

To ship logs, ensure your instance has the following tag:

    LogKinesisStreamName

set to the name of your logging Kinesis stream.

Also ensure your instances have permissions, scoped to the same Kinesis stream:

    kinesis:DescribeStream
    kinesis:PutRecord

If you are using @guardian/cdk version 41.1.0 or greater the required tag and
permissions are automatically added.

_While not required, it is strongly recommended to [enable tag metadata on your
instance](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-launchtemplate-launchtemplatedata-metadataoptions.html#cfn-ec2-launchtemplate-launchtemplatedata-metadataoptions-instancemetadatatags)
as this allows tag lookup without requiring remote AWS API calls at runtime._

For more information on behaviour, see
[instance-tag-discovery](https://github.com/guardian/instance-tag-discovery) and
[devx-logs](https://github.com/guardian/devx-logs).

\* **safe** here means you can add this role to a recipe without the tags being set on all instances using that recipe, it may however add a few seconds to startup time as the script(s) will still run on the instance at startup.
