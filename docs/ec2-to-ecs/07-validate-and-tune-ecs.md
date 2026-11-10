## What does this change?

<!-- A PR should have enough detail to be understandable far in the future. e.g what is the problem/why is the change needed, how does it solve it and any questions or points of discussion. Prefer copying information from a Trello card over linking to it; the card may not always exist and reviewers may not have access to the board. -->

## Purpose

Prepare dedicated CODE load testing of AMIgo's ECS deployment, including
concurrent AMI bakes and authenticated UI/API traffic, before shifting normal
traffic from EC2. This is a test plan, not evidence of successful testing.

## Base Branch and Dependencies

Base branch: `ec2-to-ecs-06-configure-and-provision-ecs-tasks`.

The preceding branch proposes one ARM64 task with 2048 CPU units, 4096 MiB
memory, read-only S3 configuration and root filesystem, and writable `/tmp`.
Normal routing remains EC2 100 / ECS 0. No infrastructure changes are made here.
Step 6's deployment, authentication, permissions, scheduler ownership and
scratch-volume prerequisites remain outstanding.

The current dependency is GuCDK 66.0.0, below the skill's revised minimum
67.1.0. Resolve the upversion and its infrastructure/volume compatibility
checks in the prerequisite branch before deployment; this documentation-only
step does not silently combine an upversion with test planning.

## Proposed Changes

Record the user's decisions:

- Dedicated load testing is required in CODE.
- Include concurrent AMI bakes as well as UI/API traffic.
- Cap test bake concurrency at two.
- The proposed workload and success criteria below are a draft requiring
  owner agreement, not approved thresholds.

