# CDK Base

This role includes boot tasks that the Guardian's EC2 CDK patterns and best 
practices rely on.

At the moment this means the following:
- fetch instance tags and store under `/etc/config` (via [`instance-tag-discovery`](https://github.com/guardian/instance-tag-discovery))
- ship `cloud-init-output` and application logs to a Kinesis stream (via [`devx-logs`](https://github.com/guardian/devx-logs))

## Ubuntu support

Ubuntu 24.04 and 26.04 use the official `noble` and `resolute` repositories,
respectively, and the `fluent-bit` package (5.1.2 was available for amd64 and
arm64 when support was added).
`devx-logs` detects the installed configuration layout, and the startup script
starts `fluent-bit.service` or `td-agent-bit.service` accordingly, after
`devx-logs` has written its configuration. No compatibility links are required.

Deploy the version of `devx-logs` that detects both layouts before baking images
with this role. Custom startup scripts on Ubuntu 24.04 and 26.04 must use
`fluent-bit.service` and `/etc/fluent-bit/fluent-bit.conf`.

Ubuntu 18.04, 20.04 and 22.04 retain the `td-agent-bit` package. Other Ubuntu
releases above 22.04 remain rejected until a repository is configured for them.

## Requirements

ℹ If you are using [`@guardian/cdk`](http://github.com/guardian/cdk) version 41.1.0 or greater, these requirements are met automatically.

### Kinesis
EC2 instances must have a `LogKinesisStreamName` tag.

We ship logs into the Central ELK stack via a Kinesis stream. `devx-logs` discovers this stream via the `LogKinesisStreamName` tag on the EC2 instance.

<details>
<summary>CFN YAML snippet</summary>

```yaml
Parameters:
  LoggingStreamName:
    Type: AWS::SSM::Parameter::Value<String>
    Description: SSM parameter containing the Name (not ARN) on the kinesis stream
    Default: /account/services/logging.stream.name
Resources:
  MyAutoScalingGroup:
    Type: AWS::AutoScaling::AutoScalingGroup
    Properties:
      Tags:
       - Key: LogKinesisStreamName
         Value: !Ref LoggingStreamName
         PropagateAtLaunch: true
```
</details>

### IAM permissions
EC2 instances must have the following IAM permissions:
- `kinesis:DescribeStream` scoped to the Kinesis stream above
- `kinesis:PutRecord` scoped to the Kinesis stream above
- `kinesis:PutRecords` scoped to the Kinesis stream above
- `ec2:DescribeTags`
- `ec2:DescribeInstances`

<details>
<summary>CFN YAML snippet</summary>

```yaml
Parameters:
  LoggingStreamName:
    Type: AWS::SSM::Parameter::Value<String>
    Description: SSM parameter containing the Name (not ARN) on the kinesis stream
    Default: /account/services/logging.stream.name
Resources:
  InstanceRole:
    Type: AWS::IAM::Role
    Properties:
      Path: /
      AssumeRolePolicyDocument:
        Statement:
        - Effect: Allow
          Principal:
            Service:
            - ec2.amazonaws.com
          Action:
          - sts:AssumeRole
  InstancePolicy:
    Type: AWS::IAM::Policy
    Properties:
      Roles:
      - !Ref InstanceRole
      PolicyDocument:
        Statement:
        - Effect: Allow
          Action:
          - kinesis:DescribeStream
          - kinesis:PutRecord
          - kinesis:PutRecords
          Resource: !Sub 'arn:aws:kinesis:${AWS::Region}:${AWS::AccountId}:stream/${LoggingStreamName}'
        - Effect: Allow
          Resource: '*'
          Action:
          - ec2:DescribeTags
          - ec2:DescribeInstances
  InstanceProfile:
    Type: AWS::IAM::InstanceProfile
    Properties:
      Path: /
      Roles:
      - !Ref InstanceRole
  MyAutoScalingGroup:
    Type: AWS::AutoScaling::AutoScalingGroup
    Properties:
      LaunchConfigurationName: !Ref 'MyLaunchConfig'
      Tags:
       - Key: LogKinesisStreamName
         Value: !Ref LoggingStreamName
  MyLaunchConfig:
    Type: AWS::AutoScaling::LaunchConfiguration
    Properties:
      IamInstanceProfile: !Ref 'InstanceProfile' 
```
</details>

_While not required, it is strongly recommended to [enable tag metadata on your
instance](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-ec2-launchtemplate-launchtemplatedata-metadataoptions.html#cfn-ec2-launchtemplate-launchtemplatedata-metadataoptions-instancemetadatatags)
as this allows tag lookup without requiring remote AWS API calls at runtime._

## Options

- `start_fluentbit`: boolean, default `true`

    Set this to `false` if you do not want log shipping to start automatically.
    This is useful if you will add or change the log shipping config supplied by
    `devx-logs`. If you have set this to `false` you are now responsible for
    starting the installed service in your user data script after all config
    files have been created: `systemctl start fluent-bit.service` on Ubuntu
    24.04/26.04, or `systemctl start td-agent-bit.service` on older supported
    releases. If you fail to do so logs will not be shipped.
