import { App } from "aws-cdk-lib";
import { Template } from "aws-cdk-lib/assertions";
import { ImageCopierKMSKey } from "./image-copier-kms";

describe("The kms key stack", () => {
  it("matches the snapshot", () => {
    const app = new App();
    const stack = new ImageCopierKMSKey(app, "IntegrationTest", {
      stack: "cdk",
      stage: "TEST",
    });
    const template = Template.fromStack(stack);
    expect(template.toJSON()).toMatchSnapshot();
  });
});
