## Purpose

Assess AMIgo's migration from its current EC2-hosted Play application to ECS
Managed Instances. This assessment proposes no infrastructure or application
changes. The migration must keep the existing EC2 application available during
weighted traffic shifting, and must retain the separate EC2 instances that
Packer launches to bake AMIs.

## Base Branch and Dependencies

Base branch: `ec2-to-ecs-00-add-target-group-type-to-response-headers`.
This step follows the optional target-response-header step.

The assessment uses repository configuration and user-provided information.
No AWS account or deployment state was queried.

## Proposed Changes

This step records the current state and migration risks:

- AMIgo is a Scala/Play application. `cdk/lib/amigo.ts` provisions it with
  `GuPlayApp`; `cdk/package.json` pins `@guardian/cdk` to `63.6.2`, below the
  migration skill's current minimum of `66.0.0`. There is no ECS service or
  `GuLoadBalancedAppExperimental.ecsProps` configuration. Upversioning and
  preparing that app pattern must be a separate step, preserving the current
  EC2 configuration and CDK snapshots.
- Authentication currently uses Google OIDC at the ALB and Google
  authentication/group checks in the application. The migration requirement
  calls for Cognito when authentication is used; a design must preserve the
  Guardian-domain and group restrictions while changing the authentication
  integration.
- The application role currently combines normal app access with Packer
  permissions. AMIgo launches, monitors, and terminates EC2 bake instances;
  it also uses DynamoDB, S3, SNS, SSM Parameter Store, STS, Prism, and
  Anghammarad. ECS needs a task role that supports these runtime operations,
  including Packer's EC2 launch and `iam:PassRole` needs, without relying on
  EC2 instance-profile credentials.
- `AppLoader` explicitly uses profile and instance-profile credential
  providers. Step 04 adds the ECS container credentials provider while
  retaining local profiles and the EC2 instance-profile fallback. ECS task-role
  behavior still requires deployment verification.
- Packer is an in-process external executable (`1.13.1`) with plugins and the
  Session Manager plugin installed by EC2 user data. It writes generated
  playbooks/configuration and a cache under the system temporary directory.
  The ECS image needs the correct ARM64 binaries, network access to the
  Packer builder instances, and writable `/tmp` scratch space compatible with
  a read-only container filesystem.
- The current EC2 host is `t4g.medium`; user data installs ARM64 Packer and
  Session Manager artifacts. The image and CI publishing architecture must
  therefore be chosen explicitly; the present GitHub runner is `ubuntu-latest`.
- Step 04 removes production application and JVM GC file logging; both now
  write to stdout. Confirm the EC2 stdout collection path before merging, and
  validate the app with a read-only root filesystem plus writable `/tmp` before
  ECS traffic is enabled.
- Dynamic configuration currently comes from SSM through
  `ConfigurationLoader`/`SSMConfigurationLocation`. Separately, EC2 user data
  downloads `amigo-service-account-cert.json` from the distribution bucket to
  `/amigo`. These do not meet the single-S3-prefix/single-filesystem-location
  requirement; the config, certificate, and remaining SSM-backed settings need
  an explicit consolidation plan. Secrets should remain appropriately managed
  as secrets.
- Every app process starts Quartz scheduled-bake and housekeeping jobs.
  `amigo.scheduledBakes.enabled` only gates scheduled bake execution; it does
  not disable housekeeping. Running EC2 and ECS concurrently, or scaling to
  multiple tasks, can duplicate jobs against shared data and bake instances.
  A single-owner/leader strategy or equivalent task-specific scheduling plan
  is required before parallel operation.
- CI already runs CDK lint/tests/synthesis and SBT formatting, compile, and
  tests, then packages and deploys CloudFormation through
  `guardian/actions-riff-raff`. It uses `riff-raff.yaml`. There is no Docker
  build or ECR image publication step yet.
- `riff-raff.yaml` declares CloudFormation deployment, and CI passes
  `configPath: riff-raff.yaml`. The repository has a `GU_ARTIFACTS_ROLE_ARN`
  secret, and the repository OIDC customization reports immutable subjects
  enabled. Secret values and AWS role trust were not inspected.

