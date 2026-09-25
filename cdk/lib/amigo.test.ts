import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { amigoCodeProps, amigoProdProps } from '../bin/cdk';
import { AmigoStack } from './amigo';

describe('The Amigo stack', () => {
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
