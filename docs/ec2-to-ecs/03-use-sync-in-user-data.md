## Purpose

Synchronize AMIgo's S3 configuration prefix into one EC2 filesystem directory
as a prerequisite for the later ECS configuration mount, while preserving the
existing EC2 deployment.

## Base Branch and Dependencies

Base branch: `ec2-to-ecs-02-upversion-cdk`.

This step depends on the GuCDK upversion and app-pattern preparation. The
optional target header and migration assessment are present through the
preceding migration branch chain.

## Proposed Changes

- Replace the individual service-account certificate download with
  `aws s3 sync` from
  `s3://<distribution-bucket>/deploy/<stage>/amigo/conf/` to
  `/etc/gu/s3-sync/`.
- Enable GuCDK `ec2Props.allowS3Sync` so EC2 user data has the
  distribution-bucket `s3:ListBucket` permission required by the sync.
- Point the application certificate consumer at
  `/etc/gu/s3-sync/amigo-service-account-cert.json` in CODE/PROD. SSM-based
  application configuration remains unchanged.
- Update `script/devcontainer-setup` to sync the same CODE configuration prefix
  into `/etc/gu/s3-sync/`. Preserve the existing DEV certificate-path setting
  by symlinking its configured path to the synced certificate; this symlink is
  needed because local DEV retains its existing certificate-path setting.
- Keep the versioned AMIgo Debian package download at its existing artifact
  key; it is not application configuration.

The existing certificate remains at its current
`deploy/<stage>/amigo/conf/` object key; this change does not move S3 objects.
Application settings are loaded from SSM by `ConfigurationLoader`; this step
does not migrate or change those settings or secrets. Confirm whether any
additional S3 configuration objects need to be maintained under the prefix
before deployment. The later ECS S3 configuration mount must use the same
`/etc/gu/s3-sync/` path.

## Local Validation

- `bash -n script/devcontainer-setup && git diff --check` — passed.
- `sbt -batch scalafmtCheckAll test` — passed; formatting checks passed and
  all 50 application tests succeeded.
- `cd cdk && npm test -- --updateSnapshot` — passed; one stack snapshot
  updated.
- `cd cdk && npm run lint && npm test` — passed (3 suites, 4 tests, 4
  snapshots).
- `cd cdk && npx tsc --noEmit --module esnext --moduleResolution bundler && npm run synth -- --no-lookups`
  — passed without AWS lookups. Synthesis reported the existing Google OIDC
  default-action replacement, informational SSM-parameter annotations, and
  the unrelated Lambda S3 object-version warning.
- No AWS reads, Riff-Raff deployment, or live S3 access will be performed.

## Prerequisites for Merge

- [ ] Confirm the distribution bucket still contains
  `deploy/CODE/amigo/conf/amigo-service-account-cert.json` and
  `deploy/PROD/amigo/conf/amigo-service-account-cert.json`; no file relocation
  is expected from this change.
- [ ] Confirm the CODE/PROD `amigo/conf/` prefixes contain all intended S3
  configuration files. Application settings loaded from SSM remain unchanged
  by this step.
- [ ] Confirm the repository CI run passes and review the IAM snapshot to
  ensure `s3:ListBucket` is scoped to the app configuration prefix.
- [ ] Merge and deploy this step to CODE through Riff-Raff. Confirm successful
  deployment and verify the UI and application startup before proceeding to
  ECS image preparation.

## Deployment and Verification

No deployment or AWS verification was performed. After merge, deploy CODE via
Riff-Raff, confirm deployment success, and interact with the UI using the
agreed validation. The app must start with the Google service-account
certificate available at `/etc/gu/s3-sync/amigo-service-account-cert.json`.

The following read-only AWS CLI command was not run by the agent. Replace
`<AWS_PROFILE>` with the service owner's profile:

```shell
aws s3 ls \
  s3://<DISTRIBUTION_BUCKET>/deploy/CODE/amigo/conf/ \
  --region eu-west-1 \
  --profile <AWS_PROFILE>
```

Confirm the certificate object is listed. Do not copy or move objects during
verification. Review the Riff-Raff deployment result and service startup logs
for an S3 sync error or a certificate-load failure. Confirm SSM-backed
application configuration continues to load as before. If any of these checks
fail, stop before proceeding to ECS.
