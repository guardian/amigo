import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { Lambda } from './lambda';

describe('the lambda stack', () => {
	it('matches the snapshot', () => {
		const app = new App();
		const stack = new Lambda(app, 'IntegrationTest', {
			stack: 'cdk',
			stage: 'TEST',
		});
		const template = Template.fromStack(stack);
		expect(template.toJSON()).toMatchSnapshot();
	});
});
