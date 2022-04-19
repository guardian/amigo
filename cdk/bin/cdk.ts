import { App } from "aws-cdk-lib";
import type { AmigoProps } from "../lib/amigo";
import { AmigoStack } from "../lib/amigo";

const app = new App();

const stageAgnosticProps = {
  stack: "deploy",
  migratedFromCloudFormation: true,
};

const amigoCodeProps: AmigoProps = {
  ...stageAgnosticProps,
  stage: "CODE",
  domainName: "amigo.code.dev-gutools.co.uk",
};

new AmigoStack(app, "AMIgo-CODE", amigoCodeProps);

export const amigoProdProps: AmigoProps = {
  ...stageAgnosticProps,
  stage: "PROD",
  domainName: "amigo.gutools.co.uk",
};

new AmigoStack(app, "AMIgo-PROD", amigoProdProps);
