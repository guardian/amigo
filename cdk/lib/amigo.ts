import { InstanceClass, InstanceSize, InstanceType, Peer, Port } from "@aws-cdk/aws-ec2";
import { Effect, Policy, PolicyStatement } from "@aws-cdk/aws-iam";
import type { Bucket } from "@aws-cdk/aws-s3";
import type { App } from "@aws-cdk/core";
import { Duration } from "@aws-cdk/core";
import { AccessScope, GuPlayApp } from "@guardian/cdk";
import { Stage } from "@guardian/cdk/lib/constants";
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import { GuDistributionBucketParameter, GuStack, GuStringParameter } from "@guardian/cdk/lib/constructs/core";
import { AppIdentity } from "@guardian/cdk/lib/constructs/core/identity";
import { GuCname } from "@guardian/cdk/lib/constructs/dns";
import { GuSecurityGroup, GuVpc } from "@guardian/cdk/lib/constructs/ec2";
import {
  GuAllowPolicy,
  GuAnghammaradSenderPolicy,
  GuDescribeEC2Policy,
  GuLogShippingPolicy,
  GuSSMRunCommandPolicy,
} from "@guardian/cdk/lib/constructs/iam";
import { GuS3Bucket } from "@guardian/cdk/lib/constructs/s3";

const packerVersion = "1.6.6";
const domainNames = {
  [Stage.CODE]: { domainName: "amigo.code.dev-gutools.co.uk" },
  [Stage.PROD]: { domainName: "amigo.gutools.co.uk" },
};

export class AmigoStack extends GuStack {
  private static app: AppIdentity = {
    app: "amigo",
  };

  private readonly dataBucket: Bucket;
  private readonly packerInstanceProfile: GuStringParameter;

  private get appPolicy(): Policy {
    return new Policy(this, "AppPolicy", {
      policyName: "app-policy",
      statements: [
        /*
        Permissions to enable listing of installed packages created during a bake
        See https://github.com/guardian/amigo/pull/395
         */
        new PolicyStatement({
          effect: Effect.ALLOW,
          actions: ["s3:GetObject"],
          resources: [`${this.dataBucket.bucketArn}/*`],
        }),

        /*
        AMIgo uses DynamoDb as a data store.
        The permissions are quite wide, mainly because AMIgo creates tables as well as reading/writing data.
        See `app/data/Dynamo.scala`
         */
        new PolicyStatement({
          effect: Effect.ALLOW,
          actions: ["dynamodb:ListTables"],
          resources: ["*"],
        }),
        new PolicyStatement({
          effect: Effect.ALLOW,
          actions: ["dynamodb:*"],
          resources: [`arn:aws:dynamodb:*:*:table/amigo-${this.stage}-*`],
        }),

        /*
        Permissions to support encrypted bakes
        See https://github.com/guardian/amigo/pull/164
         */
        new PolicyStatement({
          effect: Effect.ALLOW,
          actions: ["sns:ListTopics"],
          resources: ["*"],
        }),

        /*
        Permissions to trigger AMI deletion
        See https://github.com/guardian/amigo/pull/193
         */
        new PolicyStatement({
          effect: Effect.ALLOW,
          actions: ["sns:*"],
          resources: [
            `arn:aws:sns:*:*:amigo-${this.stage}-notify`,
            `arn:aws:sns:*:*:amigo-${this.stage}-housekeeping-notify`,
          ],
        }),

        /*
        Allow us to allow other accounts to retrieve the ImageCopier lambda artifact
         */
        new PolicyStatement({
          effect: Effect.ALLOW,
          actions: ["s3:GetBucketPolicy", "s3:PutBucketPolicy"],
          resources: [`arn:aws:s3::*:${GuDistributionBucketParameter.getInstance(this).valueAsString}`],
        }),

        /*
        See https://github.com/guardian/amigo/pull/526
         */
        new PolicyStatement({
          effect: Effect.ALLOW,
          actions: ["iam:GetInstanceProfile"],
          resources: [this.packerInstanceProfile.valueAsString],
        }),
      ],
    });
  }

  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    this.packerInstanceProfile = new GuStringParameter(this, "PackerInstanceProfile", {
      description:
        "Instance profile given to instances created by Packer. Find this in the PackerUser-PackerRole in IAM",
    });

    const importBucketName = this.withStageDependentValue({
      app: AmigoStack.app.app,
      variableName: "DataBucketName",
      stageValues: {
        [Stage.CODE]: "amigo-data-code",
        [Stage.PROD]: "amigo-data-prod",
      },
    });

    this.dataBucket = new GuS3Bucket(this, "AmigoDataBucket", {
      bucketName: importBucketName,
      existingLogicalId: {
        logicalId: "AmigoDataBucket",
        reason: "To prevent orphaning of the YAML defined bucket",
      },
    });

    const ssmPolicy = GuSSMRunCommandPolicy.getInstance(this);

    // TODO Can GuSSMRunCommandPolicy expose its default `PolicyStatement` to easily add actions to it?
    ssmPolicy.addStatements(
      new PolicyStatement({
        effect: Effect.ALLOW,
        resources: ["*"],

        // required to allow Packer to run SSM commands
        // see https://github.com/guardian/amigo/pull/526 and https://github.com/guardian/amigo/pull/538
        actions: ["ssm:StartSession", "ssm:TerminateSession"],
      })
    );