Downstream assessment supplied by the user:

- Teams: DevX and Security.
- Existing regression protection: built-in CI; the user considers it
  sufficient.
- The user considers manual testing and automated deployment checks
  sufficient for migration validation. No observability was identified.
- The agreed CODE check is to deploy via Riff-Raff, confirm the deployment
  succeeded, and interact with the UI. Named individual contacts and
  additional observability are not identified.

## Local Validation

- Read repository source/configuration and CI definitions; no AWS calls,
  builds, tests, synthesis, image publication, or deployment were run.
- `gh secret list --repo guardian/amigo` — succeeded; confirmed the names
  `GU_ARTIFACTS_ROLE_ARN` and `GU_RIFF_RAFF_ROLE_ARN` are configured. Values
  were not read.
- `gh api repos/guardian/amigo/actions/oidc/customization/sub` — succeeded;
  reported `use_immutable_subject: true`.
- `sbt 'testOnly services.TargetHeaderFilterSpec'` — passed (3 tests) for the
  preceding optional target-response-header step.
- Application tests and CDK snapshot tests were not run because this step
  changes assessment documentation only.

## Prerequisites for Merge

- [x] DevX and Security are identified as downstream teams.
- [x] The user considers built-in CI sufficient and accepts manual testing and
  automated deployment checks as the migration validation approach.
- [x] The user agreed the CODE check: deploy through Riff-Raff, confirm
  successful deployment, and interact with the UI.
- [ ] Agree the specific UI workflows, pass results, and reviewers with DevX
  and Security. No observability was identified; define operational health
  signals and rollback/response owners before weighted traffic shifting.
- [x] `GU_ARTIFACTS_ROLE_ARN` is configured in the repository and immutable
  OIDC is enabled. The role's trust and usability by an approved publishing
  workflow still need verification by the responsible team.
- [x] `riff-raff.yaml` exists, declares CloudFormation deployment, and is
  referenced by GitHub CI.
- [ ] Upversion GuCDK to at least `66.0.0` and prepare
  `GuLoadBalancedAppExperimental` in a separate step, preserving the EC2
  configuration and snapshot coverage.
- [ ] Resolve the Cognito integration while preserving existing login and
  Google group authorization behavior.
- [ ] Define a single active scheduler/housekeeping owner during parallel
  EC2/ECS operation and with multiple ECS tasks.
- [ ] Consolidate configuration to one S3 prefix and one filesystem path;
  support ECS task-role credentials and Packer scratch/network requirements.
- [ ] Make application/runtime logging stdout-only and validate the app with a
  read-only container filesystem.
- [ ] Add ARM64-compatible container build and approved ECR publishing to CI,
  then provision ECS only through GuCDK's `ecsProps`, initially at 0% traffic.

## Deployment and Verification

No deployment or live verification was performed. The user-agreed CODE check
is to deploy through Riff-Raff, confirm successful deployment, and interact
with the UI. DevX and Security should specify which UI workflows and results
constitute a pass. A CODE bake and verification of its status, logs, resulting
AMI, and expected notifications are possible additional checks, but were not
included in the user's agreed validation. No live result is claimed.

The following read-only commands are for the service owner to run; they were
not run by the agent. Replace `<AWS_PROFILE>` with the team's profile:

```shell
aws cloudformation describe-stacks \
  --stack-name amigo-CODE \
  --region eu-west-1 \
  --profile <AWS_PROFILE> \
  --query 'Stacks[0].StackStatus' \
  --output text

aws cloudformation list-stack-resources \
  --stack-name amigo-CODE \
  --region eu-west-1 \
  --profile <AWS_PROFILE> \
  --query 'StackResourceSummaries[].[LogicalResourceId,ResourceType,ResourceStatus]' \
  --output table
```

An `*_COMPLETE` stack status and healthy current resources are expected.
Investigate failed/in-progress resources with the service owner before using
the deployment as a migration baseline. Later ECS steps must additionally
verify ECS task health, target health, and routing weights after deployment.
Removing the web app's EC2 service must not remove the EC2 permissions,
security group, or related resources still needed by Packer bake instances.
