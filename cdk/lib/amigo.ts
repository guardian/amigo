import { AccessScope } from '@guardian/cdk/lib/constants';
import type {
	AppIdentity,
	GuStackProps,
} from '@guardian/cdk/lib/constructs/core';
import {
	GuDistributionBucketParameter,
	GuStack,
	GuStringParameter,
} from '@guardian/cdk/lib/constructs/core';
import { GuCname } from '@guardian/cdk/lib/constructs/dns';
import {
	GuHttpsEgressSecurityGroup,
	GuSecurityGroup,
	GuVpc,
} from '@guardian/cdk/lib/constructs/ec2';
import {
	GuAllowPolicy,
	GuAnghammaradSenderPolicy,
	GuDescribeEC2Policy,
	GuLogShippingPolicy,
	GuPolicy,
} from '@guardian/cdk/lib/constructs/iam';
import { GuS3Bucket } from '@guardian/cdk/lib/constructs/s3';
import {
	getDefaultS3ConfigMount,
	GuLoadBalancedAppExperimental,
} from '@guardian/cdk/lib/experimental/patterns/gu-load-balanced-app';
import { Aws, Duration, SecretValue } from 'aws-cdk-lib';
import type { App } from 'aws-cdk-lib';
import { Peer, Port } from 'aws-cdk-lib/aws-ec2';
import { CfnTaskDefinition } from 'aws-cdk-lib/aws-ecs';
import type { Volume } from 'aws-cdk-lib/aws-ecs';
import {
	ListenerAction,
	UnauthenticatedAction,
} from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import { AccountPrincipal, Effect, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import type { Bucket } from 'aws-cdk-lib/aws-s3';
import {
	ParameterDataType,
	ParameterTier,
	StringParameter,
} from 'aws-cdk-lib/aws-ssm';
import { createAmigoDeveloperPolicy } from './amigo-developer-policy';

export interface AmigoProps extends GuStackProps {
	domainName: string;
	imageDigest: string;
}

export class AmigoStack extends GuStack {
	private static app: AppIdentity = {
		app: 'amigo',
	};

	private readonly dataBucket: Bucket;
	private readonly packerInstanceProfile: GuStringParameter;

	private get appPolicy(): GuPolicy {
		return new GuPolicy(this, 'AppPolicy', {
			policyName: 'app-policy',
			statements: [
				/*
		Permissions to enable listing of installed packages created during a bake
		See https://github.com/guardian/amigo/pull/395
		 */
				new PolicyStatement({
					effect: Effect.ALLOW,
					actions: ['s3:GetObject'],
					resources: [`${this.dataBucket.bucketArn}/*`],
				}),

				/*
		AMIgo uses DynamoDb as a data store.
		The permissions are quite wide, mainly because AMIgo creates tables as well as reading/writing data.
		See `app/data/Dynamo.scala`
		 */
				new PolicyStatement({
					effect: Effect.ALLOW,
					actions: ['dynamodb:ListTables'],
					resources: ['*'],
				}),
				new PolicyStatement({
					effect: Effect.ALLOW,
					actions: ['dynamodb:*'],
					resources: [`arn:aws:dynamodb:*:*:table/amigo-${this.stage}-*`],
				}),

				/*
		Permissions to support encrypted bakes
		See https://github.com/guardian/amigo/pull/164
		 */
				new PolicyStatement({
					effect: Effect.ALLOW,
					actions: ['sns:ListTopics'],
					resources: ['*'],
				}),

				/*
		Permissions to trigger AMI deletion
		See https://github.com/guardian/amigo/pull/193
		 */
				new PolicyStatement({
					effect: Effect.ALLOW,
					actions: ['sns:*'],
					resources: [
						`arn:aws:sns:*:*:amigo-${this.stage}-notify`,
						`arn:aws:sns:*:*:amigo-${this.stage}-housekeeping-notify`,
					],
				}),

				/*
		Allow us to allow other accounts to retrieve the ImageCopier lambda artifact
		 */
				new PolicyStatement({
					effect: Effect.ALLOW,
					actions: ['s3:GetBucketPolicy', 's3:PutBucketPolicy'],
					resources: [
						`arn:aws:s3::*:${GuDistributionBucketParameter.getInstance(this).valueAsString}`,
					],
				}),

				/*
		See https://github.com/guardian/amigo/pull/526
		 */
				new PolicyStatement({
					effect: Effect.ALLOW,
					actions: ['iam:GetInstanceProfile'],
					resources: [this.packerInstanceProfile.valueAsString],
				}),
			],
		});
	}

	constructor(scope: App, id: string, props: AmigoProps) {
		super(scope, id, props);

		const { domainName, imageDigest } = props;
		if (!/^sha256:[a-f0-9]{64}$/.test(imageDigest)) {
			throw new Error('AMIgo requires an immutable SHA-256 image digest');
		}

		this.packerInstanceProfile = new GuStringParameter(
			this,
			'PackerInstanceProfile',
			{
				description:
					'Instance profile given to instances created by Packer. Find this in the PackerUser-PackerRole in IAM',
			},
		);

		if (this.stage === 'CODE') {
			const devDataBucket = new GuS3Bucket(this, 'DevDataBucket', {
				app: AmigoStack.app.app,
				bucketName: 'amigo-data-dev',
			});
			devDataBucket.addToResourcePolicy(
				new PolicyStatement({
					effect: Effect.ALLOW,
					principals: [new AccountPrincipal(Aws.ACCOUNT_ID)],
					actions: ['s3:PutObject'],
					resources: [devDataBucket.arnForObjects('packagelists/*')],
					conditions: {
						ArnLike: {
							'aws:PrincipalArn': `arn:${Aws.PARTITION}:iam::${Aws.ACCOUNT_ID}:role/PackerUser-PackerRole-*`,
						},
					},
				}),
			);

			createAmigoDeveloperPolicy(
				this,
				this.packerInstanceProfile,
				devDataBucket,
			);
		}

		this.dataBucket = new GuS3Bucket(this, 'AmigoDataBucket', {
			app: AmigoStack.app.app,
			bucketName: `amigo-data-${this.stage.toLowerCase()}`,
		});

		this.overrideLogicalId(this.dataBucket, {
			logicalId: 'AmigoDataBucket',
			reason: 'To prevent orphaning of the YAML defined bucket',
		});

		const ssmPolicy = new GuPolicy(this, 'SSMPolicy', {
			statements: [
				new PolicyStatement({
					effect: Effect.ALLOW,
					resources: ['*'],
					actions: [
						// Standard SSM permissions
						'ec2messages:AcknowledgeMessage',
						'ec2messages:DeleteMessage',
						'ec2messages:FailMessage',
						'ec2messages:GetEndpoint',
						'ec2messages:GetMessages',
						'ec2messages:SendReply',
						'ssm:UpdateInstanceInformation',
						'ssm:ListInstanceAssociations',
						'ssm:DescribeInstanceProperties',
						'ssm:DescribeDocumentParameters',
						'ssmmessages:CreateControlChannel',
						'ssmmessages:CreateDataChannel',
						'ssmmessages:OpenControlChannel',
						'ssmmessages:OpenDataChannel',

						// required to allow Packer to run SSM commands
						// see https://github.com/guardian/amigo/pull/526 and https://github.com/guardian/amigo/pull/538
						'ssm:StartSession',
						'ssm:TerminateSession',
					],
				}),
			],
		});

		const additionalPolicies: GuPolicy[] = [
			ssmPolicy,
			GuLogShippingPolicy.getInstance(this),
			new GuAllowPolicy(this, 'PackerPolicy', {
				policyName: 'packer-required-permissions',
				resources: ['*'],
				actions: [
					'ec2:AttachVolume',
					'ec2:AuthorizeSecurityGroupIngress',
					'ec2:CopyImage',
					'ec2:CreateImage',
					'ec2:CreateKeypair',
					'ec2:CreateSecurityGroup',
					'ec2:CreateSnapshot',
					'ec2:CreateTags',
					'ec2:CreateVolume',
					'ec2:DeleteKeypair',
					'ec2:DeleteSecurityGroup',
					'ec2:DeleteSnapshot',
					'ec2:DeleteVolume',
					'ec2:DeregisterImage',
					'ec2:DescribeImageAttribute',
					'ec2:DescribeImages',
					'ec2:DescribeInstances',
					'ec2:DescribeRegions',
					'ec2:DescribeSecurityGroups',
					'ec2:DescribeSnapshots',
					'ec2:DescribeSubnets',
					'ec2:DescribeTags',
					'ec2:DescribeVolumes',
					'ec2:DetachVolume',
					'ec2:GetPasswordData',
					'ec2:ModifyImageAttribute',
					'ec2:ModifyInstanceAttribute',
					'ec2:ModifySnapshotAttribute',
					'ec2:RegisterImage',
					'ec2:RunInstances',
					'ec2:StopInstances',
					'ec2:TerminateInstances',
					'iam:PassRole',
				],
			}),
			GuDescribeEC2Policy.getInstance(this),
			GuAnghammaradSenderPolicy.getInstance(this),
			this.appPolicy,
		];

		const sg = new GuSecurityGroup(this, 'PackerSecurityGroup', {
			...AmigoStack.app,
			vpc: GuVpc.fromIdParameter(this, 'vpc'),

			// The security group name is added to config.
			// See `app/packer/PackerConfig.scala`.
			securityGroupName: `amigo-packer-${this.stage}`,

			description: 'Security group for instances created by Packer',

			// When true, `allowAllOutbound` will also allow all outbound UDP traffic
			allowAllOutbound: false,
			egresses: [
				{
					port: Port.tcpRange(0, 65535),
					range: Peer.anyIpv4(),
					description: 'Allow all outbound TCP',
				},
			],
		});

		this.overrideLogicalId(sg, {
			logicalId: 'PackerSecurityGroup',
			reason:
				"Keeping the same resource for simplicity. We would otherwise have to update the stack when there are no ongoing bakes, i.e. when the security group isn't in use.",
		});

		const amigoApp = new GuLoadBalancedAppExperimental(this, {
			...AmigoStack.app,
			applicationPort: 9000,
			ecsProps: {
				imageIdentifier: imageDigest,
				repositoryName: 'guardian/amigo/amigo',
				cpu: 2048,
				memoryLimitMiB: 4096,
				scaling: { minimumTasks: 1, maximumTasks: 1 },
				s3Config: getDefaultS3ConfigMount(this, AmigoStack.app),
			},
			access: {
				scope: AccessScope.PUBLIC,
			},
			certificateProps: { domainName },
			monitoringConfiguration: {
				noMonitoring: true,
			},
			additionalPolicies,
		});

		const taskDefinition = amigoApp.ecsService?.taskDefinition;
		const appContainer = taskDefinition?.defaultContainer;
		if (!taskDefinition || !appContainer) {
			throw new Error(
				'AMIgo ECS task or application container was not created',
			);
		}

		const tmpVolume: Volume = { name: 'tmp-volume' };
		taskDefinition.addVolume(tmpVolume);
		appContainer.addMountPoints({
			containerPath: '/tmp',
			sourceVolume: tmpVolume.name,
			readOnly: false,
		});

		const cfnTaskDefinition = taskDefinition.node.defaultChild;
		if (!(cfnTaskDefinition instanceof CfnTaskDefinition)) {
			throw new Error(
				'AMIgo ECS task definition has no CloudFormation resource',
			);
		}
		// GuCDK v66 overrides Volumes, hiding volumes registered through addVolume.
		// Rendering includes those overrides; the public volumes property does not.
		const renderedTemplate: unknown = this.resolve(
			cfnTaskDefinition._toCloudFormation(),
		);
		if (
			typeof renderedTemplate !== 'object' ||
			renderedTemplate === null ||
			!('Resources' in renderedTemplate) ||
			typeof renderedTemplate.Resources !== 'object' ||
			renderedTemplate.Resources === null
		) {
			throw new Error('AMIgo ECS task definition could not be rendered');
		}
		const renderedResource: unknown = Object.values(
			renderedTemplate.Resources,
		)[0];
		if (
			typeof renderedResource !== 'object' ||
			renderedResource === null ||
			!('Properties' in renderedResource) ||
			typeof renderedResource.Properties !== 'object' ||
			renderedResource.Properties === null ||
			!('Volumes' in renderedResource.Properties) ||
			!Array.isArray(renderedResource.Properties.Volumes)
		) {
			throw new Error('AMIgo ECS task definition has no rendered volumes');
		}
		const existingVolumes: unknown[] = renderedResource.Properties.Volumes;
		cfnTaskDefinition.addPropertyOverride('Volumes', [
			...existingVolumes,
			{ Name: tmpVolume.name },
		]);

		// Ensure LB can egress to 443 (for Google endpoints) for OIDC flow.
		const albEgressSg = new GuHttpsEgressSecurityGroup(this, 'IdP-access', {
			app: AmigoStack.app.app,
			vpc: amigoApp.vpc,
		});

		amigoApp.loadBalancer.addSecurityGroup(albEgressSg);

		// This parameter is used by https://github.com/guardian/waf
		new StringParameter(this, 'AlbSsmParam', {
			parameterName: `/infosec/waf/services/${this.stage}/amigo-alb-arn`,
			description: `The arn of the ALB for amigo-${this.stage}. N.B. this parameter is created via cdk`,
			simpleName: false,
			stringValue: amigoApp.loadBalancer.loadBalancerArn,
			tier: ParameterTier.STANDARD,
			dataType: ParameterDataType.TEXT,
		});

		const clientId = new GuStringParameter(this, 'ClientId', {
			description: 'Google OAuth client ID',
		});

		const ecsTargetGroup = amigoApp.targetGroups.ecs;
		if (!ecsTargetGroup) {
			throw new Error('AMIgo ECS target group was not created');
		}

		const authenticate = (next: ListenerAction) =>
			ListenerAction.authenticateOidc({
				authorizationEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
				issuer: 'https://accounts.google.com',
				scope: 'openid',
				authenticationRequestExtraParams: { hd: 'guardian.co.uk' },
				onUnauthenticatedRequest: UnauthenticatedAction.AUTHENTICATE,

				tokenEndpoint: 'https://oauth2.googleapis.com/token',

				userInfoEndpoint: 'https://openidconnect.googleapis.com/v1/userinfo',
				clientId: clientId.valueAsString,
				clientSecret: SecretValue.secretsManager(
					`/${this.stage}/deploy/amigo/clientSecret`,
				),
				next,
			});

		amigoApp.listener.addAction('Google Auth', {
			action: authenticate(ListenerAction.forward([ecsTargetGroup])),
		});

		new GuCname(this, 'DnsRecord', {
			app: AmigoStack.app.app,
			domainName: domainName,
			ttl: Duration.hours(1),
			resourceRecord: amigoApp.loadBalancer.loadBalancerDnsName,
		});
	}
}