    const policiesToAttachToRootRole: Policy[] = [
      ssmPolicy,
      GuLogShippingPolicy.getInstance(this),
      new GuAllowPolicy(this, "PackerPolicy", {
        policyName: "packer-required-permissions",
        resources: ["*"],
        actions: [
          "ec2:AttachVolume",
          "ec2:AuthorizeSecurityGroupIngress",
          "ec2:CopyImage",
          "ec2:CreateImage",
          "ec2:CreateKeypair",
          "ec2:CreateSecurityGroup",
          "ec2:CreateSnapshot",
          "ec2:CreateTags",
          "ec2:CreateVolume",
          "ec2:DeleteKeypair",
          "ec2:DeleteSecurityGroup",
          "ec2:DeleteSnapshot",
          "ec2:DeleteVolume",
          "ec2:DeregisterImage",
          "ec2:DescribeImageAttribute",
          "ec2:DescribeImages",
          "ec2:DescribeInstances",
          "ec2:DescribeRegions",
          "ec2:DescribeSecurityGroups",
          "ec2:DescribeSnapshots",
          "ec2:DescribeSubnets",
          "ec2:DescribeTags",
          "ec2:DescribeVolumes",
          "ec2:DetachVolume",
          "ec2:GetPasswordData",
          "ec2:ModifyImageAttribute",
          "ec2:ModifyInstanceAttribute",
          "ec2:ModifySnapshotAttribute",
          "ec2:RegisterImage",
          "ec2:RunInstances",
          "ec2:StopInstances",
          "ec2:TerminateInstances",
          "iam:PassRole",
        ],
      }),
      GuDescribeEC2Policy.getInstance(this),
      GuAnghammaradSenderPolicy.getInstance(this),
      this.appPolicy,
    ];

    new GuSecurityGroup(this, "PackerSecurityGroup", {
      ...AmigoStack.app,
      vpc: GuVpc.fromIdParameter(this, "vpc"),

      // The security group name is added to config.
      // See `app/packer/PackerConfig.scala`.
      securityGroupName: `amigo-packer-${this.stage}`,

      description: "Security group for instances created by Packer",

      // When true, `allowAllOutbound` will also allow all outbound UDP traffic
      allowAllOutbound: false,
      egresses: [
        {
          port: Port.tcpRange(0, 65535),
          range: Peer.anyIpv4(),
          description: "Allow all outbound TCP",
        },
      ],
      existingLogicalId: {
        logicalId: "PackerSecurityGroup",
        reason:
          "Keeping the same resource for simplicity. We would otherwise have to update the stack when there are no ongoing bakes, i.e. when the security group isn't in use.",
      },
    });

    const artifactPath = [
      GuDistributionBucketParameter.getInstance(this).valueAsString,
      this.stack,
      this.stage,
      AmigoStack.app.app,
      "amigo_1.0-latest_all.deb",
    ].join("/");

    const guPlayApp = new GuPlayApp(this, {
      ...AmigoStack.app,
      instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.SMALL),
      userData: [
        "#!/bin/bash -ev",
        `wget -P /tmp https://releases.hashicorp.com/packer/${packerVersion}/packer_1.6.6_linux_arm64.zip`,
        "mkdir /opt/packer",
        "unzip -d /opt/packer /tmp/packer_*_linux_arm64.zip",
        "echo 'export PATH=${!PATH}:/opt/packer' > /etc/profile.d/packer.sh",
        "wget -P /tmp https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_arm64/session-manager-plugin.deb",
        "dpkg -i /tmp/session-manager-plugin.deb",
        `aws --region eu-west-1 s3 cp s3://${artifactPath} /tmp/amigo.deb`,
        "dpkg -i /tmp/amigo.deb",
      ].join("\n"),
      access: {
        scope: AccessScope.RESTRICTED,
        cidrRanges: [Peer.ipv4("77.91.248.0/21")],
      },
      certificateProps: domainNames,
      scaling: {
        [Stage.CODE]: {
          minimumInstances: 1,
        },
        [Stage.PROD]: {
          minimumInstances: 1,
        },
      },
      monitoringConfiguration: {
        noMonitoring: true,
      },
      roleConfiguration: {
        additionalPolicies: policiesToAttachToRootRole,
      },
    });

    new GuCname(this, "AmigoCname", {
      app: AmigoStack.app.app,
      domainNameProps: domainNames,
      ttl: Duration.hours(1),
      resourceRecord: guPlayApp.loadBalancer.loadBalancerDnsName,
    });
    /*
    Looks like some @guardian/cdk constructs are not applying the App tag.
    I suspect since https://github.com/guardian/cdk/pull/326.
    Until that is fixed, we can safely, manually apply it to all constructs in tree from `this` as it's a single app stack.
    TODO: remove this once @guardian/cdk has been fixed.
    */
    AppIdentity.taggedConstruct(AmigoStack.app, this);
  }
}
