---
name: ec2-to-ecs-migration-orchestrator-skill
description: Orchestrate migrations of GuCDK applications deployed on EC2 within a GuStack to Amazon ECS Managed Instances.
---

# EC2-to-ECS Migration Orchestrator

## How to Use This Skill

This section is for users requesting a migration.

Copy this skill directory into `.github/skills/` in the repository you want to migrate. In a Copilot session with access to that repository, ask to use `ec2-to-ecs-migration-orchestrator-skill` and specify the application if the repository contains more than one.

You can request a single step or the full series. For example:

> Use ec2-to-ecs-migration-orchestrator-skill to assess this application's migration from EC2 to ECS Managed Instances.

> Use ec2-to-ecs-migration-orchestrator-skill to prepare step 6, Configure and Provision ECS Tasks, based on the previous migration branch.

> Use ec2-to-ecs-migration-orchestrator-skill to prepare the entire series of migration branches. Include the optional target response header step.

The agent will ask for information it cannot establish from the repository, including downstream testing arrangements and your load-testing decision. It will skip work that is already complete and return local, ordered branches containing proposed changes. It will not push branches, create PRs, publish images, deploy resources, or access AWS.

Each branch includes a Markdown file under `docs/ec2-to-ecs/` that you can use as its PR description. Review the changes, push the branches yourself, and create PRs using the reported base branches so each PR shows only its own step's changes. Follow the documented order and satisfy the prerequisites for merge before merging each PR.

Next to each PR-description file is a `<step>-<description>-create-pr.md` file containing an optional `gh pr create` command using its branch, base, and PR-description file. Run it yourself after pushing the head and base branches to GitHub.

You or your team run deployment and verification commands using your own AWS access. The PR-description files provide suggested read-only CLI checks and expected results. Later branches can be prepared before live testing is complete, but their existence does not mean they are ready to merge or deploy.

## Purpose

Guide teams through moving deployments from EC2 to Amazon ECS, using the repository's existing deployment tools where possible.

The migration is not a direct cutover. Keep EC2 and ECS running in parallel temporarily and shift traffic between them using weighted proportions.

Complete the prerequisites and migration steps one at a time, delivering each step's proposed changes on a separate branch that the user can turn into a PR. Avoid combining multiple migration steps in one branch so the scope and impact of each change remain clear. Do not create PRs automatically.

For every step, first inspect the repository and available deployment state to determine whether the work is already complete. If no change is needed, explain the evidence briefly, do not create an empty branch or PR, and suggest the next step. If work is needed, create a dedicated branch for that step. Keep each step's changes separate.

Use the named previous step branch as both the new branch's starting point and the PR's base if that branch exists. Use `main` instead only when the previous actions are confirmed complete and merged into `main`. For the first selected step with no predecessors, use `main`; omit the optional step 0 when it is not selected. If an intervening step is skipped without a branch because its work is already complete, confirm that work is in `main` or the preceding prepared branch and use the latest applicable preceding named branch. If a required previous branch is missing and its work is not confirmed complete and merged, ask the user rather than silently defaulting to `main`.

The user may request one step or the entire series of branches. When preparing a series, build each dependent branch on the preceding prepared branch without requiring earlier changes to be merged, deployed, or live-tested first. Record outstanding testing, observation, and approval gates as conditions the user must satisfy before merging or deploying the affected changes. A request for the entire series authorizes preparing those conditional proposals, not claiming the gates have passed or performing live actions. This series-preparation rule applies to all step-specific gates below.

Use the standard branch name listed for each step: `ec2-to-ecs-<step>-<description>`, with zero-padded step numbers for sorting. The optional target-header step uses `00` and, when chosen, comes before all other steps; retain the numbering when steps are skipped. Where available, the descriptive suffixes follow the non-application-specific Amiable exemplar branches. If a named branch already exists, inspect it before proceeding; reuse it only when it belongs to the same migration step, and ask the user to resolve any naming conflict rather than overwriting existing work.

## Branch Safety and Session Scope

Before creating or switching branches, inspect the current branch and worktree. Preserve unrelated tracked and untracked changes; do not discard, automatically stash, or carry them into migration commits. If existing changes prevent safely preparing the requested branch, stop and ask the user how to proceed.

