import type { App } from "@aws-cdk/core";
import type { GuStackProps } from "@guardian/cdk/lib/constructs/core";
import { GuStack } from "@guardian/cdk/lib/constructs/core";

export class AmigoStack extends GuStack {
  constructor(scope: App, id: string, props: GuStackProps) {
    super(scope, id, { ...props, description: "AMIgo, an AMI bakery" });
  }
}
