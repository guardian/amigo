import "@aws-cdk/assert/jest";
import { SynthUtils } from "@aws-cdk/assert";
import { App } from "@aws-cdk/core";
import { AmigoStack } from "./amigo";

describe("The Amigo stack", () => {
  it("matches the snapshot", () => {
    const app = new App();
    const stack = new AmigoStack(app, "AMIgo", {
      stack: "deploy",
      migratedFromCloudFormation: true,
    });

    expect(SynthUtils.toCloudFormation(stack)).toMatchSnapshot();
  });
});
