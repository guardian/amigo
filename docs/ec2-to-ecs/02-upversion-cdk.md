## Purpose

Upversion GuCDK to the migration minimum (`66.0.0`) and move AMIgo's existing
EC2-hosted Play application onto `GuLoadBalancedAppExperimental`, without
provisioning ECS or changing the current traffic path.

## Base Branch and Dependencies

Base branch: `ec2-to-ecs-01-assess-ec2-to-ecs-migration`.

This step depends on the migration assessment. The user clarified that CODE
validation is to deploy via Riff-Raff, confirm a successful deployment, and
interact with the UI.

## Proposed Changes

- Upgraded `@guardian/cdk` from `63.6.2` to `66.0.0` and updated its lockfile.
- Replaced `GuPlayApp` with `GuLoadBalancedAppExperimental`, moving the existing
  ARM64 `t4g.medium`, Packer user data, minimum instance count, application
  logging, and metric granularity into `ec2Props`.
- Kept AMIgo on its existing EC2 target group and retained its current Google
  OIDC listener action, application authentication, and group checks. No ECS
  target, ECS service, or traffic split is introduced in this step.
- Moved the app's custom IAM policies to the new top-level
  `additionalPolicies` property and changed raw `Policy` instances to
  `GuPolicy` as required by GuCDK 66.
- Updated the developer-policy construct to the stable `GuDeveloperPolicy`
  export, replacing the removed experimental import.
- Updated the CDK stack snapshot.

The user chose to preserve the existing Google OIDC integration for this
upversion. GuCDK's Google-auth Cognito integration remains a required migration
item before ECS is provisioned; its allowed group email addresses and
Secrets Manager JSON credentials still need to be agreed and configured.

## Local Validation

- `cd cdk && npm run lint` — passed.
- `cd cdk && npm test -- --updateSnapshot` — passed; one stack snapshot
  updated.
- `cd cdk && npm test` — passed (3 suites, 4 tests, 4 snapshots).
- `cd cdk && npx tsc --noEmit --module esnext --moduleResolution bundler` —
  passed.
- `cd cdk && npm run synth -- --no-lookups` — passed without AWS lookups.
  Synthesis reported that the configured Google OIDC default action replaces
  the load-balanced pattern's default forward action; the OIDC action itself
  explicitly forwards to the existing EC2 target group. It also reported
  existing informational SSM-parameter annotations and an unrelated Lambda
  S3-version warning.
- `cd cdk && npm run build` — blocked by the existing TypeScript configuration:
  `tsconfig.json` sets `module: CommonJS` while its inherited
  `moduleResolution: bundler` requires an ES module-compatible `module` value
  (TS5095). The explicit type-check above passed. The TypeScript configuration
  was not changed as part of this migration step.
- No AWS reads, Riff-Raff deployments, or live UI checks were performed.

## Prerequisites for Merge

- [ ] Review the CloudFormation change set for unexpected replacement of
  existing EC2, load balancer, listener, or DNS resources.
- [ ] Confirm the CI run for this branch passes.
- [ ] Keep Google OIDC behavior intact in this step. Before ECS provisioning,
  agree the Cognito allowed Google group email addresses and credentials
  secret path, then prepare the required GuCDK Cognito configuration.
- [ ] After the change is merged, deploy CODE via Riff-Raff, confirm successful
  deployment, interact with the UI, and share the result with DevX and
  Security before proceeding to the next migration step.

## Deployment and Verification

This branch was not deployed. After merge, the service owner should deploy the
CODE stack through Riff-Raff, confirm the deployment succeeded, and interact
with the CODE UI at `https://amigo.code.dev-gutools.co.uk`. Verify login and
group-restricted access, then exercise the normal read-only UI workflows.
Capture the deployment result and any failures for DevX and Security. A CODE
bake should only be run if separately agreed as safe.

The following read-only AWS CLI command was not run by the agent. Replace
`<AWS_PROFILE>` with the service owner's profile:

```shell
aws cloudformation describe-stacks \
  --stack-name amigo-CODE \
  --region eu-west-1 \
  --profile <AWS_PROFILE> \
  --query 'Stacks[0].StackStatus' \
  --output text
```

Expect a successful complete stack status after deployment. Investigate any
failed or in-progress status before continuing. This step keeps the existing
EC2 service serving traffic; it does not deploy ECS or authorize a traffic
shift.
