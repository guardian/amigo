import { App } from "@aws-cdk/core";
import { AmigoStack } from "../lib/amigo";

const cloudFormationStackName = process.env.GU_CFN_STACK_NAME;

const app = new App();

new AmigoStack(app, "AMIgo", {
  stack: "deploy",
  migratedFromCloudFormation: true,
  cloudFormationStackName,
});
