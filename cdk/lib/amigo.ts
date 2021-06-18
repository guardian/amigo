import path from "path";
import { CfnInclude } from "@aws-cdk/cloudformation-include";
import type { App } from "@aws-cdk/core";
import type { GuStackProps, GuStageParameter } from "@guardian/cdk/lib/constructs/core";
import { GuStack } from "@guardian/cdk/lib/constructs/core";

const yamlTemplateFilePath = path.join(__dirname, "../../cloudformation.yaml");

export class AmigoStack extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, props);

    new CfnInclude(this, "YamlTemplate", {
      templateFile: yamlTemplateFilePath,
      parameters: {
        Stage: this.getParam<GuStageParameter>("Stage"), // TODO `GuStageParameter` could be a singleton to simplify this
      },
    });
  }
}
