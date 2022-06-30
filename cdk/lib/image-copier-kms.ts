import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import { GuStack } from "@guardian/cdk/lib/constructs/core";
import type { App } from "aws-cdk-lib";
import { CfnOutput, RemovalPolicy } from "aws-cdk-lib";
import { ArnPrincipal, Effect, PolicyDocument, PolicyStatement } from "aws-cdk-lib/aws-iam";
import { Key } from "aws-cdk-lib/aws-kms";

export class ImageCopierKMSKey extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    const key = new Key(this, "KmsKey", {
      removalPolicy: RemovalPolicy.RETAIN,
      enableKeyRotation: true,
      policy: new PolicyDocument({
        statements: [
          new PolicyStatement({
            sid: "Enable IAM User Permissions",
            effect: Effect.ALLOW,
            principals: [new ArnPrincipal(`arn:aws:iam::${this.account}:root`)],
            actions: ["kms:*"],
            resources: ["*"],
          }),
          new PolicyStatement({
            sid: "Allow use of the key",
            effect: Effect.ALLOW,
            principals: [
              new ArnPrincipal(
                `arn:aws:iam::${this.account}:role/aws-service-role/autoscaling.amazonaws.com/AWSServiceRoleForAutoScaling`
              ),
            ],
            actions: [
              "kms:Encrypt",
              "kms:Decrypt",
              "kms:ReEncrypt*",
              "kms:GenerateDataKey*",
              "kms:DescribeKey",
              "kms:CreateGrant",
            ],
            resources: ["*"],
          }),
          new PolicyStatement({
            sid: "Allow attachment of persistent resources",
            effect: Effect.ALLOW,
            principals: [
              new ArnPrincipal(
                `arn:aws:iam::${this.account}:role/aws-service-role/autoscaling.amazonaws.com/AWSServiceRoleForAutoScaling`
              ),
            ],
            actions: ["kms:CreateGrant"],
            resources: ["*"],
            conditions: {
              Bool: { "kms:GrantIsForAWSResource": true },
            },
          }),
        ],
      }),
    });

    this.overrideLogicalId(key, {
      reason: "to preserve key",
      logicalId: "KmsKey",
    });

    new CfnOutput(this, "AmigoImageCopierKey", {
      description: "Amigo image copier key arn",
      value: key.keyArn,
      exportName: "amigo-imagecopier-key",
    });
  }
}
