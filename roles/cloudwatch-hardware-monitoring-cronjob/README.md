# CloudWatch hardware monitoring cronjob

> [!WARNING]
> DEPRECATED.
> This role is uses IMDSv1 and therefore violates [FSBP EC2.8](https://docs.aws.amazon.com/securityhub/latest/userguide/ec2-controls.html#ec2-8).
> For that reason, it's considered to be deprecated.
> Please use [`aws-cloud-watch-agent`](../aws-cloud-watch-agent) instead.

Utilises [mon-put-instance-data.pl](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/mon-scripts.html) script to 
collect and report to CloudWatch memory, swap and disk space utilization data. 

This role creates metrics in the namespace `System/Linux`.

Requires the instance to have `cloudwatch:PutMetricData` (probably on resource `*`). 

Example params: `monitor_memory_utilisation: true, monitor_disk_space_utilisation: true, paths: [/, /data]`

## Gotchas

- if `monitor_disk_space_available` or `monitor_disk_space_utilisation` are set to `true` then at least one 
  path must be specified; else metrics won't be reported to CloudWatch
- ensure that the EC2 instance on which the script is running has the correct permissions; as an example, see 
  [this](https://github.com/guardian/deploy-tools-platform/pull/114) PR
- Be aware of differing CloudWatch metric namespaces when migrating to `aws-cloud-watch-agent`. 
  You may want to use a custom namespace, or update your alarms and dashboard to use the new namespace.
