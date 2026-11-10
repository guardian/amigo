## What does this change?

<!-- A PR should have enough detail to be understandable far in the future. e.g what is the problem/why is the change needed, how does it solve it and any questions or points of discussion. Prefer copying information from a Trello card over linking to it; the card may not always exist and reviewers may not have access to the board. -->

## Purpose

**WARNING: deploying this proposal deletes the application EC2 resources
and removes the EC2 rollback path. Satisfy every merge prerequisite before
merging or deploying. Preparing this branch is not removal approval.**

Remove AMIgo's EC2 application deployment after the agreed post-cutover
observation period. Retain the EC2 infrastructure and permissions used by
Packer to bake AMIs.

## Base Branch and Dependencies

Base branch: `ec2-to-ecs-09-cut-over-to-ecs`.

Step 9 proposes 100% ECS traffic while retaining EC2; no live cutover,
observation results or removal approval have been supplied. Step 7's CODE
load profile and criteria remain drafts requiring owner agreement, including
the maximum of two concurrent bakes.

Resolve and propagate predecessor gates before deployment: GuCDK minimum
67.1.0 (the inherited dependency is still 66.0.0), Managed Instances support,
authentication/Cognito integration, task-role/S3 permissions, external
configuration and read-only scratch compatibility, scheduler/housekeeping
ownership, and required repository publishing/deployment settings.
This step deliberately does not silently upgrade GuCDK or replace auth.

## Proposed Changes

- Remove `ec2Props`, application user data, instance metrics settings and
  weighted/deterministic routing from `GuLoadBalancedAppExperimental`.
  Provision ECS exclusively through the existing `ecsProps`.
- Keep Google OIDC on the default listener and forward directly to ECS.
  Remove both deterministic header rules; `X-Gu-Target-Group: ec2` no longer
  selects EC2. Retain the diagnostic `X-Gu-Target` response header.
- Preserve ECS sizing, ARM64 digest wiring, read-only root/configuration,
  writable `/tmp`, and actual library-owned volume definitions.
- Remove Riff-Raff's autoscaling deployment and AMI-selection parameters,
  Debian packaging in CI, and the Debian artifact upload. Retain
  CloudFormation deployment, Docker build/publication and image-copier zip.
- Retain Packer binaries/plugins in the image, the root `packer-version`,
  `PackerSecurityGroup`, `PackerInstanceProfile` parameter, bake permissions,
  application storage, and the other stacks. No bake EC2 resources are
  intentionally removed.

### Reviewed deletion inventory

The PROD snapshot removes these 15 resource definitions. CODE/PROD tests
also require no application ASG, launch template/configuration, instance
profile or deterministic listener rules. CODE-specific storage remains.
Review the actual stage-specific CloudFormation change set before execution;
the PROD logical IDs below are not claims about live state.

| Logical ID | CloudFormation type (`AWS::` prefix omitted) |
|---|---|
| `AutoScalingGroupAmigoASG5BE72894` | `AutoScaling::AutoScalingGroup` |
| `deployPRODamigo77EB3561` | `EC2::LaunchTemplate` |
| `deployPRODamigoProfileFBA18EAC` | `IAM::InstanceProfile` |
| `InstanceRoleAmigo75944747` | `IAM::Role` |
| `GetDistributablePolicyAmigoB25A5D2B` | `IAM::Policy` |
| `GuListBucketPolicyAmigo094DC318` | `IAM::Policy` |
| `SsmSshPolicy4CFC977E` | `IAM::Policy` |
| `GuHttpsEgressSecurityGroupAmigo28861996` | `EC2::SecurityGroup` |
| `GuHttpsEgressSecurityGroupAmigofromAMIgoIdPaccessAmigo4AE1B4709000BF8805C1` | `EC2::SecurityGroupIngress` |
| `GuHttpsEgressSecurityGroupAmigofromAMIgoLoadBalancerAmigoSecurityGroup730D090E90006200512B` | `EC2::SecurityGroupIngress` |
| `IdPaccessAmigotoAMIgoGuHttpsEgressSecurityGroupAmigo34A5A83890007FBC5E83` | `EC2::SecurityGroupEgress` |
| `LoadBalancerAmigoSecurityGrouptoAMIgoGuHttpsEgressSecurityGroupAmigo34A5A8389000E5CCAB10` | `EC2::SecurityGroupEgress` |
| `ListenerAmigoDeterministicRouteToEc2Rule091623C8` | `ElasticLoadBalancingV2::ListenerRule` |
| `ListenerAmigoDeterministicRouteToEcsRuleA0A9F93B` | `ElasticLoadBalancingV2::ListenerRule` |
| `TargetGroupAmigoB9501F07` | `ElasticLoadBalancingV2::TargetGroup` |

