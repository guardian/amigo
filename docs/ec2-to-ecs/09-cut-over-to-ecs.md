## What does this change?

<!-- A PR should have enough detail to be understandable far in the future. e.g what is the problem/why is the change needed, how does it solve it and any questions or points of discussion. Prefer copying information from a Trello card over linking to it; the card may not always exist and reviewers may not have access to the board. -->

## Purpose

**WARNING: deploying this proposal changes production traffic to 100% ECS
when PROD is deployed. The prerequisites below must be satisfied before
merging or deploying. This branch is not cutover approval or live evidence.**

Propose ECS as the destination for all normal traffic, retaining EC2 at 0%
through an owner-agreed observation period for rollback.

## Base Branch and Dependencies

Base branch: `ec2-to-ecs-08-serve-partial-traffic-with-ecs`.

Step 8 proposes 1% ECS / 99% EC2. Its CODE and production partial-traffic
results have not been supplied. Step 7's dedicated CODE UI/API and
two-concurrent-bake testing, monitoring criteria and downstream review remain
required, not assumed complete.

Predecessor gates also remain: scheduler/housekeeping ownership,
authentication/Cognito integration, task-role and S3 permissions, scratch
compatibility and the revised GuCDK minimum 67.1.0. The inherited dependency
is still 66.0.0; resolve prerequisite changes on the appropriate branches
and propagate them before deployment.

## Proposed Changes

- Set the shared target-weight object to EC2 0 and ECS 100. Both GuCDK
  configuration and the authenticated listener forwarding action use it.
- Apply this proposal to CODE and PROD when each stage is deployed.
- Keep EC2 instances, target group, permissions and authenticated
  `X-Gu-Target-Group: ec2` routing available during observation.
- Preserve ECS sizing, task count, mounts, image-digest wiring,
  authentication and all other resources.
- Update CODE/PROD routing assertions and the infrastructure snapshot.
  The snapshot changes only the two default forwarding weights.

This routes normal requests, not background work. EC2 may still run jobs
at 0%, and deliberately header-routed requests can still reach it.
Weighted target groups do not provide automatic fallback to healthy EC2
targets if ECS becomes unhealthy.

## How to test

<!-- Provide instructions to help others verify the change. This could take the form of "On PROD, do X and witness Y. On this branch, do X and witness Z. " -->

## Local Validation

- `cd cdk && npm run lint -- --quiet` - passed.
- `cd cdk && npm test -- --runInBand --updateSnapshot` - passed; seven tests
  and four snapshots, one updated. Tests assert 0:100 weights in CODE and
  PROD while retaining EC2, authentication and filesystem/mount requirements.
- `cd cdk && npx tsc --noEmit --moduleResolution node` - passed, using
  CommonJS-compatible resolution as in preceding steps.
- Reviewed the snapshot diff: EC2 weight 99 to 0 and ECS weight 1 to 100;
  no resource or capacity changes.
- `git diff --check` - passed.
- No AWS reads, live tests, publication, deployment or traffic changes were
  performed. CLI synthesis with the actual published digest remains for CI;
  unit tests synthesize stacks using an explicit test digest fixture.

## Deployment and Verification

These are instructions for the service owner; the agent has not executed
them. Obtain explicit cutover approval only after reviewing the recorded
CODE and production partial-traffic results and all prerequisites.

Record the image digest, task revision, approved full infrastructure diff,
known-good rollback build, responder, numeric stop criteria and observation
duration before deployment. Review capacity evidence at the expected full
workload: a successful 1% sample alone does not establish full-load capacity.
No automatic capacity increase is proposed without evidence.

Deploy CODE through Riff-Raff first. Verify authenticated UI journeys,
authorization, shared state and approved controlled bakes; monitor
target-group latency/errors, CPU/memory/scratch usage, restarts, Packer
outcomes and stdout errors using the Step 7/8 agreed criteria. Confirm a
single intended scheduler/housekeeping owner. Record results and obtain
separate production deployment approval before deploying PROD.

Use the Step 6/8 resource-discovery instructions and substitute the actual
stage-specific identifiers below. These read-only commands were not run:

