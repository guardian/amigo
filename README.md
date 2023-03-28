# AMIgo

AMIgo is an application for baking AMIs (Amazon Machine Images).
For information on how to use Amigo baked AMIs with Riffraff check [here](./docs/riffraff-integration.md).
This project is built in [teamcity](https://teamcity.gutools.co.uk/buildConfiguration/Tools_Amigo).

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

### Testing ansible scripts without running amigo/packer

*Warning: Multipass seems to struggle if running at the same time as the VPN. We
recommend not running the VPN when using Multipass locally.*

Amigo roles are simply Ansible scripts and can be run independently of Amigo
itself. This is often a lot easier than running Amigo itself.

To test roles locally, run:

    $ multipass/run.sh

This will install [Multipass](https://multipass.run/), a Canonical tool to
manage Ubuntu VMs, and execute Ansible roles within it.

If you want to run commands/debug directly in the VM then (post installing
things via run.sh), run:

    $ multipass shell amigo-test

If the Multipass VM is timing out, try deleting and then re-running the script:

    $ multipass stop amigo-test
    $ multipass delete amigo-test
    $ multipass purge amigo-test

You should also disconnect from the VPN too if using it.

### Running the full app

Load the `deployTools` credentials using Janus, then execute [`./script/server`](./script/server). This will run the 
Amigo app locally and the associated packer process should have the sufficient AWS authorization.

Note that you must use Java 11 to run this app. There are a few options for switching between Java versions at the
time of writing:

* [Coursier](https://get-coursier.io/docs/cli-java)
* [asdf](https://asdf-vm.com/)
* [sdkman](https://sdkman.io/usage)

<details>
<summary>Previous run locally advice</summary>

Install dependencies with [`./script/setup`](./script/setup)

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
</details>

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
