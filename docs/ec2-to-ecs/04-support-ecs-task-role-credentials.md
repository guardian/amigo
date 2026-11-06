## Purpose

Allow AMIgo's AWS SDK v2 clients to use the ECS task role when the application
runs in an ECS container, while preserving the existing local profile and EC2
instance-profile credential sources.

This step does not configure ECS, change IAM permissions, or deploy the
application to ECS.

## Base Branch and Dependencies

Base branch: `ec2-to-ecs-03-use-sync-in-user-data`.

The step-03 CODE deployment and UI checks remain a pre-merge gate for the
stacked migration changes. Cognito configuration, scheduler ownership, and
container logging/filesystem preparation remain unresolved prerequisites
before ECS is provisioned.

## Proposed Changes

- Add the AWS SDK v2 container credentials provider to AMIgo's existing
  credentials provider chain, before the EC2 instance-profile provider.
- Preserve the `deployTools` profile and default profile precedence for local
  development, and preserve the EC2 instance-profile fallback.
- Leave IAM policies, Packer behavior, ECS configuration, and authentication
  unchanged.

## Local Validation

- `sbt scalafmtCheckAll test` — passed; formatting checks passed and all 50
  application tests succeeded.
- `git diff --check` — passed.
- No AWS credentials, AWS calls, image publishing, or deployment will be used
  for local validation.

## Prerequisites for Merge

- [ ] Confirm the step-03 CODE deployment and agreed UI checks pass before
  merging the stacked migration work.
- [ ] Confirm CI passes.
- [ ] Before ECS provisioning, resolve Cognito's allowed Google group emails
  and credentials secret path, choose one active scheduler/housekeeping owner,
  and complete the remaining container logging, filesystem, and Packer
  runtime preparation.

## Deployment and Verification

No AWS or ECS deployment is part of this step. After local validation and CI,
review the provider chain and merge only after the step-03 CODE validation gate
is satisfied. ECS task-role behavior should be verified as part of a later
controlled ECS deployment by checking that application AWS SDK calls succeed
with the task role and do not require EC2 instance metadata credentials.
