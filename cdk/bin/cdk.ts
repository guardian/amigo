import { App } from "@aws-cdk/core";
import { AmigoStack } from "../lib/amigo";

const app = new App();

new AmigoStack(app, "AMIgo", {
  stack: "deploy",
  migratedFromCloudFormation: true,
});
