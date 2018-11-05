This role will install the [Amazon Kinesis Agent](https://github.com/awslabs/amazon-kinesis-agent)

It also provide a utility script to configure your logs in `/opt/aws-kinesis-agent/configure-aws-kinesis-agent`
The arguments are:
 - the AWS region
 - the AWS Kinesis Stream
 - the AWS Kinesis pattern to match for the log file(s)


Here's an example user data that makes use of it:

```yaml
UserData:
"Fn::Base64":
  !Sub
    - |
      #!/bin/bash -ev
      /opt/aws-kinesis-agent/configure-aws-kinesis-agent ${AWS::Region} ${LogStream} ${LogFilePattern}
```

You will need to give the following policies to your instance:

```yaml
- Effect: Allow
  Action:
  - kinesis:PutRecord
  - kinesis:DescribeStream
  Resource:
   !Sub 
     - arn:aws:kinesis:${AWS::Region}:${AWS::AccountId}:stream/${LogStream}
     - App: !FindInMap [ Constants, App, Value ]
       Stack: !FindInMap [ Constants, Stack, Value ]
```

