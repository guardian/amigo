## What does this change?

<!-- A PR should have enough detail to be understandable far in the future. e.g what is the problem/why is the change needed, how does it solve it and any questions or points of discussion. Prefer copying information from a Trello card over linking to it; the card may not always exist and reviewers may not have access to the board. -->

## Purpose

Prepare an ARM64 ECS task through GuCDK alongside the existing EC2 application,
with ECS receiving 0% of normal traffic. Nothing has been deployed.

## Base Branch and Dependencies

Base branch: `ec2-to-ecs-05-build-and-publish-docker-image`.

This depends on the preceding configuration, runtime and image changes.
GuCDK 66 currently implements the service using Fargate. The user confirmed
that GuCDK will handle the forthcoming move to Managed Instances; this change
does not create independent capacity infrastructure.

## Proposed Changes

- Configure `GuLoadBalancedAppExperimental.ecsProps` with the immutable image
  digest from CI, repository `guardian/amigo/amigo`, ARM64 Linux, 2048 CPU units,
  and 4096 MiB memory. This initially matches the existing `t4g.medium` core and
  memory sizing; it is not evidence-backed performance tuning.
- Run one task, with minimum and maximum both one. Deployment replacement can
  temporarily run more tasks. EC2 remains provisioned and serves normal traffic.
- Use `getDefaultS3ConfigMount` to mount the existing
  `deploy/<STAGE>/amigo/conf/` prefix at `/etc/gu/s3-sync/`, read-only.
  SSM-based configuration loading remains unchanged.
- Add `tmp-volume` to the GuCDK-created task definition and mount it read/write
  at `/tmp` in the AMIgo container. This is ephemeral task scratch space, not
  a Packer installation or persistent bake store. Packer remains in the image.
  The root filesystem remains read-only.
- GuCDK 66 overrides the complete CloudFormation `Volumes` array, hiding
  volumes added through `addVolume`. Replace that override with all three
  volumes: GuCDK's logging volume, its S3 Files mount, and the scratch volume.
  Tests verify the exact array and both application and log-router mounts.
  Revisit this version-specific adjustment when upgrading GuCDK.
- Preserve stdout log shipping through GuCDK's FireLens sidecar. Its writable
  `/init` volume is separate from application scratch space.
- Keep the existing Google OIDC action on both the default weighted route and
  the target-selection header rules. Cognito integration remains an outstanding
  prerequisite, not a silently changed authentication flow.
- Run CDK lint, tests and synthesis after image publication in CI, passing the
  publishing action's `imageDigest` output. Missing or invalid digests fail
  explicitly; production synthesis never falls back to a test digest or tag.
- Attach the existing application/Packer policies to the task role through
  GuCDK. This adds IAM roles and policies and needs appropriate DevX/Security
  reviewers.

## How to test

<!-- Provide instructions to help others verify the change. This could take the form of "On PROD, do X and witness Y. On this branch, do X and witness Z. " -->

## Local Validation

- `cd cdk && npm run lint -- --quiet` - passed.
- `cd cdk && npm test -- --runInBand --updateSnapshot` - passed after correcting
  the volume override; one infrastructure snapshot updated.
- `cd cdk && npm test -- --runInBand` - passed; seven tests and four snapshots.
  Tests cover CODE and PROD volumes, read-only roots, ARM64 sizing, preserved
  EC2, zero normal ECS traffic, authenticated target-selection rules, and
  rejection of mutable image tags. Jest uses a fixed digest only as a fixture.
- `cd cdk && npx tsc --noEmit --moduleResolution node` - passed. The repository's
  inherited bundler resolution conflicts with its CommonJS module setting;
  the compatible command avoids that existing configuration issue without
  changing the shared TypeScript setup.
- `bash -n script/ci` - passed.
- `git diff --check` - passed.
- Full CDK CLI synthesis is not run: no published image digest was supplied.
  Unit tests synthesize stacks using an explicit test fixture, without AWS.
- No AWS reads, config fetching, image publication, deployment or application
  startup against live dependencies was performed.

## Deployment and Verification

The following actions and commands are for the service owner and were not
run by the agent. Satisfy the prerequisites below before merging or deploying.
Deploy CODE through Riff-Raff first; do not deploy PROD merely because CODE
synthesis succeeds.

