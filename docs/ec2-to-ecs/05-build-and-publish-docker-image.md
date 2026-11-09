## Purpose

Build an ARM64 Docker image for AMIgo, publish it to the Guardian ECR
repository from CI, and provide a local Compose workflow that mounts CODE
configuration read-only. This step does not configure or deploy ECS.

## Base Branch and Dependencies

Base branch: `ec2-to-ecs-04-prepare-amigo-runtime-for-ecs`.

This branch includes the step-04 runtime changes. The step-03 configuration
sync and CODE deployment/verification remain required migration prerequisites;
the step-04 stdout logging and runtime changes also need their documented
verification. CI publishing uses the existing `GU_ARTIFACTS_ROLE_ARN` secret
and the approved `guardian/actions-ecr/push` action. The prior assessment
records that the secret is configured and immutable OIDC is enabled; CI role
trust and publication have not been verified by this change.

## Proposed Changes

- Enable sbt-native-packager's Docker plugin and build a Java 21 image for
  port 9000 on Ubuntu 22.04 ARM64, consistent with the application's existing
  Ubuntu ARM64 runtime tooling.
- Include ARM64 Packer 1.13.1, the HashiCorp Amazon and Ansible Packer
  plugins, and the Ubuntu ARM64 AWS Session Manager `.deb` package required
  by AMI bakes.
- Build the image locally in `script/ci`; run that CI job on an ARM64 GitHub
  runner and publish the resulting image to ECR with the team's approved
  action.
- Add Compose services that fetch the existing CODE `amigo/conf/` prefix
  into a named volume and mount it read-only at `/etc/gu/s3-sync/`, matching
  the path established by step 03 and intended for the ECS S3 configuration
  mount. Credentials are passed explicitly through environment variables,
  and downloaded configuration stays outside the repository. SSM-based
  application configuration continues to use its existing loading path.
- Set the local app container's root filesystem read-only with writable
  `/tmp`, consistent with the previously identified Packer scratch-file
  requirement.
- Keep CloudFormation deployment and the existing EC2 application unchanged.
  Passing the published digest into CDK validation is part of step 06, not
  this image-publishing step.

## Local Validation

- `sbt -batch clean scalafmtSbtCheck scalafmtCheckAll compile test 'Debian / packageBin' 'Docker / publishLocal'`
  — passed; Scala/SBT formatting, compilation, all 50 tests, Debian packaging,
  and the initial local Docker image build succeeded.
- `sbt -batch 'Docker / publishLocal'` — passed after switching the image to
  Ubuntu 22.04 ARM64 and the matching AWS Session Manager `.deb`.
- `sbt -batch scalafmtCheckAll test` — passed after the Ubuntu image
  adjustment; all 50 tests succeeded.
- `docker image inspect amigo:1.0-latest --format '{{.Os}}/{{.Architecture}}'`
  — passed; the image reports `linux/arm64`.
- `docker run --rm --read-only --tmpfs /tmp:rw,nosuid,size=1g --entrypoint /bin/sh amigo:1.0-latest -c 'id && /opt/packer/packer version && session-manager-plugin --version && touch /tmp/write-test && test -w /tmp && test ! -w /opt/docker && echo "read-only root and writable /tmp verified"'`
  — passed; Packer 1.13.1 and Session Manager plugin 1.2.896.0 are present,
  `/tmp` is writable, and the packaged application directory is not writable.
  This checked image tooling/filesystem only; it did not start AMIgo.
- `AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test DISTRIBUTION_BUCKET=example docker compose config --quiet`
  — passed without AWS access; revalidated after aligning the mount with
  `/etc/gu/s3-sync/`.
- `git diff --check` — passed.
- The image startup check with a read-only filesystem is blocked unless
  isolated SSM/AWS dependency fixtures and a valid local configuration fixture
  are available. AMIgo currently loads runtime configuration and AWS-backed
  dependencies at startup. Do not start the app against live AWS as a local
  validation substitute.
- No AWS config-sync sidecar, image publication, deployment, or live service
  check was run.

## Prerequisites for Merge

- [ ] Confirm the step-03 CODE deployment and application/UI checks passed,
  and that the `deploy/CODE/amigo/conf/` prefix contains the required
  certificate/configuration files.
- [ ] Confirm step-04 stdout logging and runtime changes passed their
  documented verification.
- [ ] Confirm CI can assume the approved ECR publishing role using immutable
  OIDC and that the image publishes successfully for ARM64.
- [ ] Review CI and the published image for the expected ARM64 architecture,
  Packer and Session Manager tooling, and a Java 21 runtime.
- [ ] Run the image with isolated configuration and dependency fixtures under
  a read-only root filesystem, and confirm startup succeeds without file
  logging. This repository does not currently provide those fixtures.
- [ ] Confirm CI passes.

## Deployment and Verification

No AWS, ECR, deployment, or live verification was performed. After the
prerequisites above and CI pass, the service owners should run the documented
CODE deployment and UI checks from the preceding steps. Use the local Compose
workflow in the README only with approved short-lived credentials and the
correct deployment distribution bucket; it intentionally syncs the CODE
configuration at startup.

For CI, confirm the publish step succeeds and reports an immutable image
digest. The following read-only AWS CLI check is for the service owner; the
agent did not run it. Replace placeholders with the ECR registry account,
region, repository, and GitHub run number:

```shell
aws ecr describe-images \
  --registry-id <ECR_ACCOUNT_ID> \
  --repository-name guardian/amigo/amigo \
  --region eu-west-1 \
  --image-ids imageTag=build-<GITHUB_RUN_NUMBER> \
  --query 'imageDetails[0].[imageDigest,imageTags]' \
  --output table \
  --profile <AWS_PROFILE>
```

The image digest and build tag should be present. A missing image, failed
publication, or non-ARM64 image is a blocker for step 06. Do not run this
command with agent credentials or publish an image manually as part of this
step.
