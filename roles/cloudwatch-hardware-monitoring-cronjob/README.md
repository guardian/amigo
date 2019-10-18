# CloudWatch hardware monitoring cronjob

Utilises [mon-put-instance-data.pl](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/mon-scripts.html) script to 
collect and report to CloudWatch memory, swap and disk space utilization data. 

## Gotchas

- if `monitor_disk_space_available` or `monitor_disk_space_utilisation` are set to `true` then at least one 
  path must be specified; else metrics won't be reported to CloudWatch
- ensure that the EC2 instance on which the script is running has the correct permissions; as an example, see 
  [this](https://github.com/guardian/deploy-tools-platform/pull/114) PR