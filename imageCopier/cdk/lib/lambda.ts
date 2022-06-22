import type { GuStackProps } from '@guardian/cdk/lib/constructs/core';
import { GuStack } from '@guardian/cdk/lib/constructs/core';
import type { App } from 'aws-cdk-lib';
import { CfnCondition, CfnParameter, Duration, Fn } from 'aws-cdk-lib';
import { Effect, PolicyStatement } from 'aws-cdk-lib/aws-iam';
import { Code, Function, Runtime } from 'aws-cdk-lib/aws-lambda';
import { SnsEventSource } from 'aws-cdk-lib/aws-lambda-event-sources';
import { Bucket } from 'aws-cdk-lib/aws-s3';
import { Topic } from 'aws-cdk-lib/aws-sns';

export class Lambda extends GuStack {
	constructor(scope: App, id: string, props: GuStackProps) {
		super(scope, id, props);

		const functionCodeBucketParam = new CfnParameter(
			this,
			'FunctionCodeBucket',
			{
				description: 'The S3 bucket from which to download the lambda function',
				type: 'String',
				default: 'deploy-tools-dist',
			},
		);

		const functionCodeBucket = Bucket.fromBucketName(
			this,
			'function-code-bucket',
			functionCodeBucketParam.valueAsString,
		);

		const kmsKeyArnParam = new CfnParameter(this, 'KmsKeyArn', {
			description: 'Override the default KMS key if required',
			type: 'String',
			default: '',
		});

		new CfnCondition(this, 'ImportKmsKeyArn', {
			expression: Fn.conditionEquals(kmsKeyArnParam.valueAsString, ''),
		});

		const kmsKeyArn = Fn.conditionIf(
			'ImportKmsKeyArn',
			Fn.importValue('amigo-imagecopier-key'),
			kmsKeyArnParam.valueAsString,
		).toString();

		const encryptedTagValueParam = new CfnParameter(this, 'EncryptedTagValue', {
			description: 'The value of the Encrypted tag on the created AMI',
			type: 'String',
			default: true,
		});

		const housekeepingTopicParam = new CfnParameter(
			this,
			'AmigoHousekeepingTopicArn',
			{
				description: 'The housekeeping SNS topic to subscribe to',
				type: 'String',
			},
		);

		const copierTopicParam = new CfnParameter(this, 'AmigoTopicArn', {
			description: 'The SNS topic to subscribe to',
			type: 'String',
		});

		const loggingPolicy = new PolicyStatement({
			effect: Effect.ALLOW,
			actions: [
				'logs:CreateLogGroup',
				'logs:CreateLogStream',
				'logs:PutLogEvents',
			],
			resources: ['arn:aws:logs:*:*:*'],
		});

		const copierLambda = new Function(this, 'ImageCopierLambda', {
			description: 'Lambda for copying and encrypting AMIgo baked AMIs',
			runtime: Runtime.JAVA_8,
			memorySize: 512,
			handler: 'com.gu.imageCopier.LambdaEntrypoint::run',
			timeout: Duration.seconds(30),
			environment: {
				ACCOUNT_ID: this.account,
				KMS_KEY_ARN: kmsKeyArn,
				ENCRYPTED_TAG_VALUE: encryptedTagValueParam.valueAsString,
			},
			code: Code.fromBucket(
				functionCodeBucket,
				`${this.stack}/${this.stage}/imagecopier/imagecopier.zip`,
			),
			initialPolicy: [
				loggingPolicy,
				new PolicyStatement({
					effect: Effect.ALLOW,
					actions: ['ec2:CopyImage', 'ec2:CreateTags'],
					resources: ['*'],
				}),
				new PolicyStatement({
					effect: Effect.ALLOW,
					actions: [
						'kms:Encrypt',
						'kms:Decrypt',
						'kms:CreateGrant',
						'kms:GenerateDataKey*',
						'kms:DescribeKey',
					],
					resources: [
						Fn.conditionIf(
							'ImportKmsKeyArn',
							Fn.importValue('amigo-imagecopier-key'),
							kmsKeyArnParam.valueAsString,
						).toString(),
					],
				}),
			],
		});

		this.overrideLogicalId(copierLambda, {
			logicalId: 'ImageCopierLambda',
			reason: 'To gain confidence during the migration to CDK',
		});

		const copierTopic = Topic.fromTopicArn(
			this,
			`ImageCopierLambda-SnsExistingIncomingEventsTopic`,
			copierTopicParam.valueAsString,
		);

		copierLambda.addEventSource(new SnsEventSource(copierTopic));

		const housekeepingLambda = new Function(this, 'HousekeepingLambda', {
			description: 'Lambda for housekeeping AMIgo baked AMIs in other accounts',
			runtime: Runtime.JAVA_8,
			memorySize: 512,
			handler: 'com.gu.imageCopier.LambdaEntrypoint::housekeeping',
			timeout: Duration.seconds(30),
			environment: {
				ACCOUNT_ID: this.account,
				KMS_KEY_ARN: kmsKeyArn,
				ENCRYPTED_TAG_VALUE: encryptedTagValueParam.valueAsString,
			},
			code: Code.fromBucket(
				functionCodeBucket,
				`${this.stack}/${this.stage}/imagecopier/imagecopier.zip`,
			),
			initialPolicy: [
				loggingPolicy,
				new PolicyStatement({
					effect: Effect.ALLOW,
					actions: [
						'ec2:DescribeImages',
						'ec2:DeregisterImage',
						'ec2:DeleteSnapshot',
					],
					resources: ['*'],
				}),
			],
		});

		this.overrideLogicalId(housekeepingLambda, {
			logicalId: 'HousekeepingLambda',
			reason: 'To gain confidence during the migration to CDK',
		});

		const housekeepingTopic = Topic.fromTopicArn(
			this,
			`HousekeepingLambda-SnsExistingIncomingEventsTopic`,
			housekeepingTopicParam.valueAsString,
		);

		housekeepingLambda.addEventSource(new SnsEventSource(housekeepingTopic));
	}
}
