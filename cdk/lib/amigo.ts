import { GuPlayApp } from "@guardian/cdk";
import { AccessScope } from "@guardian/cdk/lib/constants";
import type { AppIdentity, GuStackProps } from "@guardian/cdk/lib/constructs/core";
import { GuDistributionBucketParameter, GuStack, GuStringParameter } from "@guardian/cdk/lib/constructs/core";
import { GuCname } from "@guardian/cdk/lib/constructs/dns";
import { GuHttpsEgressSecurityGroup, GuSecurityGroup, GuVpc } from "@guardian/cdk/lib/constructs/ec2";
import {
  GuAllowPolicy,
  GuAnghammaradSenderPolicy,
  GuDescribeEC2Policy,
  GuLogShippingPolicy,
} from "@guardian/cdk/lib/constructs/iam";
import { GuS3Bucket } from "@guardian/cdk/lib/constructs/s3";
import { Duration, SecretValue } from "aws-cdk-lib";
import type { App } from "aws-cdk-lib";
import {InstanceClass, InstanceSize, InstanceType, Peer, Port, UserData} from "aws-cdk-lib/aws-ec2";
import { ListenerAction, UnauthenticatedAction } from "aws-cdk-lib/aws-elasticloadbalancingv2";
import { Effect, ManagedPolicy, Policy, PolicyStatement } from "aws-cdk-lib/aws-iam";
import type { Bucket } from "aws-cdk-lib/aws-s3";
import { ParameterDataType, ParameterTier, StringParameter } from "aws-cdk-lib/aws-ssm";

const packerVersion = "1.8.5";

export interface AmigoProps extends GuStackProps {
  domainName: string;
}

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

  constructor(scope: App, id: string, props: AmigoProps) {
    super(scope, id, props);

    const { domainName } = props;

    this.packerInstanceProfile = new GuStringParameter(this, "PackerInstanceProfile", {
      description:
        "Instance profile given to instances created by Packer. Find this in the PackerUser-PackerRole in IAM",
    });

    this.dataBucket = new GuS3Bucket(this, "AmigoDataBucket", {
      app: AmigoStack.app.app,
      bucketName: `amigo-data-${this.stage.toLowerCase()}`,
    });

    this.overrideLogicalId(this.dataBucket, {
      logicalId: "AmigoDataBucket",
      reason: "To prevent orphaning of the YAML defined bucket",
    });

    const ssmPolicy = new Policy(this, "SSMPolicy", {
      statements: [
        new PolicyStatement({
          effect: Effect.ALLOW,
          resources: ["*"],
          actions: [
            // Standard SSM permissions
            "ec2messages:AcknowledgeMessage",
            "ec2messages:DeleteMessage",
            "ec2messages:FailMessage",
            "ec2messages:GetEndpoint",
            "ec2messages:GetMessages",
            "ec2messages:SendReply",
            "ssm:UpdateInstanceInformation",
            "ssm:ListInstanceAssociations",
            "ssm:DescribeInstanceProperties",
            "ssm:DescribeDocumentParameters",
            "ssmmessages:CreateControlChannel",
            "ssmmessages:CreateDataChannel",
            "ssmmessages:OpenControlChannel",
            "ssmmessages:OpenDataChannel",

            // required to allow Packer to run SSM commands
            // see https://github.com/guardian/amigo/pull/526 and https://github.com/guardian/amigo/pull/538
            "ssm:StartSession",
            "ssm:TerminateSession",
          ],
        }),
      ],
    });

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

    const sg = new GuSecurityGroup(this, "PackerSecurityGroup", {
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
    });

    this.overrideLogicalId(sg, {
      logicalId: "PackerSecurityGroup",
      reason:
        "Keeping the same resource for simplicity. We would otherwise have to update the stack when there are no ongoing bakes, i.e. when the security group isn't in use.",
    });

    const distBucket = GuDistributionBucketParameter.getInstance(this).valueAsString;

    const artifactPath = [distBucket, this.stack, this.stage, AmigoStack.app.app, "amigo_1.0-latest_all.deb"].join("/");

    const guPlayApp = new GuPlayApp(this, {
      ...AmigoStack.app,
      instanceType: InstanceType.of(InstanceClass.T4G, InstanceSize.MEDIUM),
      userData: UserData.custom([
        "#!/bin/bash -ev",
        `wget -P /tmp https://releases.hashicorp.com/packer/${packerVersion}/packer_${packerVersion}_linux_arm64.zip`,
        "mkdir /opt/packer",
        "unzip -d /opt/packer /tmp/packer_*_linux_arm64.zip",
        "echo 'export PATH=${!PATH}:/opt/packer' > /etc/profile.d/packer.sh",
        "wget -P /tmp https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_arm64/session-manager-plugin.deb",
        "dpkg -i /tmp/session-manager-plugin.deb",

        "mkdir /amigo",
        `aws --region eu-west-1 s3 cp s3://${distBucket}/${this.stack}/${this.stage}/${AmigoStack.app.app}/conf/amigo-service-account-cert.json /amigo/`,

        `aws --region eu-west-1 s3 cp s3://${artifactPath} /tmp/amigo.deb`,
        "dpkg -i /tmp/amigo.deb",
      ].join("\n")),
      access: {
        scope: AccessScope.PUBLIC,
      },
      certificateProps: { domainName },
      scaling: { minimumInstances: 1 },
      monitoringConfiguration: {
        noMonitoring: true,
      },
      roleConfiguration: {
        additionalPolicies: policiesToAttachToRootRole,
      },
      applicationLogging: {
        enabled: true,
      },
    });

    // Ensure LB can egress to 443 (for Google endpoints) for OIDC flow.
    const albEgressSg = new GuHttpsEgressSecurityGroup(this, "IdP-access", {
      app: AmigoStack.app.app,
      vpc: guPlayApp.vpc,
    });

    guPlayApp.loadBalancer.addSecurityGroup(albEgressSg);

    // This parameter is used by https://github.com/guardian/waf
    new StringParameter(this, "AlbSsmParam", {
      parameterName: `/infosec/waf/services/${this.stage}/amigo-alb-arn`,
      description: `The arn of the ALB for amigo-${this.stage}. N.B. this parameter is created via cdk`,
      simpleName: false,
      stringValue: guPlayApp.loadBalancer.loadBalancerArn,
      tier: ParameterTier.STANDARD,
      dataType: ParameterDataType.TEXT,
    });

    const clientId = new GuStringParameter(this, "ClientId", {
      description: "Google OAuth client ID",
    });

    guPlayApp.listener.addAction("Google Auth", {
      action: ListenerAction.authenticateOidc({
        authorizationEndpoint: "https://accounts.google.com/o/oauth2/v2/auth",
        issuer: "https://accounts.google.com",
        scope: "openid",
        authenticationRequestExtraParams: { hd: "guardian.co.uk" },
        onUnauthenticatedRequest: UnauthenticatedAction.AUTHENTICATE,

        tokenEndpoint: "https://oauth2.googleapis.com/token",

        userInfoEndpoint: "https://openidconnect.googleapis.com/v1/userinfo",
        clientId: clientId.valueAsString,
        clientSecret: SecretValue.secretsManager(`/${this.stage}/deploy/amigo/clientSecret`),
        next: ListenerAction.forward([guPlayApp.targetGroup]),
      }),
    });

    new GuCname(this, "DnsRecord", {
      app: AmigoStack.app.app,
      domainName: domainName,
      ttl: Duration.hours(1),
      resourceRecord: guPlayApp.loadBalancer.loadBalancerDnsName,
    });
  }
}
