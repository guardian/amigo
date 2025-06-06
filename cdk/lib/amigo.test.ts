import { App } from 'aws-cdk-lib';
import { Template } from 'aws-cdk-lib/assertions';
import { amigoProdProps } from '../bin/cdk';
import { AmigoStack } from './amigo';

describe('The Amigo stack', () => {
	it('matches the snapshot', () => {
		const app = new App();
		const stack = new AmigoStack(app, 'AMIgo', amigoProdProps);
		expect(Template.fromStack(stack).toJSON()).toMatchSnapshot();
	});
});