```shell
aws cloudformation describe-stacks \
  --stack-name <amigo-CODE_OR_amigo-PROD> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'Stacks[0].StackStatus'
aws elbv2 describe-listeners \
  --load-balancer-arn <STAGE_ALB_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'Listeners[?Port==`443`].{arn:ListenerArn,actions:DefaultActions}'
aws elbv2 describe-rules \
  --listener-arn <STAGE_HTTPS_LISTENER_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'Rules[*].{priority:Priority,conditions:Conditions,actions:Actions}'
aws elbv2 describe-target-health \
  --target-group-arn <STAGE_ECS_TARGET_GROUP_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE>
aws elbv2 describe-target-health \
  --target-group-arn <STAGE_EC2_TARGET_GROUP_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE>
aws ecs describe-services \
  --cluster <STAGE_CLUSTER> --services <STAGE_SERVICE> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'services[0].{desired:desiredCount,running:runningCount,pending:pendingCount,deployments:deployments,events:events[:10]}'
```

Expect a completed stack update, authenticated default forwarding with EC2
weight 0 and ECS weight 100, healthy ECS tasks and retained healthy EC2
targets. Verify authenticated deterministic rules remain available.
Normal application responses without a routing header should identify
`X-Gu-Target: ecs`; login redirects alone are not serving-target evidence.
Allow in-flight requests to finish and distinguish header-forced EC2 tests
from normal traffic in metrics.

Keep EC2 provisioned and deployment-ready through the agreed observation
period. Record request/error/latency trends, downstream checks, completed
bake outcomes, temporary-resource cleanup, session behavior, saturation and
scheduled-job behavior. Do not remove EC2 or expand the approved bake test
budget as part of this step. No observation duration has yet been agreed.

### Rollback

If agreed stop criteria are breached, notify the responder and restore
normal traffic to EC2 100 / ECS 0 through the reviewed deployment procedure.
Confirm EC2 is healthy and has compatible configuration before restoring
traffic. Review the complete prior build's infrastructure/image diff if
using an older Riff-Raff build; do not assume it changes only weights.
Restoring the 1% split is not the same as removing ECS from normal traffic.

Verify the authenticated listener is 100:0 and service/downstream checks
recover. Do not terminate tasks blindly while bakes are running. ECS
background jobs and forced-header requests remain possible at 0%; resolve
them under the agreed scheduler and cleanup procedures. Review the failure
before any further cutover attempt.

## What is the value of this?

<!-- Why are these changes being made? -->

Makes ECS the normal serving path while retaining the EC2 rollback option
until the post-cutover observation period has been successfully completed.

## Have we considered potential risks?

<!-- What are the potential risks and how can they be mitigated? Does an error require an alarm? Should user help, infosec, or legal be informed of this change? Is private information guarded? Do we need to add anything in the backlog? -->

The full workload may expose resource saturation, auth/session issues or bake
failures not observed at 1%. Shared data and background work require
operational coordination independently of traffic routing. No roles,
recipes or resources are removed by this proposal.

## Prerequisites for Merge

- [ ] Agreed CODE and production partial-traffic tests passed, with results
  recorded, including the dedicated load/bake validation from Step 7.
- [ ] Downstream regression tests and observability checks passed, and DevX
  and Security reviewed the results.
- [ ] Service health, errors, latency, resource headroom and other agreed
  success signals remained acceptable for the agreed observation period.
- [ ] Predecessor compatibility, GuCDK, authentication, permissions and
  scheduler-ownership gates are resolved; full-load capacity is reviewed.
- [ ] Monitoring and responders are ready; quantitative stop criteria and
  a reviewed rollback procedure to restore EC2 100 / ECS 0 are available.
- [ ] Owners agree the post-cutover observation duration and evidence to
  collect while EC2 remains available.
- [ ] The user explicitly approves the 100% ECS cutover. Requesting this
  local proposal does not satisfy this live operational approval.
- [ ] CI passes and CODE verification precedes separately approved PROD
  deployment.

Leave these conditions unchecked until supported by actual evidence.
EC2 removal is a separate Step 10 proposal, not part of this cutover.

> **Note**
> If this change adds, updates or removes a role or recipe please note that in your PR and assign appropriate reviewers for the team that will be impacted by those changes.