Commit each step's proposed changes, including its PR-description Markdown file, on that step's local branch. Stage only files or hunks belonging to that step, inspect the staged diff, and do not amend or rewrite existing commits without explicit permission. Do not push branches.

Prepare only the step or series the user requested. For a single-step request, stop after handing over that branch and suggest the next step without starting it. For an explicitly requested series, continue through the requested steps, commit each branch before using it as a base, and stop after handing over the ordered branch list.

Ask questionnaire items one at a time using the available user-question tool. Reuse answers and decisions already supplied; ask again only when clarification is needed or the relevant circumstances have changed.

## When to Use

Use this skill when a GuCDK-based application is currently deployed on EC2 as part of a `GuStack` and the desired target is Amazon ECS Managed Instances.

## Inputs and Assumptions

Inspect the repository and deployment environment to confirm the prerequisites below. Report unmet or unclear prerequisites before implementing migration changes; do not silently assume access or configuration is available.

The agent will not have AWS console or CLI credentials. Never attempt to read from AWS for this process, even if credentials happen to be available. Do not request credentials. This prohibition includes AWS CLI/SDK calls, CDK context lookups, credential-provider or instance-metadata probes, and config-sync containers. Use repository evidence and user-provided deployment results instead; provide AWS CLI commands for the user to run to verify deployment state.

## Execution Boundary: Propose Changes, Do Not Operate Services

This skill produces local branches containing proposed code or documentation changes. Instructions to provision resources, publish images, shift traffic, or remove EC2 describe the intended effect of those changes when the user chooses to deploy them, not actions for the agent to execute.

The agent may inspect repository and available GitHub state read-only and run local builds, tests, and infrastructure synthesis that do not require AWS credentials, deploy resources, or affect live services. Check scripts for side effects before running them. Do not execute AWS CLI commands or CI publishing or deployment scripts locally just to validate the proposed changes.

Do not push branches, create or merge PRs, trigger remote CI or publishing workflows, publish images to a registry, deploy CloudFormation or CDK stacks, change AWS resources or traffic weights, upload or modify S3 configuration, or alter GitHub secrets, settings, or permissions. Describe any required external changes for the user or responsible team to perform. Leave image publication, deployment, live testing, and teardown to them.

User approval at a migration gate authorizes preparing the corresponding proposed changes on a branch; it does not authorize executing the live action. User-supplied evidence may establish AWS deployment state, but local validation alone must not be reported as a successful deployment or live test.

### Credential-Free Local Validation and Synthesis

Before running builds, tests, application startup, Compose, or CDK scripts, inspect their code and configuration for AWS reads, including implicit SDK credential resolution and context lookups. Run only local validation paths that do not contact AWS. Do not start a config-sync sidecar or an application that reads live AWS dependencies to validate the image.

Use existing repository CDK context and established test fixtures or mocks. For local CDK synthesis, disable lookups (for example, `cdk synth --no-lookups`) and ensure the application code itself does not make AWS calls; disabling CDK lookups alone does not prevent arbitrary SDK calls. Do not run `cdk context` refresh/reset operations or otherwise initiate lookups.

If synthesis or another check requires missing context or live AWS access, report it as blocked and document the check for the user to run. Continue with independent local checks where possible. Do not retry with credentials, invent lookup values, fabricate context, or change infrastructure definitions merely to make validation pass.

## Requirements

