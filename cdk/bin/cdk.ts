import { App } from "@aws-cdk/core";
import { AmigoStack } from "../lib/amigo";

const stackName = process.env.GU_CDK_STACK_NAME;

const app = new App();

new AmigoStack(app, "AMIgo", {
  stack: "deploy",
  migratedFromCloudFormation: true,
  stackName,
});
