# AMIgo

AMIgo is an application for baking AMIs (Amazon Machine Images).
For information on how to use Amigo baked AMIs with Riffraff check [here](./docs/riffraff-integration.md)

## Terminology

* A __base image__ is the source AMI to use as the basis for an image. For example you might have an "Ubuntu Wily" base image.

* A __role__ is something installed or configured on the machine. For example if you want your machine to have a JVM, Node and nginx pre-installed, you would assign the corresponding roles to your image. Currently roles are implemented as Ansible roles.

* A __recipe__ is a description of how to bake your AMI. Making a recipe consists of choosing a base image and deciding which roles to assign. For example you might have a recipe that builds an image based on Ubuntu Wily and installs a JVM, Node and nginx.

* A __bake__ is a single execution of a recipe. The result of a bake is an AMI.

## Implementation

AMIgo is implemented as a Play application. It uses Packer and Ansible to bake AMIs.

### AMI baking process

Roughly, AMIgo does the following:

1. Dynamically generate an Ansible playbook based on the recipe's roles
2. Dynamically generate a Packer build configuration file to install and then run Ansible
3. Execute Packer as an external process
4. Parse the Packer output and extract useful information from it

All data (base images, recipes, bakes, bake logs) are stored in DynamoDB. The Dynamo tables are created automatically if they do not exist.

## Debugging recipes

When running in an environment that is not `PROD` there is an option to `Bake with debug enabled`.
This passes the `-debug` flag through to packer which saves a copy of the SSH key in AMIgos working directory. This makes 
it possible to SSH onto the instance that is being used to build the AMI. 

## How to run locally

(For a faster but messier way of testing your ansible scripts - see 'Testing ansible scripts without runing amigo/packer' below.)

AMIgo requires Packer to be [installed](https://www.packer.io/intro/getting-started/install.html)

To run the Play app, you will need credentials in either the `deployTools` profile or the default profile.

If you want to actually perform a bake, you will need separate credentials for Packer. These must be available either as environment variables or in the default profile. (Packer doesn't play nicely with named profiles.) I'm not sure whether Packer understands federated credentials, session token, etc. I created an IAM user with limited permissions (see below) and use that user's credentials.

If you have created a custom VPC in your AWS account (i.e. your account contains any VPCs other than the default one), then you will also need to tell Packer which VPC and subnet to use when building images:

```shell
$ cat ~/.configuration-magic/amigo.conf

packer {
  vpcId = "vpc-1234abcd"
  subnetId = "subnet-5678efgh"
  instanceProfile = "[optional] instance profile name for the box packer will run on"
}
```

If you want to use the `packages` role to install packages from an S3 bucket then you'll also need to configure that:

```hocon
ansible {
  packages {
    s3bucket = "your-bucket"
    s3prefix = "an/optional/prefix/"
  }
}
```

Optionally, you may want to set `associate_public_ip_address` to true if your subnet does not default to this, to ensure Packer can SSH into your instance.

Once you have your credentials and config sorted out, just do: 

```shell
$ sbt run
```

## How to run the tests

```shell
$ sbt test
```

## Required AWS permissions for Packer

```json5
{
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "ec2:AttachVolume",
                "ec2:CreateVolume",
                "ec2:DeleteVolume",
                "ec2:CreateKeypair",
                "ec2:DeleteKeypair",
                "ec2:DescribeSubnets",
                "ec2:CreateSecurityGroup",
                "ec2:DeleteSecurityGroup",
                "ec2:AuthorizeSecurityGroupIngress",
                "ec2:CreateImage",
                "ec2:CopyImage",
                "ec2:RunInstances",
                "ec2:TerminateInstances",
                "ec2:StopInstances",
                "ec2:DescribeVolumes",
                "ec2:DetachVolume",
                "ec2:DescribeInstances",
                "ec2:CreateSnapshot",
                "ec2:DeleteSnapshot",
                "ec2:DescribeSnapshots",
                "ec2:DescribeImages",
                "ec2:RegisterImage",
                "ec2:CreateTags",
                "ec2:ModifyImageAttribute",
                "iam:*",
                "elasticloadbalancing:*"
            ],
            "Resource": "*"
        }
    ]
}
```

## Testing ansible scripts without runing amigo/packer

Tired of waiting for amigo to build, deploy and bake only to discover you made a one character error in your ansible script?
Then read on...

You can use [Vagrant](https://www.vagrantup.com/downloads.html) to test ansible scripts. Once set up, it allows you to try out your script with a feedback loop
of 20 seconds or so. There are some docs [here](https://docs.ansible.com/ansible/2.7/scenario_guides/guide_vagrant.html) 
covering this, but, roughly speaking you need to:

### Pre-requisites

1. [Vagrant](https://www.vagrantup.com/downloads.html)
1. [Virtualbox](https://www.virtualbox.org/wiki/Downloads)

### Running Ansible roles

1. `cd` into `roles/`
1. Create a `Vagrantfile` in this directory. Note location of .playbook and .extra_vars files (feel free to change):
 
    ```ruby
    Vagrant.configure(2) do |config|
    
      config.vm.box = "ubuntu/bionic64"
      #config.vm.box = "centos/7"
    
      config.ssh.insert_key = false
    
      # For some reason when tried, is sometimes needed with redhat images. Ubuntu and centos seem fine 
      #config.vm.synced_folder ".", "/vagrant"
    
      config.vm.provider "virtualbox" do |v|  
       v.customize ["modifyvm", :id, "--natdnshostresolver1", "on"]
       v.customize ["modifyvm", :id, "--natdnsproxy1", "on"]
      end  
    
      config.vm.provision "ansible_local" do |ansible|
        ansible.install_mode = "pip" # Ubuntu is fine without that. Redhat prefers it.
        ansible.verbose = "v" # or "vv", "vvv", "vvvv"
        ansible.playbook = "vagrant/playbook.yaml"
        ansible.extra_vars = "@vagrant/extra-vars.yaml"
      end
    end
    ```
1. Create a `playbook.yaml` file (using [airflow](roles/airflow/) role as an example):
    ```yaml
    ---
    - name: Airflow
      hosts: all
      connection: local
      become: true
      roles:
        - role: aws-efs
        - role: airflow
        - role: ...
    ```
1. Create a `extra-vars.yaml` file:
    ```yaml
    ---
    
    nfs_mount_enabled: True
    nfs_mount_id: localhost
    airflow_executor: SequentialExecutor
    airflow_s3_dags_folder: "ophan-dist/ophan-data-lake/PROD/airflow-assets/dags/"
    airflow_s3_connections_folder: "ophan-dist/ophan-data-lake/PROD/airflow-assets/connections/"
    airflow_s3_python_libs_folder: "ophan-dist/ophan-data-lake/PROD/airflow-assets/python_libs/"
    
    # whatever other concrete values you may need. 
    ```
1. Run `vagrant up` to download the image and run your ansible script
1. Run `vagrant provision` to re-run the ansible script
 
1. If you get an error about python not being set up properly, a hacky workaround is to install it as a pre task:
    ```yaml
    ---
    - name: Airflow
      hosts: all
      connection: local
      become: true
      pre_tasks:
        - name: 'install python2'
          raw: sudo apt-get -y install python
      roles:
        - role: aws-efs
        - role: airflow
        - role: ...
    ```
