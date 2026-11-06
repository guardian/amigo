## Purpose

Add `X-Gu-Target` to every AMIgo response so callers can distinguish ECS
requests from EC2 requests while the two environments run in parallel.

## Base Branch and Dependencies

Base branch: `main`. This optional diagnostic step is intended to precede the
numbered migration steps.

The ECS value is selected when `AWS_EXECUTION_ENV` begins with `AWS_ECS_`.
Otherwise the value is `ec2`, an inference based on AMIgo's current EC2/ECS
migration scope, not definitive proof that the request ran on EC2.

## Proposed Changes

- Added a Play `EssentialFilter` that adds `X-Gu-Target` to successful,
  error, and redirect responses while preserving their status, body, and
  other headers.
- Added environment detection for ECS execution environments and an EC2
  inference fallback.
- Registered the filter alongside AMIgo's existing HTTP filters.
- Added tests for ECS identification, the EC2 fallback, and response-header
  behavior on a redirect.

## Local Validation

- `sbt 'testOnly services.TargetHeaderFilterSpec'` — passed (3 tests).
- `sbt 'set root / Compile / scalafmtOnly / aggregate := false' 'root / Compile / scalafmtOnly app/services/TargetHeaderFilter.scala app/components/AppComponents.scala' 'set root / Test / scalafmtOnly / aggregate := false' 'root / Test / scalafmtOnly test/services/TargetHeaderFilterSpec.scala'` — passed.
- No AWS operations or deployment were performed.

## Prerequisites for Merge

- [ ] Confirm ECS Managed Instances set `AWS_EXECUTION_ENV` to a value beginning
  with `AWS_ECS_` in the deployed task. The ECS header depends on this runtime
  signal.
- [ ] Confirm downstream callers can use `X-Gu-Target` for diagnostics without
  changing existing request behavior.

## Deployment and Verification

No deployment was performed. After deployment, inspect both an EC2-served
response and an ECS-served response and confirm that each includes
`X-Gu-Target: ec2` or `X-Gu-Target: ecs`, respectively. Verify redirects retain
their status and `Location` header. Until ECS runtime environment values are
confirmed, an `ec2` value means ECS was not positively detected; it is not
proof that the response came from EC2.
