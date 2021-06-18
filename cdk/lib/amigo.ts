import path from "path";
import type { CfnRole } from "@aws-cdk/aws-iam";
import { Effect, PolicyStatement, Role } from "@aws-cdk/aws-iam";
import { CfnInclude } from "@aws-cdk/cloudformation-include";
import type { App } from "@aws-cdk/core";
import type { GuStackProps, GuStageParameter } from "@guardian/cdk/lib/constructs/core";
import { GuStack } from "@guardian/cdk/lib/constructs/core";
import { GuSSMRunCommandPolicy } from "@guardian/cdk/lib/constructs/iam";

const yamlTemplateFilePath = path.join(__dirname, "../../cloudformation.yaml");

export class AmigoStack extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    const yamlDefinedStack = new CfnInclude(this, "YamlTemplate", {
      templateFile: yamlTemplateFilePath,
      parameters: {
        Stage: this.getParam<GuStageParameter>("Stage"), // TODO `GuStageParameter` could be a singleton to simplify this
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
    ssmPolicy.attachToRole(rootRole);
  }
}
