import { App } from 'aws-cdk-lib';
import { KMSKey } from './kms';
import { Lambda } from './lambda';

const app = new App();

new KMSKey(app, 'kms-key-stack', {
	stack: 'deploy',
	stage: 'PROD',
	description: 'AMIgo kms key creator',
});

new Lambda(app, 'lambda-stack', {
	stack: 'deploy',
	stage: 'PROD',
	description: 'AMIgo image copier lambda',
});
