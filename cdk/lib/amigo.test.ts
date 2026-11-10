import { App } from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import { amigoCodeProps, amigoProdProps } from '../bin/cdk';
import { AmigoStack } from './amigo';

describe('The Amigo stack', () => {
	it.each([amigoCodeProps, amigoProdProps])(
		'keeps the $stage root and configuration read-only with writable scratch space',
		(props) => {
			const template = Template.fromStack(
				new AmigoStack(new App(), `AMIgo-${props.stage}-volumes`, props),
			);
			template.hasResourceProperties('AWS::ECS::TaskDefinition', {
				Cpu: '2048',
				Memory: '4096',
				RuntimePlatform: {
					CpuArchitecture: 'ARM64',
					OperatingSystemFamily: 'LINUX',
				},
				ContainerDefinitions: Match.arrayWith([
					Match.objectLike({
						Name: 'amigo',
						ReadonlyRootFilesystem: true,
						MountPoints: [
							{
								ContainerPath: '/etc/gu/s3-sync/',
								SourceVolume: 's3files-volume',
								ReadOnly: true,
							},
							{
								ContainerPath: '/tmp',
								SourceVolume: 'tmp-volume',
								ReadOnly: false,
							},
						],
					}),
					Match.objectLike({
						Name: 'LogShipping',
						ReadonlyRootFilesystem: true,
						MountPoints: [
							{
								ContainerPath: '/init',
								SourceVolume: 'logging-volume',
								ReadOnly: false,
							},
						],
					}),
				]),
				Volumes: [
					{ Name: 'logging-volume' },
					{
						Name: 's3files-volume',
						S3FilesVolumeConfiguration: Match.objectLike({
							FileSystemArn: Match.anyValue(),
							RootDirectory: '/',
						}),
					},
					{ Name: 'tmp-volume' },
				],
			});
			template.hasResourceProperties('AWS::S3Files::FileSystem', {
				Prefix: `deploy/${props.stage}/amigo/conf/`,
			});
			template.resourceCountIs('AWS::ECS::Service', 1);
			template.resourceCountIs('AWS::AutoScaling::AutoScalingGroup', 1);
			template.hasResourceProperties('AWS::ElasticLoadBalancingV2::Listener', {
				Port: 443,
				DefaultActions: [
					Match.objectLike({ Type: 'authenticate-oidc' }),
					Match.objectLike({
						Type: 'forward',
						ForwardConfig: {
							TargetGroups: [
								{
									TargetGroupArn: { Ref: 'TargetGroupAmigoB9501F07' },
									Weight: 0,
								},
								{
									TargetGroupArn: { Ref: 'AmigoEcsTargetGroupAmigoF6C987CA' },
									Weight: 100,
								},
							],
						},
					}),
				],
			});
			for (const priority of [10, 11]) {
				template.hasResourceProperties(
					'AWS::ElasticLoadBalancingV2::ListenerRule',
					{
						Priority: priority,
						Actions: [
							Match.objectLike({ Type: 'authenticate-oidc' }),
							Match.objectLike({ Type: 'forward' }),
						],
					},
				);
			}
		},
	);

	it('rejects mutable image tags', () => {
		expect(
			() =>
				new AmigoStack(new App(), 'AMIgo-invalid-image', {
					...amigoCodeProps,
					imageDigest: 'latest',
				}),
		).toThrow('AMIgo requires an immutable SHA-256 image digest');
	});

	it('matches the snapshot', () => {
		const app = new App();
		const stack = new AmigoStack(app, 'AMIgo', amigoProdProps);
		expect(Template.fromStack(stack).toJSON()).toMatchSnapshot();
	});

	it('matches the Amigo developer policy snapshot', () => {
		const codeApp = new App();
		const prodApp = new App();
		const codeTemplate = Template.fromStack(
			new AmigoStack(codeApp, 'AMIgo-CODE-test', amigoCodeProps),
		);
		const prodTemplate = Template.fromStack(
			new AmigoStack(prodApp, 'AMIgo-PROD-test', amigoProdProps),
		);
		const developerPolicyProperties = {
			Properties: {
				Description: 'Develop and test Amigo',
			},
		};

		const developerPolicy = {
			CODE: codeTemplate.findResources(
				'AWS::IAM::ManagedPolicy',
				developerPolicyProperties,
			),
			PROD: prodTemplate.findResources(
				'AWS::IAM::ManagedPolicy',
				developerPolicyProperties,
			),
		};

		expect(developerPolicy).toMatchSnapshot();
	});
});