The obsolete `AMIAmigo` parameter is also removed. ASG removal
terminates its managed application instances; those instances are not
separate resource definitions in the snapshot.

Surviving IAM policies lose only the removed application instance-role
attachment; ECS task-role attachments and permission statements remain.
The ECS service loses dependencies on the two removed listener rules.
The HTTPS listener changes from 0:100 weighted forwarding to direct ECS
forwarding with OIDC unchanged. Metadata reflects removal of EC2 constructs.
No new resources or changes to surviving task definitions, bake security
group, data storage, DNS or ALB definitions appear in the snapshot.

## How to test

<!-- Provide instructions to help others verify the change. This could take the form of "On PROD, do X and witness Y. On this branch, do X and witness Z. " -->

## Local Validation

- `cd cdk && npm run lint -- --quiet` - passed.
- `cd cdk && npm test -- --runInBand --updateSnapshot` - passed; seven
  tests, four snapshots, one updated. CODE/PROD assertions cover ECS-only
  service/routing, retained Packer infrastructure and filesystem mounts.
- `cd cdk && npx tsc --noEmit --moduleResolution node` - passed.
- `bash -n script/ci` - passed; the script was not executed.
- `cd cdk && node -e "const yaml = require('js-yaml'), fs = require('fs'); for (const p of ['../riff-raff.yaml', '../.github/workflows/ci.yaml']) { yaml.load(fs.readFileSync(p, 'utf8')); console.log(p + ': YAML parsed'); }"` - passed.
- Reviewed the snapshot resource inventory and surviving-resource changes.
- `git diff --check` - passed.
- No AWS reads, publication, deployment, application startup or live tests
  were performed. Tests synthesize with the existing mocks and test-only
  image digest. Actual-digest synthesis/publication remains for normal CI.

## Deployment and Verification

Owner instructions only; the agent has not executed these actions.

Record the successful Step 9 observation period, downstream review,
explicit removal approval, known-good ECS build/digest, responder and
recovery procedure. Check for active EC2-hosted bakes, subprocesses,
scheduled jobs and in-flight requests; traffic weight zero does not stop
background work. Finish or safely transfer that work before terminating
application instances. Do not terminate Packer bake instances or remove
their security group/profile.

Deploy CODE through Riff-Raff first. Review its full CloudFormation change
set against the inventory, including role deletion, and reject unexpected
deletions/replacements of ECS, Packer, storage, ALB or DNS resources. Verify
authenticated UI/API flows, configuration reads, approved controlled bakes,
temporary-resource cleanup, stdout logging and single-owner background
jobs. Review CPU/memory/scratch headroom, restarts, errors and latency using
the agreed criteria. Obtain separate PROD approval after recording CODE
results, then repeat the change-set review and checks in PROD.

Capture the old application ASG and EC2 target-group identifiers before
deployment. Discover the retained ECS/ALB identifiers from the stage's
stack resources. Substitute real identifiers in these **unrun, read-only**
commands; angle-bracket fields are placeholders:

