import path from "path";
import type { CfnRole } from "@aws-cdk/aws-iam";
import { Effect, Policy, PolicyStatement, Role } from "@aws-cdk/aws-iam";
import type { Bucket } from "@aws-cdk/aws-s3";
import { CfnInclude } from "@aws-cdk/cloudformation-include";
import type { App } from "@aws-cdk/core";
import { Stage } from "@guardian/cdk/lib/constants";
import type { GuStackProps, GuStageParameter } from "@guardian/cdk/lib/constructs/core";
import { GuDistributionBucketParameter, GuStack } from "@guardian/cdk/lib/constructs/core";
import { AppIdentity } from "@guardian/cdk/lib/constructs/core/identity";
import {
  GuAllowPolicy,
  GuAnghammaradSenderPolicy,
  GuDescribeEC2Policy,
  GuGetDistributablePolicy,
  GuLogShippingPolicy,
  GuSSMRunCommandPolicy,
} from "@guardian/cdk/lib/constructs/iam";
import { GuS3Bucket } from "@guardian/cdk/lib/constructs/s3";

const yamlTemplateFilePath = path.join(__dirname, "../../cloudformation.yaml");

export class AmigoStack extends GuStack {
  private static app: AppIdentity = {
    app: "amigo",
  };

  private readonly dataBucket: Bucket;

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
      ],
    });
  }

  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    const importBucketName = this.withStageDependentValue({
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

    const yamlDefinedStack = new CfnInclude(this, "YamlTemplate", {
      templateFile: yamlTemplateFilePath,

      // These override like-named parameters in the YAML template.
      // TODO remove the parameter from the YAML template once each resource that uses it has been CDK-ified.
      parameters: {
        Stage: this.getParam<GuStageParameter>("Stage"), // TODO `GuStageParameter` could be a singleton to simplify this
        DistributionBucketName: GuDistributionBucketParameter.getInstance(this).valueAsString,
      },
    });

    // See https://docs.aws.amazon.com/cdk/latest/guide/use_cfn_template.html#use_cfn_template_cfninclude_access
    const cfnRootRole = yamlDefinedStack.getResource("RootRole") as CfnRole;
    const rootRole = Role.fromRoleArn(this, "RootRole", cfnRootRole.attrArn);

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
      new GuGetDistributablePolicy(this, AmigoStack.app),
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

    policiesToAttachToRootRole.forEach((policy) => policy.attachToRole(rootRole));

    /*
    Looks like some @guardian/cdk constructs are not applying the App tag.
    I suspect since https://github.com/guardian/cdk/pull/326.
    Until that is fixed, we can safely, manually apply it to all constructs in tree from `this` as it's a single app stack.
    TODO: remove this once @guardian/cdk has been fixed.
    */
    AppIdentity.taggedConstruct(AmigoStack.app, this);
  }
}
