## Purpose

Prepare AMIgo's runtime for a later ECS deployment by enabling ECS task-role
credentials and removing production file-based application and JVM GC logs.
This step does not configure ECS, change IAM permissions, or deploy the
application to ECS. Packer's runtime scratch files still need writable
temporary storage; configure an ECS task volume mounted at `/tmp` before
enabling a read-only root filesystem.

## Base Branch and Dependencies

Base branch: `ec2-to-ecs-03-use-sync-in-user-data`.

The step-03 CODE deployment and UI checks remain a pre-merge gate for the
stacked migration changes. Cognito configuration and scheduler ownership
remain unresolved prerequisites before ECS is provisioned.

## Proposed Changes

- Add the AWS SDK v2 container credentials provider to AMIgo's existing
  credentials provider chain, before the EC2 instance-profile provider.
- Preserve the `deployTools` profile and default profile precedence for local
  development, and preserve the EC2 instance-profile fallback.
- Configure production application logs to write only to stdout, and remove
  the JVM's separate GC log file under `/var/log/amigo`.
- Keep Packer scratch files under the JVM temporary directory; a later ECS
  task definition must provide writable `/tmp` storage when the root
  filesystem is read-only.
- Leave IAM policies, ECS configuration, and authentication unchanged.

## Local Validation

- `sbt scalafmtCheckAll test "Debian / packageBin"` — passed; formatting
  checks passed, all 50 application tests succeeded, and the Debian app
  package was created.
- `xmllint --noout conf/logback-PROD.xml` — passed.
- `git diff --check` — passed.
- No AWS credentials, AWS calls, image publishing, or deployment will be used
  for local validation.

## Prerequisites for Merge

- [ ] Confirm the step-03 CODE deployment and agreed UI checks pass before
  merging the stacked migration work.
- [ ] Confirm CI passes.
- [ ] Before ECS provisioning, resolve Cognito's allowed Google group emails
  and credentials secret path, choose one active scheduler/housekeeping owner,
  and complete the remaining filesystem and Packer runtime preparation.
- [ ] Configure the ECS task definition with writable `/tmp` storage before
  enabling a read-only root filesystem; verify Packer builds and cleanup with
  that mount.
- [ ] Verify production stdout logging on EC2 through the existing deployment
  and logging path before merging, since the rolling application and GC log
  files are removed by this change.

## Deployment and Verification

No AWS or ECS deployment is part of this step. After local validation and CI,
review the provider chain and merge only after the step-03 CODE validation gate
is satisfied. ECS task-role behavior should be verified as part of a later
controlled ECS deployment by checking that application AWS SDK calls succeed
with the task role and do not require EC2 instance metadata credentials.
Verify that production application and GC logs appear through the existing
EC2 stdout collection path before merging this step.
