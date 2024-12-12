# Image Copier

Image Copier copies new AMIs into target accounts and encrypts them ready for
use as well as performing some housekeeping - deletion of older AMIs.
Cloudformation stacks are defined in the root Amigo CDK directory and deployed
as stack sets.

Note, there are better ways to achieve what Image Copier does. For possible
approaches see:

https://docs.google.com/document/d/1p0cSup1LmgrqDsnN3HGpk9C10ZDJyTbk4InjWHlhJ_w/edit?usp=sharing

## Testing on CODE

First see [Deploying](#deploying) to get your changes onto CODE.

There are two image copier lambdas to test:

1. Image Copier (handles copying/encrypting AMIs to target accounts)
2. Housekeeper (handles deletion of redundant AMIs in target accounts)

These live in a single Cloudformation stack, and a CODE version of this stack
exists in the Deploy Tools account.

To test these, create an AMI in Amigo CODE and ensure that it has
`Request encrypted copy` set to the Account ID of the Deploy Tools account. Then
bake it and check that the encryption (via the Image Copier lambda) works. After
some time, the Housekeeping lambda should run to delete this AMI provided
nothing is using it.

At the time of writing, a `arm64-jammy-java11-deploy-infrastructure` recipe
exists in Amigo CODE which is suitable for testing things so use that if you
don't want to create your own recipe - simply force a new bake.

## Deploying

Image Copier has a hybrid and, some would say, not great deployment strategy.
There are two parts:

### 1. Updating the Lambda zip file in S3

If your change involves modifications to the lambda code itself (i.e. anything
under `/src` in this directory) you will need to upload the new artifact to S3.
This is done as part of the main `tools::amigo` RiffRaff build.

**YOU MUST RENAME THE ARTIFACT:** the lambda will not update (even if the file
uploads to S3) unless the name is also changed. To do this bump the
`ImageCopierLambdaProps.version`, which is set in `bin/cdk.ts`.

**For PROD:** no action required; `tools::amigo` deploys to PROD automatically
whenever changes are merged into `main`.

**For CODE:** deploy the `tools::amigo` project to `CODE` in RiffRaff.

### 2. Updating the Cloudformation stackset

This is currently a manual process.

Firstly, synth the Cloudformation stack:

    $ git pull main
    $ cd cdk
    $ yarn synth

This should generate the following file:

    cdk/cdk.out/imagecopier-lambda-stack.template.json

**For CODE:** CODE is NOT stacksetted so simply update the
`AMIgo-ImageCopier-CODE` stack with this new template. Then consider
[Testing on CODE](#testing-on-code).

**For PROD:** deploy the PROD stackset from the Root account. Note, you can use
the following CLI command to check out which accounts/Org Units have been
targeted in the past so that you can target them again:

    aws cloudformation list-stack-instances --stack-set-name AmigoImageCopier --profile root --region eu-west-1 | jq -r '.Summaries[].Account' | sort
