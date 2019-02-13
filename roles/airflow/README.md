# Airflow

This installs airflow on the host.

## AMI configuration

### NFS mount

Airflow instances will be recycled upon AMI changes and/or other updates.

If you want your logs to survive such event, you will need to place your logs
into a shared NFS directory.

AWS provides such a service called [EFS](https://eu-west-1.console.aws.amazon.com/efs/home?region=eu-west-1). 

To enable an NFS/EFS mount point into your airflw instance, pass in the following:

```
nfs_mount_enabled: True
nfs_mount_id: fs-123456
nfs_mount_point: /somewhere # optional, defaults to /mnt/nfs
```

to the airflow role.

## Airflow configuration

Airflow will be run via `systemd`.

`Systemd` will also load an environment file, whose location is defined
in `{ airflow_environment_file_folder }}/airflow`. By default this is

```
/etc/sysconfig/airflow
``` 

Now, Airflow allows for its configuration values to be passed via environment variables, as defined in its 
[documentation](https://airflow.readthedocs.io/en/stable/howto/set-config.html).

Therefore, in the `UserData` of your cloudformation template, append the `key`/`value` pairs you may need,
as in:

```
  LaunchConfig:
    Type: AWS::AutoScaling::LaunchConfiguration
    Properties:
      ImageId: <Airflow AMI>
      [...]
      UserData:
        Fn::Base64: !Sub |
          #!/bin/bash

          # Upgrade SSM agent
          rpm -U https://s3.us-west-1.amazonaws.com/amazon-ssm-us-west-1/latest/linux_amd64/amazon-ssm-agent.rpm

          cat AIRFLOW__CORE__SQL_ALCHEMY_CONN=my_conn_string >> /etc/sysconfig/airflow
          
          [...]
      InstanceType: 'm5.large'
    DependsOn:
      - ...
      [...]

```