```shell
aws cloudformation describe-stacks \
  --stack-name <amigo-CODE_OR_amigo-PROD> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'Stacks[0].StackStatus'
aws cloudformation list-stack-resources \
  --stack-name <amigo-CODE_OR_amigo-PROD> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'StackResourceSummaries[*].{id:LogicalResourceId,type:ResourceType,status:ResourceStatus,physicalId:PhysicalResourceId}'
aws autoscaling describe-auto-scaling-groups \
  --auto-scaling-group-names <OLD_APPLICATION_ASG_NAME> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'AutoScalingGroups'
aws ecs describe-services \
  --cluster <STAGE_CLUSTER> --services <STAGE_SERVICE> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query '{failures:failures,services:services[*].{desired:desiredCount,running:runningCount,pending:pendingCount,deployments:deployments,events:events[:10]}}'
aws elbv2 describe-listeners \
  --load-balancer-arn <STAGE_ALB_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE> \
  --query 'Listeners[?Port==`443`].DefaultActions'
aws elbv2 describe-rules \
  --listener-arn <STAGE_HTTPS_LISTENER_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE>
aws elbv2 describe-target-health \
  --target-group-arn <STAGE_ECS_TARGET_GROUP_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE>
aws elbv2 describe-target-groups \
  --target-group-arns <OLD_APPLICATION_EC2_TARGET_GROUP_ARN> \
  --region eu-west-1 --profile <AWS_PROFILE>
```

Expect a completed stack update, no active resources from the deletion
inventory, no old ASG (`[]`), retained `PackerSecurityGroup`, one healthy ECS
service at desired count 1 with no failures, healthy ECS targets, OIDC then
direct ECS forwarding, and only the default listener rule. The old EC2
target-group lookup should return `TargetGroupNotFound`; authorization or
network failures are not deletion evidence. If CloudFormation reports
deleted-resource history, distinguish it from active resources.
Unexpected retained resources, deletion failures or unhealthy ECS block
completion. An authenticated application response should identify
`X-Gu-Target: ecs`; a login redirect alone is not serving-target evidence.

### Recovery after EC2 removal

There is no EC2 weight or forced-header rollback after this deployment.
Prefer a reviewed known-good ECS build when appropriate. Returning to EC2
requires recreating its ASG, launch template, IAM role/profile, networking
and target/routing resources, restoring the AMI/package deployment path,
waiting for health, then deliberately restoring traffic. Review the
complete prior build/change set and current shared-state compatibility;
do not assume deploying an older build only changes weights. Preserve
active ECS bakes/jobs during recovery and involve the agreed responders.

## What is the value of this?

<!-- Why are these changes being made? -->

Retires duplicate application capacity and the obsolete Debian deployment
path while preserving AMIgo's EC2-based AMI-baking capability.

## Have we considered potential risks?

<!-- What are the potential risks and how can they be mitigated? Does an error require an alarm? Should user help, infosec, or legal be informed of this change? Is private information guarded? Do we need to add anything in the backlog? -->

Deletes the application instance role/profile and EC2 rollback resources.
Request review from the impacted service owners and downstream teams
(DevX and Security). No recipes or Packer bake roles are removed. Stopping
EC2 background work can interrupt bakes even though it receives no normal
traffic. Library upgrades must revalidate the override-aware volume
extension; this proposal does not resolve inherited compatibility gates.

## Prerequisites for Merge

- [ ] ECS has served 100% of production traffic successfully for the agreed
  post-cutover observation period, with recorded evidence.
- [ ] Downstream checks and agreed service health, error-rate, latency,
  resource and bake-success signals remain acceptable.
- [ ] Service owners and downstream teams agree the EC2 rollback path is
  no longer needed and approve the recovery procedure.
- [ ] The user explicitly approves EC2 resource removal; requesting this
  proposal is not operational removal approval.
- [ ] All predecessor compatibility, publishing, permissions, auth,
  scheduler and validation gates are resolved and propagated.
- [ ] Active application-EC2 work is drained safely; retained bake
  infrastructure and single-owner job behavior are verified.
- [ ] CI passes, exact stage change sets are reviewed, and CODE verification
  precedes separately approved PROD teardown.

All unconfirmed gates remain unchecked. No live migration completion is
claimed.

> **Note**
> If this change adds, updates or removes a role or recipe please note that in your PR and assign appropriate reviewers for the team that will be impacted by those changes.
