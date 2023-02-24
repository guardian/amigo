This role uses the [unified cloudwatch agent](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/UseCloudWatchUnifiedAgent.html) for logging.

It also provide a utility script to configure your logs in `/opt/cloudwatch-logs/configure-logs`
The arguments are:
- an id, to distinguish between log file (for instance `application` and `gc`)
- the stack
- the stage
- the app
- the full path to the log
- an optional date format, see "datetime_format" [here](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/AgentReference.html)


Here's an example user data that makes use of it:

```yaml
UserData:
"Fn::Base64":
  !Sub
    - |
      #!/bin/bash -ev
      aws --region ${AWS::Region} s3 cp s3://mobile-dist/${Stack}/${Stage}/${App}/${App}_1.0-latest_all.deb /tmp
      dpkg -i /tmp/${App}_1.0-latest_all.deb
      /opt/cloudwatch-logs/configure-logs application ${Stack} ${Stage} ${App} /var/log/${App}/application.log
    - App: !FindInMap [ Constants, App, Value ]
      Stack: !FindInMap [ Constants, Stack, Value ]
```

You will need to give the following policies to your instance:

```json
{
"Version": "2012-10-17",
"Statement": [
  {
    "Effect": "Allow",
    "Action": [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
      "logs:DescribeLogStreams"
    ],
    "Resource": [
      "arn:aws:logs:*:*:*"
    ]
  }
 ]
}```

