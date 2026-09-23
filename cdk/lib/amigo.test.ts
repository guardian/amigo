import { App } from 'aws-cdk-lib';
import { Match, Template } from 'aws-cdk-lib/assertions';
import { amigoCodeProps, amigoProdProps } from '../bin/cdk';
import { AmigoStack } from './amigo';

describe('The Amigo stack', () => {
	it('matches the snapshot', () => {
		const app = new App();
		const stack = new AmigoStack(app, 'AMIgo', amigoProdProps);
		expect(Template.fromStack(stack).toJSON()).toMatchSnapshot();
	});

	it('adds the Amigo developer policy to CODE only', () => {
		const codeApp = new App();
		const prodApp = new App();
		const codeTemplate = Template.fromStack(
			new AmigoStack(codeApp, 'AMIgo-CODE-test', amigoCodeProps),
		);
		const prodTemplate = Template.fromStack(
			new AmigoStack(prodApp, 'AMIgo-PROD-test', amigoProdProps),
		);

		codeTemplate.hasResourceProperties('AWS::IAM::ManagedPolicy', {
			Description: 'Develop and test Amigo against DEV resources',
			Path: '/developer-policy/guardian/amigo/deploy/CODE/amigo-dev/',
		});
		prodTemplate.resourceCountIs('AWS::IAM::ManagedPolicy', 0);
	});

	it('scopes sensitive developer permissions', () => {
		const app = new App();
		const template = Template.fromStack(
			new AmigoStack(app, 'AMIgo-CODE-test', amigoCodeProps),
		);

		template.hasResourceProperties('AWS::IAM::ManagedPolicy', {
			PolicyDocument: {
				Statement: Match.arrayWith([
					Match.objectLike({
						Action: Match.arrayWith(['ec2:TerminateInstances']),
						Condition: {
							StringEquals: {
								'aws:ResourceTag/AmigoStage': 'DEV',
								'aws:ResourceTag/Stack': 'amigo-packer',
								'ec2:Region': 'eu-west-1',
							},
						},
					}),
					Match.objectLike({
						Action: 'iam:PassRole',
						Condition: {
							StringEquals: {
								'iam:PassedToService': 'ec2.amazonaws.com',
							},
						},
						Resource: {
							'Fn::Join': [
								':role/',
								{
									'Fn::Split': [
										':instance-profile/',
										{ Ref: 'PackerInstanceProfile' },
									],
								},
							],
						},
					}),
				]),
			},
		});
	});

	it('does not grant EC2 write actions on all resources', () => {
		const app = new App();
		const template = Template.fromStack(
			new AmigoStack(app, 'AMIgo-CODE-test', amigoCodeProps),
		);
		const policies = template.findResources('AWS::IAM::ManagedPolicy');
		const policy = Object.values(policies)[0] as {
			Properties: {
				PolicyDocument: {
					Statement: Array<{
						Action: string | string[];
						Resource: string | string[];
					}>;
				};
			};
		};
		const ec2WriteStatements = policy.Properties.PolicyDocument.Statement.filter(
			({ Action }) =>
				[Action].flat().some(
					(action) =>
						action.startsWith('ec2:') &&
						!action.startsWith('ec2:Describe'),
				),
		);

		for (const statement of ec2WriteStatements) {
			expect([statement.Resource].flat()).not.toContain('*');
		}
	});

	it('uses the configured stack name in developer policy resources', () => {
		const app = new App();
		const template = Template.fromStack(
			new AmigoStack(app, 'AMIgo-CODE-custom-stack', {
				...amigoCodeProps,
				stack: 'custom-stack',
			}),
		);
		const policies = template.findResources('AWS::IAM::ManagedPolicy');
		const policy = JSON.stringify(Object.values(policies)[0]);

		expect(policy).toContain('parameter/DEV/custom-stack/amigo/*');
		expect(policy).toContain(
			'parameter/CODE/custom-stack/amigo/aws.distributionBucket',
		);
		expect(policy).toContain(
			'/custom-stack/CODE/amigo/conf/amigo-service-account-cert.json',
		);
	});
});