CI publishes the image before synthesizing the templates. To synthesize
locally, use an actual published digest, not a tag or the unit-test fixture:

```shell
export IMAGE_DIGEST=<PUBLISHED_SHA256_DIGEST>
cd cdk
npm run synth -- --no-lookups
```

Check CloudFormation status and discover the new physical resources:

```shell
aws cloudformation describe-stacks \
  --stack-name amigo-CODE --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'Stacks[0].StackStatus'
aws cloudformation list-stack-resources \
  --stack-name amigo-CODE --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'StackResourceSummaries[?ResourceType==`AWS::ECS::Service` || ResourceType==`AWS::ECS::Cluster` || ResourceType==`AWS::ElasticLoadBalancingV2::TargetGroup`].[ResourceType,PhysicalResourceId]'
```

Expect a completed stack update, the existing EC2 target group and a new ECS
target group. Use the discovered identifiers for these read-only checks:

```shell
aws ecs describe-services \
  --cluster <CLUSTER> --services <SERVICE> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'services[0].{desired:desiredCount,running:runningCount,pending:pendingCount,deployments:deployments,events:events[:5]}'
aws ecs describe-task-definition \
  --task-definition <TASK_DEFINITION_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'taskDefinition.{volumes:volumes,containers:containerDefinitions[*].{name:name,image:image,readOnly:readonlyRootFilesystem,mounts:mountPoints}}'
aws elbv2 describe-target-health \
  --target-group-arn <ECS_TARGET_GROUP_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE>
aws elbv2 describe-listeners \
  --load-balancer-arn <ALB_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'Listeners[?Port==`443`].DefaultActions'
```

Expect one running task and healthy targets, a digest-pinned AMIgo image,
read-only application root and configuration, and writable `/tmp` only in the
application container. The log-router retains its own writable `/init`.
The HTTPS listener must authenticate and forward with EC2 weight 100 and ECS
weight 0. Check header-routing rules also authenticate. Check stdout logs for
SSM, mount, permissions and startup errors. Confirm scratch writes and cleanup
with a controlled CODE bake, including Packer's task-role and network access.
Do not treat 0% traffic as preventing scheduled jobs: task startup itself has
side effects against the stage's AWS dependencies.

## What is the value of this?

<!-- Why are these changes being made? -->

Establishes a digest-pinned, read-only container deployment that can bake AMIs
using isolated temporary storage, while retaining EC2 as the serving path.

## Have we considered potential risks?

<!-- What are the potential risks and how can they be mitigated? Does an error require an alarm? Should user help, infosec, or legal be informed of this change? Is private information guarded? Do we need to add anything in the backlog? -->

## Prerequisites for Merge

- [ ] Confirm preceding CODE configuration/runtime checks and image publishing
  succeeded, including startup with isolated fixtures.
- [ ] Resolve a single scheduler/housekeeping owner before parallel operation.
  Every process currently starts Quartz; one ECS task plus EC2 can duplicate
  jobs even with 0% ECS traffic. Deployment replacement can overlap tasks too.
- [ ] Review the existing Google OIDC integration and resolve the migration's
  Cognito requirement while preserving group authorization.
- [ ] Review task-role/Packer access, including SSM, IAM pass-role and bake
  network requirements; a working UI alone is insufficient.
- [ ] Review GuCDK 66's S3 Files permissions. The helper grants bucket-wide
  object access (including synchronization writes for its service role), not
  prefix-scoped least privilege. Resolve this in GuCDK or explicitly approve
  the permission scope before deployment; this proposal does not claim
  least-privilege S3 access.
- [ ] Review the raw volume compatibility adjustment against the deployed
  GuCDK version, and confirm the scratch mount is writable by the image's
  non-root user and has sufficient storage for a representative CODE bake.
- [ ] Confirm CI synthesizes the actual published digest successfully.
- [ ] Confirm capacity, additional service costs and the temporary Fargate
  implementation are acceptable pending the GuCDK Managed Instances change.

> **Note**
> If this change adds, updates or removes a role or recipe please note that in your PR and assign appropriate reviewers for the team that will be impacted by those changes.
