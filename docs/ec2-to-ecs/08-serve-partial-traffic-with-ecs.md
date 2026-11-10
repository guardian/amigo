## What does this change?

<!-- A PR should have enough detail to be understandable far in the future. e.g what is the problem/why is the change needed, how does it solve it and any questions or points of discussion. Prefer copying information from a Trello card over linking to it; the card may not always exist and reviewers may not have access to the board. -->

## Purpose

Propose the user's initial traffic split: 1% ECS and 99% EC2, retaining the
existing EC2 deployment for rollback.

**Deploying this proposal changes normal traffic, including production when
PROD is deployed. Satisfy the merge prerequisites first, deploy CODE before
PROD, and obtain stage-specific deployment approval. Nothing has been
deployed or live-tested by the agent.**

## Base Branch and Dependencies

Base branch: `ec2-to-ecs-07-validate-and-tune-ecs`.

Depends on the preceding ECS task/configuration changes and the Step 7 CODE
load-test plan. The user requested "1% on first cut". That specifies the
proposed split, not evidence that testing passed or permission to operate AWS.

Outstanding predecessor gates remain, including deployment health at 0%,
concurrent-bake validation, scheduler/housekeeping ownership, authentication,
S3 permissions, scratch-volume compatibility and the revised GuCDK minimum
67.1.0 (the inherited dependency is 66.0.0). Resolve prerequisite changes on
their appropriate branches and propagate them before deployment.

## Proposed Changes

- Set relative target-group weights to EC2 99 and ECS 1 in the shared stack
  definition, applying to CODE and PROD when each is deployed.
- Use one local weight object for both GuCDK's `targetGroupWeights` and the
  authenticated listener's final forwarding action, avoiding divergent
  settings where the custom auth action replaces GuCDK's default action.
- Retain OIDC authentication, authenticated deterministic target-selection
  rules, both target groups, existing EC2 capacity, task resources, mounts
  and task count unchanged.
- Assert the exact EC2/ECS target references and 99:1 weights in both CODE and
  PROD tests. The snapshot changes only the two default forwarding weights.

The split is a routing probability, not a guarantee of exactly 1% in a small
sample. Requests carrying `X-Gu-Target-Group` deliberately bypass the weighted
choice, but not authentication. Exclude such requests from split measurements.
ALB weighted forwarding is not automatic failover: do not assume unhealthy
ECS targets will cause its share to be routed to EC2.

## How to test

<!-- Provide instructions to help others verify the change. This could take the form of "On PROD, do X and witness Y. On this branch, do X and witness Z. " -->

## Local Validation

- `cd cdk && npm run lint -- --quiet` - passed.
- `cd cdk && npm test -- --runInBand --updateSnapshot` - passed; seven tests
  and four snapshots, one updated. Both stages retain authentication,
  read-only application/configuration, writable scratch and EC2 resources.
- `cd cdk && npx tsc --noEmit --moduleResolution node` - passed, using
  CommonJS-compatible resolution as in Step 6.
- Reviewed the snapshot diff: only EC2 weight 100 to 99 and ECS weight 0 to 1.
  No resource additions, deletions or capacity changes appear in that diff.
- `git diff --check` - passed.
- No AWS reads, publication, deployment, traffic shift, live monitoring or
  application/load testing was performed. Full CLI synthesis with the actual
  published digest remains for CI; unit tests use the explicit digest fixture.

## Deployment and Verification

These are instructions for the service owner, not commands run by the agent.

Before deploying, record the candidate image digest, task revision, known-good
EC2-serving Riff-Raff build, responsible responder, observation window and
quantitative stop criteria. Reuse the reviewed Step 7 results and thresholds;
its draft rates and thresholds are not yet approved. Keep scheduler ownership
explicit: traffic weights do not prevent either process from running jobs.