No live results, performance measurements or monitoring evidence have been
supplied. Do not tune CPU, memory, task count or storage based on this plan
alone. Review the [load-testing guidance](https://docs.google.com/document/d/1jlQ48FV_6LetQ-iFNxNho2BmqY989wNXsGREpRMr04E/edit?pli=1&tab=t.bx59w21dy2ge#heading=h.mhmh3dxq2tcl)
with DevX and Security before execution.

## How to test

<!-- Provide instructions to help others verify the change. This could take the form of "On PROD, do X and witness Y. On this branch, do X and witness Z. " -->

## Local Validation

Inspected the route definitions, Step 6 proposal, dependency version and Git
branch ancestry. This change only adds documentation. No load generator,
application, AWS query, deployment, image publication or live test was run.
Load-test results and runtime validation remain unverified.

## Deployment and Verification

All execution instructions in this section are for the service owner or
designated test operator. The agent has not executed them.

### Preconditions and test ownership

Name the DevX test operator, Security reviewer, monitoring observer and
stop/cleanup owner. Record an agreed CODE test window, AWS spend ceiling,
approved recipe IDs and bake timeout before starting. Check account quotas
and confirm selected recipes have no production distribution or downstream
effects. Use isolated CODE test data and preserve existing AMIs.

Deploy and verify Step 6 in CODE first, with healthy ECS targets at 0% normal
traffic, a known image digest and task revision, functioning authentication
and available stdout logs. Resolve duplicate scheduler/housekeeping execution
across EC2 and ECS before starting either the baseline or ECS test. Two is
the total concurrent test bake limit across both environments, not per task;
account for any other bakes before starting. Do not run deployment replacement
or unrelated capacity changes during the measured window.

### Authenticated routing and workload

Use `https://amigo.code.dev-gutools.co.uk` only. Authenticate through the
normal ALB and application flows, with authorized test-user sessions.
Keep cookies and tokens outside the repository and out of shared reports.
Do not disable authentication or count login pages and redirects as successful
application responses.

Apply `X-Gu-Target-Group: ec2` for the baseline and `X-Gu-Target-Group: ecs`
for ECS requests. Confirm the application response carries `X-Gu-Target: ecs`
for ECS requests; record the actual EC2 response and remember the EC2
fallback is an inference. Check response bodies as well as status, and verify
header-routing rules still authenticate unauthenticated requests. Do not
change target weights to run this test.

Use only representative read routes for generated traffic: `/`, `/recipes`,
`/base-images`, `/roles`, and selected existing `/recipes/:id` and
`/recipes/:recipeId/bakes/:buildNumber` pages. Keep the route mix and dataset
identical between EC2 and ECS. Check that the selected pages perform no
mutations. Do not sweep all GET endpoints or use `/healthcheck` as a proxy
for UI performance.

The following profile is proposed, not agreed:

| Phase | Traffic | Duration | Bake activity |
|---|---|---|---|
| EC2 baseline | 1 request/second, then 5 requests/second | 5 minutes, then 10 minutes | No new bakes |
| ECS comparison | Same route mix at 1 request/second, then 5 requests/second | 5 minutes, then 10 minutes | Launch one pair of approved test bakes during the 5 request/second phase |
| Bake completion and recovery | Stop generated load; continue monitoring | Until both bakes finish or the approved timeout expires, followed by an agreed recovery window | No additional bakes |

Use an operator-selected authenticated load generator with bounded rate and
concurrency. Record its version, route mix, connection limit, redirect handling
and latency definition before execution. No new load-test tooling is added by
this proposal. The EC2 no-bake baseline measures UI overhead; it is not a
like-for-like concurrent-bake throughput benchmark.

Start the two bakes manually through the normal CSRF-protected application
flow, routing the triggering requests to ECS. Confirm overlap and record the
two distinct bake IDs and their task attribution from logs. A global bake
listing alone does not establish which process started Packer. Do not
automatically retry or repeatedly POST to `/recipes/:id/bake`. Proposed total
test budget is two bake starts, subject to owner approval.

### Monitoring and draft success criteria

Capture a timestamped baseline, load window, bake completion and recovery.
Separate ECS and EC2 metrics rather than relying on combined ALB averages.
Use ECS/Container Insights for CPU, memory and task restarts; ALB target-group
metrics for latency, request counts, target health and target 5xx; the load
generator for per-route p50/p95/p99, response validation, errors and achieved
rate. Include client-side failures and throttling, not only ALB 5xx.

Review stdout logs for Packer/Ansible/Session Manager errors, failed task-role
credentials, access denials, SSM/S3 Files failures, duplicate scheduled jobs,
read-only filesystem errors and failed scratch cleanup. Measure task
ephemeral-storage usage using available platform metrics; establish how to
observe scratch usage before testing, rather than assuming a metric exists.
Confirm the scratch mount is usable by the image's non-root user.

Draft pass criteria, all requiring owner agreement:

- Both controlled bakes succeed, produce the expected test AMIs/package
  lists, and clean up their temporary resources; no duplicate bake starts.
- No unexpected HTTP errors, invalid bodies, login responses misclassified
  as successes, task restarts, OOM kills or loss of healthy ECS targets.
- ECS p95 latency is no more than 20% above the EC2 baseline for the same
  route mix and achieved rate. Record the added bake load when interpreting
  the comparison; owners must also set an absolute latency ceiling.
- Peak task CPU, memory and scratch usage each remain below 80% of their
  actual limits, with no exhaustion, throttling or upward leak after recovery.
- DevX and Security accept the UI, authorization, bake and downstream
  outcomes and the monitoring evidence.

### Stop, cleanup and results

Stop generated traffic immediately on auth/routing failures, unexpected AWS
mutations, unhealthy tasks, resource exhaustion or the approved cost/timeout
limit. Do not start further bakes. The designated operator must use the
agreed bake cancellation/cleanup procedure and inspect lingering instances,
volumes, snapshots, test AMIs and other recipe-created resources. Do not
terminate the ECS task blindly while Packer is active or delete unrelated
resources. EC2 remains the normal serving path; involve the service owner
before any further deployment or rollback.

Record image digest, task revision, GuCDK version/runtime, UTC window, route
mix, target checks, achieved rates, percentiles/errors per route, resource
peaks, bake IDs/durations/outcomes, log/graph links, cleanup confirmation and
reviewer decisions in the reviewed test report. Exclude credentials and
service-account configuration. Compare observations with the agreed limits.
Propose tuning only where those observations justify it, then repeat the
affected checks and record the new task revision.

These read-only checks supplement the Step 6 discovery instructions. Replace
placeholders with the discovered CODE identifiers; the agent has not run them:

```shell
aws ecs describe-services \
  --cluster <CODE_CLUSTER> --services <CODE_SERVICE> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'services[0].{desired:desiredCount,running:runningCount,pending:pendingCount,deployments:deployments,events:events[:10]}'
aws elbv2 describe-target-health \
  --target-group-arn <CODE_ECS_TARGET_GROUP_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE>
aws elbv2 describe-listeners \
  --load-balancer-arn <CODE_ALB_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'Listeners[?Port==`443`].DefaultActions'
```

Expect healthy tasks/targets without repeated deployments and an authenticated
default route with EC2 weight 100 and ECS weight 0 before and after the test.
These checks do not replace latency, storage, bake or downstream evidence.

## What is the value of this?

<!-- Why are these changes being made? -->

Defines a bounded test of both UI responsiveness and AMIgo's defining workload,
without mistaking a healthy container or synthetic healthcheck for a successful
migration.

## Have we considered potential risks?

<!-- What are the potential risks and how can they be mitigated? Does an error require an alarm? Should user help, infosec, or legal be informed of this change? Is private information guarded? Do we need to add anything in the backlog? -->

Concurrent bakes create billable AWS resources even at 0% ECS traffic. Shared
CODE data, scheduler overlap, authentication sessions, task scratch capacity
and cleanup require explicit ownership. No production testing or traffic
shift is authorized by this plan.

## Prerequisites for Merge

- [x] User decided dedicated CODE load testing is needed, including UI/API
  traffic and concurrent bakes capped at two.
- [ ] Owners agree the workload, recipes, rates, durations, concurrency,
  request mix, budget, timeout, recovery window and measurable pass criteria.
- [ ] Step 6 prerequisites are satisfied, including the revised GuCDK minimum,
  runtime/volume compatibility, scheduler ownership and authentication.
- [ ] Name test, monitoring, stop/cleanup and downstream review owners.
- [ ] Complete the agreed CODE test and review load, resource, log, bake,
  cleanup and downstream evidence with DevX and Security.
- [ ] Any proposed tuning is supported by those results and revalidated.
  No tuning is proposed without evidence.

These outstanding conditions do not prevent preparing this branch, but must
be satisfied before merge. Successful CODE testing alone does not authorize
Step 8 traffic changes or production load testing.

> **Note**
> If this change adds, updates or removes a role or recipe please note that in your PR and assign appropriate reviewers for the team that will be impacted by those changes.
