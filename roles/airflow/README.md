# Airflow

This installs airflow on the host.

An production recipe from the Data-tech Airflow instance can be found [here](https://amigo.gutools.co.uk/recipes/datatech-airflow).

## AMI configuration

### NFS mount

Airflow instances will be recycled upon AMI changes and/or other updates.

If you want your logs to survive such event, you will need to place your logs
into a shared NFS directory.

AWS provides such a service called [EFS](https://eu-west-1.console.aws.amazon.com/efs/home?region=eu-west-1). 

To enable an NFS/EFS mount point into your airflow instance, pass in the following:

```yaml
# NFS
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

```yaml
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

## Cloudformation

This airflow role is fairly opinionated in the sense it's been designed around [Riff Raff's autoscaling](https://riffraff.gutools.co.uk/docs/magenta-lib/types#autoscaling) deployment method.

As such, the airflow instance needs to be part of some ASG. 
The data-tech repository holds such an example of a [production stack](https://github.com/guardian/ophan-data-lake/blob/rg/airflow_readme/airflow/cloudformation/airflow.yaml).

Its [readme](https://github.com/guardian/ophan-data-lake/blob/rg/airflow_readme/airflow/README.md) also contains some information about Airflow's scheduler execution control.

## Airflow DAGs and connections

Upon (re-)deployment, systemd will try to get any DAGs and connections from S3. 

This role needs to be told where on S3 to downloads those assets from with:

```yaml
# S3 assets
airflow_s3_dags_folder: "ophan-dist/ophan-data-lake/PROD/airflow-assets/dags/"
airflow_s3_connections_folder: "ophan-dist/ophan-data-lake/PROD/airflow-assets/connections/"
airflow_s3_python_libs_folder: "ophan-dist/ophan-data-lake/PROD/airflow-assets/python_libs/"
```

* [airflow-dags-update.service](templates/airflow-dags-update.service.j2) drops DAGs in `{{ airflow_dags_folder }}` 
* [airflow-connections-update.service](templates/airflow-connections-update.service.j2) drops connections in `{{ airflow_connections_folder }}`
    and registers them by calling [airflow-update-connections.sh](templates/airflow-update-connections.sh.j2)