Deploy CODE through the normal Riff-Raff path only after the prerequisites
are met. Verify the synthesized/deployed change is limited to the intended
split; examine any additional changes introduced by a prerequisite rebase.
Observe normal authenticated UI traffic without a target-selection header.
Confirm recipe browsing, authorization and approved bake journeys work,
including CSRF-protected requests; do not generate repeated bake POSTs.
Requests in a multi-step journey may reach different compute types, so check
cross-target state/session behavior as well as requests forced to one target.

Use the Step 6 CloudFormation resource-discovery instructions to identify the
ALB, ECS service/cluster and target groups. The following read-only commands
have not been run by the agent. Use the CODE resources first and substitute
the PROD resources only after separate production approval:

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
  --query 'services[0].{desired:desiredCount,running:runningCount,pending:pendingCount,events:events[:10]}'
```

Expect a completed stack update, the authenticated HTTPS default action
forwarding to the identified EC2 target with weight 99 and ECS target with
weight 1, authentication on the target-selection rules, and healthy targets
on both paths. Record target-group-specific request counts, errors and latency
alongside ECS CPU/memory/scratch usage, task restarts and stdout log errors.
Check the `X-Gu-Target` response on authenticated application responses, not
only login redirects; ECS should identify itself as `ecs`.

At 1%, low request volumes may provide little ECS evidence. Agree an
observation duration and minimum ECS request sample with the owners before
execution rather than declaring success from a few healthy responses.
Review DevX/Security downstream checks, completed/failed bake outcomes,
scratch cleanup and duplicate scheduled work. Record CODE evidence and
approval before repeating the deployment/verification process in PROD.

### Rollback

On breached agreed thresholds, auth/session regressions, unhealthy ECS
targets, bake failures or duplicate background work, stop the rollout and
notify the named responder. The service owner should deploy a reviewed
rollback restoring EC2 weight 100 and ECS weight 0 in both GuCDK settings and
the authenticated forwarding action, without removing EC2 or blindly killing
tasks running bakes. A prior known-good Riff-Raff build may be used only after
reviewing its complete infrastructure/image diff and compatibility; do not
assume redeploying an older build is a weights-only change.

Verify the deployed default action is 100:0 and both service/data integrity
checks recover. Header-routed test requests and background jobs can still run
on ECS at 0%; resolve those separately using the agreed scheduler and bake
cleanup procedures. Do not retry the rollout until the cause and evidence
have been reviewed.

## What is the value of this?

<!-- Why are these changes being made? -->

Introduces a small initial ECS share while keeping the EC2 serving path and
a documented route back to 0% normal ECS traffic.

## Have we considered potential risks?

<!-- What are the potential risks and how can they be mitigated? Does an error require an alarm? Should user help, infosec, or legal be informed of this change? Is private information guarded? Do we need to add anything in the backlog? -->

Normal requests can reach ECS after deployment, including bake submissions.
Shared sessions/data, process-local behavior, scheduler overlap and task-role
permissions need demonstrated compatibility, not just target health. No role
or recipe changes are proposed in this step.

## Prerequisites for Merge

- [x] User selected the proposed initial split: 1% ECS / 99% EC2.
- [ ] ECS is deployed and healthy at 0% normal traffic, with Step 6's
  prerequisites resolved and recorded.
- [ ] Step 7's workload and pass criteria are agreed; dedicated CODE UI/API
  and two-concurrent-bake testing is complete and its results reviewed.
- [ ] DevX/Security downstream checks and operational monitoring are
  acceptable, including scheduler ownership and cross-target sessions/state.
- [ ] Owners approve applying the proposed split after reviewing the evidence;
  CODE deployment precedes separately approved PROD deployment.
- [ ] A named responder, observation duration, minimum request sample, stop
  criteria and reviewed 100:0 rollback procedure/build are ready.
- [ ] CI passes and the actual image digest and full infrastructure diff are
  reviewed before deployment.

All unconfirmed gates remain unchecked. This branch is a conditional
proposal, not confirmation of successful deployment or readiness for Step 9.

> **Note**
> If this change adds, updates or removes a role or recipe please note that in your PR and assign appropriate reviewers for the team that will be impacted by those changes.