- Use GuCDK v66.0.0 or later (current minimum; verify the required minimum before starting because it may change).
- Preserve the existing EC2 app configuration and snapshot tests during the GuCDK upversion; prepare the `GuLoadBalancedAppExperimental` pattern with existing EC2 settings carried under `ec2Props`, as appropriate for the service. The existing pattern may be `GuEc2App` or `GuEc2AppExperimental`; inspect the repository rather than assuming either.
- Provision ECS exclusively through GuCDK's `ecsProps` on `GuLoadBalancedAppExperimental`. Do not create a separate ECS service, task definition, cluster, or capacity-provider setup outside that pattern to implement the migration.
- Use Cognito authentication when the application uses authentication.
- Verify that the GitHub repository has access to the `GU_ARTIFACTS_ROLE_ARN` secret, provided through [guardian/riffraff-platform](https://github.com/guardian/riffraff-platform); if missing, ask the responsible team to arrange access rather than changing secrets or permissions.
- Verify that Riff-Raff is configured to deploy CloudFormation; propose repository configuration changes where needed and document any external setup for the user.
- Locate `riff-raff.yaml` and verify that the GitHub CI process references it.
- Verify that immutable OIDC is enabled in the GitHub repository settings; if not, ask the user to enable it rather than changing the setting.
- Sync all configuration downloaded from S3 from one agreed prefix into `/etc/gu/s3-sync/` on EC2 and mount that same directory in ECS. Keep configuration sourced from SSM on its existing SSM loading path; do not migrate or otherwise change SSM-based configuration as part of S3 synchronization.
- Update S3-config consumers to read from `/etc/gu/s3-sync/`. Use a symlink to an application's existing expected path only when updating the consumer is not possible, and document why the symlink is necessary.
- ECS must use a read-only container filesystem, with configuration supplied through the S3 configuration mount described below. Application and runtime logging must go only to stdout, never to disk; do not make the filesystem writable to accommodate logging.

## Migration Workflow

### 0. Optional: Identify Which Target Served a Request

**Branch:** `ec2-to-ecs-00-add-target-group-type-to-response-headers`

When chosen, prepare this optional step before all other migration steps so the diagnostic header is available throughout the migration. Add a response header that identifies the serving environment. GuCDK can use the `X-Gu-Target-Group` request header (for example, `ecs` or `ec2`) to direct test requests to a target group once routing is configured; the application can add an `X-Gu-Target` response header so the caller can confirm which environment handled the request.

Derive the response value from reliable runtime environment signals, preserve existing response headers and status/body behavior, and test ECS and EC2 cases (including redirects). Treat an EC2 fallback as an inference, not definitive proof, unless the application has a reliable EC2 signal. This is optional and should not block the migration.

See the [Amiable target response header PR](https://github.com/guardian/amiable/pull/1007) for an example using `AWS_EXECUTION_ENV` to positively identify ECS and otherwise infer EC2.

### 1. Assess the Existing Deployment

**Branch:** `ec2-to-ecs-01-assess-ec2-to-ecs-migration`

Before planning migration changes, ask the user questions to understand downstream impact. Use their answers to identify downstream teams and services, then work with those teams to assess existing regression-testing and observability methods. Do not proceed to migration implementation until the checks needed to detect downstream regressions are understood, including how they can be run in CODE.

Ask:

- Which services, teams, or user-facing journeys depend on this service, and who should be involved in assessing the migration?
- What tests or checks currently detect regressions in those downstream services as close to the user as possible?
- What observability is available to identify downstream impact or user-facing regressions during the migration?
- Can these tests and observability checks be exercised in CODE? If so, how are they run and what signals or results indicate success?
- Are the existing methods suitable for detecting regressions from this migration? What gaps or limitations have the downstream teams identified?
- If checks are missing or unsuitable, what needs to be added or improved, and who will own that work?

Record the answers, relevant contacts, agreed checks, and any gaps in the assessment. Identify application components, consumers, runtime, infrastructure, deployment process, traffic routing, and dependencies. Check each requirement above, including the current GuCDK minimum, existing EC2 app pattern and snapshot tests, repository secrets and settings, Riff-Raff CloudFormation configuration, and dynamic-configuration paths. Determine how GuCDK or other existing tooling can support ECS deployments.

### 2. Upversion GuCDK and Prepare the Load-Balanced App Pattern

**Branch:** `ec2-to-ecs-02-upversion-cdk`

Complete the GuCDK upversion to the currently required minimum as a separate prerequisite PR, retaining or updating snapshot tests. Use the [Amiable upversion PR](https://github.com/guardian/amiable/pull/979) as a worked example: it replaces `GuEc2AppExperimental` with `GuLoadBalancedAppExperimental`, moves the existing EC2-specific settings under `ec2Props`, and updates references to shared resources such as the load balancer. Its version is historical; do not treat it as the current minimum.

Treat that PR as an example, not a template to copy blindly. In particular, its Google auth configuration changes are Amiable-specific; determine the target service's existing auth setup and use the appropriate approach rather than adopting those settings by default.

### 3. Consolidate Dynamic Configuration S3 Sync

**Branch:** `ec2-to-ecs-03-use-sync-in-user-data`

Before building the ECS image, change EC2 user data to sync all configuration downloaded from S3 from its single agreed configuration prefix into `/etc/gu/s3-sync/`, rather than copying individual configuration files to different paths. ECS must mount this configuration at the same path. Use GuCDK's `allowS3Sync` option in `ec2Props` where required; it grants the `ListBucket` permission needed by `aws s3 sync`. Keep SSM-based configuration on its existing SSM loading path; do not migrate or otherwise change it as part of this step.

Update every consumer of the S3-synced files to use `/etc/gu/s3-sync/`, including application configuration and runtime/build settings (for example `build.sbt`). If updating a consumer to use this directory is not possible, use a symlink only as an absolute last resort and document why it is necessary. Keep non-configuration deployment artifacts, such as a versioned application package, on their existing download path unless the migration specifically calls for changing them. Document any required S3 file relocation for the user rather than moving files yourself. Ask the user to deploy and test the EC2 path changes before moving to ECS.

Use the [Amiable S3 sync PR](https://github.com/guardian/amiable/pull/999) as an example of syncing the `conf/` prefix to `/etc/gu/s3-sync/` and updating application consumers. Adapt the prefix and consumers to the target service; `/etc/gu/s3-sync/` is the required destination, not an Amiable-specific path to replace. Preserve SSM-based configuration loading unchanged.

### 4. Prepare Application Startup for ECS

**Branch:** `ec2-to-ecs-04-improve-startup-for-ecs`

Review AWS SDK credential configuration and use a provider chain compatible with the ECS task role; do not rely on EC2-only instance-profile credentials. Preserve local development credential behavior as appropriate for the application.

Review startup-time work for redundant calls and contention. For example, if a scheduled background refresh runs immediately at startup (zero initial delay), check whether a separate eager fetch duplicates it. Remove duplicate work only after confirming the refresh semantics and that required data is still available when needed. Measure startup locally with isolated dependencies where possible; leave measurements against live dependencies to the user.

Prepare the application for ECS's required read-only filesystem. Inspect application logging configuration, runtime flags, and startup scripts for file logging, including access logs and JVM GC logs where applicable. Replace disk log destinations and file appenders with stdout logging; do not introduce writable log volumes or relax the read-only requirement. Keep dynamic configuration at the agreed mounted path. Report any other required filesystem writes as compatibility issues rather than silently enabling writes.

Use the [Amiable startup PR](https://github.com/guardian/amiable/pull/1000) as a worked example of switching to the default AWS credentials provider chain and removing redundant eager fetches where an immediate scheduled refresh already runs.

### 5. Build and Publish a Docker Image

**Branch:** `ec2-to-ecs-05-build-and-publish-docker-image`

Implement the container build using tooling appropriate to the repository's language and framework; do not assume the service is Scala or use SBT-specific steps unless it is an SBT project. Produce a locally buildable image, then add a CI step to publish it to the appropriate ECR repository. Use the `GU_ARTIFACTS_ROLE_ARN` secret and the team's approved publishing action or workflow. Ensure the CI runner architecture matches the image architecture and ECS runtime requirements.

Provide a local way to run the image with the required configuration. A Docker Compose setup can use a separate config-fetching sidecar that syncs the service's CODE configuration into a shared volume, then starts the application with that volume mounted read-only. Keep credential handling explicit and avoid writing downloaded configuration into the repository. Adapt build, publish, and local-run instructions to the project's stack.

Validate the application container with a read-only filesystem and isolated local configuration/dependency fixtures, without running the AWS config-sync sidecar. Check that it starts and emits logs to stdout without attempting disk logging. If required fixtures are unavailable, document the blocked check for the user rather than accessing AWS.

The [Amiable image publishing PR](https://github.com/guardian/amiable/pull/1002) is a Scala/SBT example: it builds a local image with SBT's Docker plugin, publishes to ECR using `guardian/actions-ecr/push`, and provides Compose with a config-sync sidecar. For Node or other stacks, use equivalent language-appropriate image build tooling rather than copying the SBT configuration.

### 6. Configure and Provision ECS Tasks

**Branch:** `ec2-to-ecs-06-configure-and-provision-ecs-tasks`

**Mandatory:** Achieve ECS provisioning through GuCDK's `ecsProps` on the existing `GuLoadBalancedAppExperimental` instance. Configure the image, task resources, scaling, and S3 configuration through that API. Do not bypass it with hand-built ECS constructs, raw CloudFormation resources, or an alternative provisioning mechanism. If the required behavior is not supported by the installed GuCDK API, report the limitation and ask the user how to proceed rather than implementing a workaround outside `ecsProps`.

Verify in local tests or synthesized task definitions that the application container's root filesystem is read-only. Preserve this requirement through GuCDK's `ecsProps` configuration or its provided defaults; do not disable it.

Configure the ECS task to mount the service's agreed S3 configuration prefix into the single filesystem directory the application expects. Reuse GuCDK's supported S3 configuration mount helper rather than inventing a separate mount mechanism; see the [GuCDK S3 mount implementation](https://github.com/guardian/cdk/pull/2984) and [Amiable ECS S3 mount PR](https://github.com/guardian/amiable/pull/976) as references. Ensure the task has only the required S3 access and that the application reads configuration from the mounted path.

Wire the immutable image identifier or digest produced by CI into the CDK ECS configuration. Because the digest is available only after publishing, configure CI to run the CDK validation/synthesis step after image publication and pass the published identifier into that step. Do not trigger publication yourself. Set the proposed ECS target group weight to 0% while introducing the mount and task definition.

Adapt Amiable's `getDefaultS3ConfigMount` / `ecsProps.s3Config` usage to the target service, bucket, prefix, and mount path. Do not copy its app name, sizing, scaling, or other service-specific settings as defaults.

Propose infrastructure changes using `GuLoadBalancedAppExperimental` that provision ECS resources with ECS receiving 0% of traffic and preserve the existing EC2 service. Do not deploy these changes. Provide the user with deployment and health-check instructions, and obtain user-provided results or confirmation that the deployed ECS service is healthy before proceeding to live validation.

### 7. Load Test, Monitor, and Adjust Configuration

**Branch:** `ec2-to-ecs-07-validate-and-tune-ecs`

Ask the user to decide whether load testing is needed and to agree its scope, environment, load profile, and success criteria. Suggest reviewing the [load-testing guidance document](https://docs.google.com/document/d/1jlQ48FV_6LetQ-iFNxNho2BmqY989wNXsGREpRMr04E/edit?pli=1&tab=t.bx59w21dy2ge#heading=h.mhmh3dxq2tcl). Propose test scripts or instructions where needed, but leave execution against deployed services to the user or responsible team.

Record any decisions and results the user has supplied. Review available user-provided monitoring results and propose configuration adjustments as needed. Do not require completed live testing or confirmation to prepare this branch.

In `docs/ec2-to-ecs/07-validate-and-tune-ecs.md`, include a **Prerequisites for merge** checklist: the user has decided whether load testing is needed; any agreed testing and monitoring have been reviewed; proposed tuning is supported by those results. Mark outstanding items as unchecked rather than blocking branch creation or inventing results.

### 8. Serve Some Traffic with ECS

**Branch:** `ec2-to-ecs-08-serve-partial-traffic-with-ecs`

Propose a weighted-routing configuration change that shifts a user-agreed portion of traffic to ECS while EC2 continues serving the remainder. Ask for the intended traffic split if needed to author the change, but do not require completed testing or cutover approval to create the branch. Include instructions for the user to deploy it and monitor service health and downstream impact. Do not change live traffic or trigger a deployment.

In `docs/ec2-to-ecs/08-serve-partial-traffic-with-ecs.md`, include a **Prerequisites for merge** checklist: ECS is deployed and healthy at 0% traffic; agreed validation (including load testing if chosen) is complete; downstream checks and monitoring are acceptable; the user approves the proposed traffic split and has a rollback procedure.

### 9. Serve All Traffic with ECS

**Branch:** `ec2-to-ecs-09-cut-over-to-ecs`

Propose configuration setting the ECS target group to 100% and EC2 to 0%, preserving the EC2 deployment for the observation period. Do not require testing confirmation or cutover approval to prepare the branch. Include deployment and monitoring instructions; do not perform the cutover yourself. Use the [Amiable ECS cutover PR](https://github.com/guardian/amiable/pull/1005) as an example of changing the weights.

In `docs/ec2-to-ecs/09-cut-over-to-ecs.md`, include a **Prerequisites for merge** checklist:

- The agreed CODE and production partial-traffic tests have passed, with results recorded.
- Downstream regression tests and observability checks have passed and downstream teams have reviewed the results.
- Service health, error rates, latency, and other agreed success signals have remained acceptable for the agreed observation period.
- Monitoring and responders are ready, and a rollback procedure to restore EC2 traffic is available.
- The user has explicitly approved the 100% ECS cutover.

Keep unconfirmed conditions unchecked and clearly state that the user must satisfy them before merging, not before the agent creates the branch.

### 10. Remove EC2

**Branch:** `ec2-to-ecs-10-remove-ec2-service`

Propose removal of EC2 resource definitions on a separate branch from the cutover. This is a potentially destructive infrastructure proposal, but completed observation and removal approval are not required to prepare the branch. Review the infrastructure diff and snapshot changes to confirm exactly which resources would be deleted, and validate the resulting ECS-only configuration locally. Hand over the proposed deletions and deployment instructions; do not delete live resources or trigger teardown. Use the [Amiable EC2 removal PR](https://github.com/guardian/amiable/pull/1006) as an example, not as a fixed observation-period recommendation.

In `docs/ec2-to-ecs/10-remove-ec2-service.md`, include a **Prerequisites for merge** checklist:

- ECS has served 100% of production traffic successfully for the agreed post-cutover observation period.
- Downstream checks and agreed service health, error-rate, and latency signals remain acceptable.
- Service owners and downstream teams agree the EC2 rollback path is no longer needed.
- The user has explicitly approved EC2 resource removal.

Keep unconfirmed conditions unchecked and warn that merging/deploying the proposed change removes the EC2 rollback path.

### Validation and Handoff

Perform validation and handoff on each step's branch; this is not a separate migration step or branch.

Run appropriate local checks, including snapshot tests, and verify proposed dynamic configuration uses the agreed S3 and filesystem locations. Document deployment, observability, and operational handoff instructions for the user. Report local validation separately from any deployed-state evidence, and identify live checks still needed.

## Safety and Approval Gates

Do not treat this migration as a one-step EC2-to-ECS cutover. Proposed changes must preserve the parallel-running period and weighted traffic-shifting approach. For steps 7-10, testing, observation, and operational approvals are prerequisites for merge documented in the branch's PR-description file, not prerequisites for creating the branch. This applies to individual-step requests as well as a full series. Do not claim outstanding conditions are satisfied. Leave all publishing, deployment, traffic changes, and resource deletion to the user or responsible team.

## Expected Outputs

The deliverable for each step requiring work is always a branch containing proposed changes that the user can turn into a PR. These may be documentation or implementation changes, depending on the step. Base the branch on the previous step's branch or `main` as described above; leave PR creation to the user.

When handing over the branch, identify its name and base, summarize the proposed changes, and report validation results and any outstanding decisions or blockers. If the step is already complete and no changes are needed, report that finding and suggest the next step rather than creating an empty branch.

Include a Markdown file at `docs/ec2-to-ecs/<step>-<description>.md` on each branch requiring work, matching that step's branch suffix (for example, `docs/ec2-to-ecs/06-configure-and-provision-ecs-tasks.md`). This is a repository-root-relative path, not an absolute filesystem path. Write it so the user can use its contents as the PR description: summarize the changes, local validation, outstanding gates, and deployment verification instructions.

Use this consistent PR-description structure for every step:

```markdown
## Purpose

What this migration step achieves.

## Base Branch and Dependencies

The actual base branch and any preceding changes this step depends on.

## Proposed Changes

The changes in this step only.

## Local Validation

Commands run and their results, plus checks not run or blocked and why.

## Prerequisites for Merge

An evidence-backed checklist of conditions that must be met before merge.
Leave unconfirmed conditions unchecked.

## Deployment and Verification

Actions for the user or responsible team, followed by read-only verification
commands, expected results, and signs of a problem.
Clearly label commands that the agent has not run.
```

Replace the template guidance with step-specific content. Distinguish user-reported live results from local validation, and do not invent evidence. For cutover and EC2-removal descriptions, add a prominent warning immediately below **Purpose**: cutover changes production traffic; EC2 removal deletes resources and removes the EC2 rollback path. State that the merge prerequisites must be satisfied before the user merges or deploys these changes.

For every step requiring work, add a separate Markdown file at `docs/ec2-to-ecs/<step>-<description>-create-pr.md` beside its PR-description file. Put the optional GitHub CLI PR-creation command in this companion file. Substitute the actual base branch, step branch, descriptive title, and Markdown path rather than leaving placeholders:

```shell
gh pr create --base "<base-branch>" --head "<step-branch>" --title "<step-title>" --body-file "docs/ec2-to-ecs/<step>-<description>.md"
```

In the companion file, tell the user to run the command from the repository root with the step branch checked out (so its description file is available), after pushing the required branches. Set `--base` to the named previous branch if it exists, or to `main` only when the preceding actions are confirmed complete and merged, following the base-selection rules above. For a series, create one companion file per prepared branch. Reference both files in the handoff. Do not execute these commands or include them in the PR-description files themselves.

Where relevant, include read-only AWS CLI commands for the user to check that deployment matches the proposal, such as CloudFormation stack status, ECS service and task health, load-balancer target health and routing weights, or the absence of removed EC2 resources. Derive identifiers and expected results from the repository where possible; otherwise use clearly marked placeholders for the AWS profile, region, stack, cluster, service, or resource identifiers. Explain the expected output and what would indicate a problem. Do not embed credentials or imply that these commands have been run by the agent.

Include any publishing, deployment, settings changes, or live checks the user must perform. Never report that the migration step has been deployed solely because its proposed changes are ready.

For a series, list the branches in order with their bases and outstanding merge/deployment gates so the user can review and apply them sequentially.

## Completion Checklist

Check repository changes against the proposed branch. Items describing live publication, deployment, or testing require user-provided results or confirmation; preparing a branch does not complete those external actions.

- [ ] `riff-raff.yaml` exists and is referenced by the GitHub CI process.
- [ ] The currently required GuCDK minimum (v66.0.0 or later, unless superseded) is in place; the existing EC2 app configuration is represented in the load-balanced app pattern as appropriate, with snapshot tests updated.
- [ ] Cognito is used for applications that require authentication.
- [ ] Required GitHub secret and immutable OIDC setting are available; Riff-Raff can deploy CloudFormation.
- [ ] Dynamic configuration is fetched from one S3 location and read from one filesystem location; any symlink is justified as a last resort.
- [ ] Downstream services and teams are identified, and suitable regression tests and observability are agreed, including how to exercise them in CODE.
- [ ] GuCDK upversion and load-balanced app pattern preparation are completed in a separate PR; service-specific authentication is preserved or intentionally configured.
- [ ] Dynamic configuration is synced from one S3 prefix into one filesystem directory; all consumers use that directory, and `allowS3Sync` permissions are configured as needed.
- [ ] AWS SDK credentials work with the ECS task role, and startup has no confirmed redundant eager/background fetches.
- [ ] Application and runtime logs go only to stdout, with no disk log destinations; the application is compatible with ECS's read-only filesystem and mounted configuration.
- [ ] A language-appropriate Docker image can be built locally and is published to ECR by CI for the required architecture.
- [ ] A documented local container workflow provides required configuration without committing it to the repository.
- [ ] ECS mounts the service's S3 configuration at the agreed filesystem path, with least-required access and CI wiring for the published image digest.
- [ ] ECS provisioning is defined exclusively through `GuLoadBalancedAppExperimental.ecsProps`, with ECS at 0% traffic initially and no parallel, hand-built ECS provisioning mechanism.
- [ ] (Optional) Responses identify the serving target when this aids migration testing.
- [ ] The user's load-testing decision is recorded; any agreed load testing and other validation are complete, and ECS has been monitored and configured before receiving traffic.
- [ ] The cutover PR-description file includes testing, downstream checks, monitoring, rollback readiness, and explicit cutover approval as prerequisites for merge.
- [ ] ECS has successfully served all traffic through the observation period while EC2 remains available for rollback.
- [ ] EC2-removal changes are on a separate branch with observation, downstream/service checks, and explicit removal approval listed as prerequisites for merge; live deletion is left to the user.
- [ ] Each prerequisite and migration step was completed and documented separately.
