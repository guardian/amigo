import { App } from "aws-cdk-lib";
import type { AmigoProps } from "../lib/amigo";
import { AmigoStack } from "../lib/amigo";
import { ImageCopierKMSKey } from "../lib/image-copier-kms";
import { ImageCopierLambda } from "../lib/image-copier-lambda";

const app = new App();

const stageAgnosticProps = {
  stack: "deploy",
  migratedFromCloudFormation: true,
};

const amigoCodeProps: AmigoProps = {
  ...stageAgnosticProps,
  stage: "CODE",
  domainName: "public.amigo.code.dev-gutools.co.uk",
};

new AmigoStack(app, "AMIgo-CODE", amigoCodeProps);

export const amigoProdProps: AmigoProps = {
  ...stageAgnosticProps,
  stage: "PROD",
  domainName: "public.amigo.gutools.co.uk",
};

new AmigoStack(app, "AMIgo-PROD", amigoProdProps);

// Below are the stacks for Image Copier. These are deployed as stacksets on a
// different cadence to Amigo itself.

new ImageCopierKMSKey(app, "kms-key-stack", {
  stack: "deploy",
  stage: "PROD",
  description: "AMIgo kms key creator",
});

new ImageCopierLambda(app, "lambda-stack", {
  stack: "deploy",
  stage: "PROD",
  description: "AMIgo image copier lambda",
});
