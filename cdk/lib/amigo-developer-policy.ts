import { GuDistributionBucketParameter } from '@guardian/cdk/lib/constructs/core';
import type { GuStack } from '@guardian/cdk/lib/constructs/core';
import { GuDeveloperPolicyExperimental } from '@guardian/cdk/lib/experimental/constructs/iam/policies';
import { Aws, Fn } from 'aws-cdk-lib';
import { Effect, PolicyStatement } from 'aws-cdk-lib/aws-iam';

const region = 'eu-west-1';

const allow = (
	actions: string[],
	resources: string[],
	conditions?: Record<string, Record<string, string | string[]>>,
) =>
	new PolicyStatement({
		effect: Effect.ALLOW,
		actions,
		resources,
		conditions,
	});

export const createAmigoDeveloperPolicy = (
	scope: GuStack,
	packerInstanceProfileArn: string,
): GuDeveloperPolicyExperimental => {
	const account = Aws.ACCOUNT_ID;
	const partition = Aws.PARTITION;
	const stack = scope.stack;
	const distributionBucket =
		GuDistributionBucketParameter.getInstance(scope).valueAsString;
	const packerRoleArn = Fn.join(
		':role/',
		Fn.split(':instance-profile/', packerInstanceProfileArn),
	);
	const ec2Arn = (resourceType: string) =>
		`arn:${partition}:ec2:${region}:${account}:${resourceType}/*`;
	const imageArn = `arn:${partition}:ec2:${region}::image/*`;
	const snapshotArn = `arn:${partition}:ec2:${region}::snapshot/*`;
	const instanceArn = ec2Arn('instance');
	const keyPairArn = ec2Arn('key-pair');
	const networkInterfaceArn = ec2Arn('network-interface');
	const securityGroupArn = ec2Arn('security-group');
	const subnetArn = ec2Arn('subnet');
	const volumeArn = ec2Arn('volume');
	const vpcArn = ec2Arn('vpc');
	const packerResourceConditions = {
		StringEquals: {
			'aws:ResourceTag/AmigoStage': 'DEV',
			'aws:ResourceTag/Stack': 'amigo-packer',
			'ec2:Region': region,
		},
	};
	const packerRequestConditions = {
		StringEquals: {
			'aws:RequestTag/AmigoStage': 'DEV',
			'aws:RequestTag/Stack': 'amigo-packer',
			'ec2:Region': region,
		},
	};
	const regionCondition = {
		StringEquals: { 'ec2:Region': region },
	};

	return new GuDeveloperPolicyExperimental(scope, 'AmigoDeveloperPolicy', {
		grantId: 'amigo-dev',
		friendlyName: 'Develop and test Amigo against DEV resources',
		withoutPolicyChecks: true,
		statements: [
			// Read and update Amigo configuration.
			allow(
				[
					// 'ssm:GetParameter',
					'ssm:GetParametersByPath',
					'ssm:GetParameters',
					'ssm:PutParameter',
				],
				[
					`arn:${partition}:ssm:${region}:${account}:parameter/DEV/${stack}/amigo`,
					`arn:${partition}:ssm:${region}:${account}:parameter/DEV/${stack}/amigo/*`,
					`arn:${partition}:ssm:${region}:${account}:parameter/CODE/${stack}/amigo/aws.distributionBucket`,
				],
			),
			// Update distribution access for image copier artefacts.
			allow(
				['s3:GetBucketPolicy', 's3:PutBucketPolicy'],
				[`arn:${partition}:s3:::${distributionBucket}`],
			),
			// Download bootstrap certificates and package lists.
			allow(
				['s3:GetObject'],
				[
					`arn:${partition}:s3:::${distributionBucket}/${stack}/CODE/amigo/conf/amigo-service-account-cert.json`,
					`arn:${partition}:s3:::amigo-data-dev/*`,
				],
			),
			// Discover existing Amigo tables.
			allow(['dynamodb:ListTables'], ['*']),
			// Persist Amigo application data.
			allow(
				[
					'dynamodb:BatchWriteItem',
					'dynamodb:CreateTable',
					'dynamodb:DeleteItem',
					'dynamodb:DescribeTable',
					'dynamodb:GetItem',
					'dynamodb:PutItem',
					'dynamodb:Query',
					'dynamodb:Scan',
					'dynamodb:UpdateItem',
				],
				[
					`arn:${partition}:dynamodb:${region}:${account}:table/amigo-DEV-*`,
					`arn:${partition}:dynamodb:${region}:${account}:table/amigo-DEV-*/index/*`,
				],
			),
			// Discover existing Amigo topics.
			allow(['sns:ListTopics'], ['*']),
			// Manage and publish Amigo notifications.
			allow(
				[
					'sns:AddPermission',
					'sns:CreateTopic',
					'sns:Publish',
					'sns:RemovePermission',
				],
				[
					`arn:${partition}:sns:${region}:${account}:amigo-DEV-notify`,
					`arn:${partition}:sns:${region}:${account}:amigo-DEV-housekeeping-notify`,
				],
			),
			// Resolve the current AWS account.
			allow(['sts:GetCallerIdentity'], ['*']),
			// Inspect EC2 resources during Packer builds.
			allow(['ec2:Describe*'], ['*'], regionCondition),
			// Create tagged resources used by Packer.
			allow(
				[
					'ec2:CopyImage',
					'ec2:CreateImage',
					'ec2:CreateKeyPair',
					'ec2:CreateSecurityGroup',
					'ec2:CreateSnapshot',
					'ec2:CreateVolume',
					'ec2:RegisterImage',
				],
				[imageArn, keyPairArn, securityGroupArn, snapshotArn, volumeArn],
				packerRequestConditions,
			),
			// Launch tagged Packer builders and attachments.
			allow(
				['ec2:RunInstances'],
				[instanceArn, networkInterfaceArn, volumeArn],
				packerRequestConditions,
			),
			// Apply tags only while Packer creates resources.
			allow(
				['ec2:CreateTags'],
				[
					imageArn,
					instanceArn,
					keyPairArn,
					networkInterfaceArn,
					securityGroupArn,
					snapshotArn,
					volumeArn,
				],
				{
					StringEquals: {
						...packerRequestConditions.StringEquals,
						'ec2:CreateAction': [
							'CopyImage',
							'CreateImage',
							'CreateKeyPair',
							'CreateSecurityGroup',
							'CreateSnapshot',
							'CreateVolume',
							'RegisterImage',
							'RunInstances',
						],
					},
				},
			),
			// Create AMIs from tagged Packer builders.
			allow(['ec2:CreateImage'], [instanceArn], packerResourceConditions),
			// Snapshot tagged Packer volumes.
			allow(['ec2:CreateSnapshot'], [volumeArn], packerResourceConditions),
			// Register images from tagged Packer snapshots.
			allow(['ec2:RegisterImage'], [snapshotArn], packerResourceConditions),
			// Create temporary security groups in the build VPC.
			allow(['ec2:CreateSecurityGroup'], [vpcArn], regionCondition),
			// Create build volumes from source snapshots.
			allow(['ec2:CreateVolume'], [snapshotArn], regionCondition),
			// Use existing resources when launching builders.
			allow(
				['ec2:RunInstances'],
				[imageArn, keyPairArn, securityGroupArn, snapshotArn, subnetArn],
				regionCondition,
			),
			// Retrieve Windows builder passwords when required.
			allow(['ec2:GetPasswordData'], [instanceArn], packerResourceConditions),
			// Manage and clean up tagged Packer resources.
			allow(
				[
					'ec2:AttachVolume',
					'ec2:AuthorizeSecurityGroupIngress',
					'ec2:DeleteKeyPair',
					'ec2:DeleteSecurityGroup',
					'ec2:DeleteSnapshot',
					'ec2:DeleteVolume',
					'ec2:DeregisterImage',
					'ec2:DetachVolume',
					'ec2:ModifyImageAttribute',
					'ec2:ModifyInstanceAttribute',
					'ec2:ModifySnapshotAttribute',
					'ec2:StopInstances',
					'ec2:TerminateInstances',
				],
				[
					imageArn,
					instanceArn,
					keyPairArn,
					securityGroupArn,
					snapshotArn,
					volumeArn,
				],
				packerResourceConditions,
			),
			// Provision builders through Session Manager.
			allow(['ssm:StartSession', 'ssm:TerminateSession'], ['*']),
			// Validate the configured Packer instance profile.
			allow(['iam:GetInstanceProfile'], [packerInstanceProfileArn]),
			// Attach the configured role to EC2 builders.
			allow(['iam:PassRole'], [packerRoleArn], {
				StringEquals: { 'iam:PassedToService': 'ec2.amazonaws.com' },
			}),
		],
	});
};
