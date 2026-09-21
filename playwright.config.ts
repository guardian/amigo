import { defineConfig, devices } from '@playwright/test';

function optionalEnv(name: string): string | undefined {
	const value = process.env[name];
	return value === '' ? undefined : value;
}

const externalBaseURL = optionalEnv('PLAYWRIGHT_BASE_URL');
const localBaseURL = 'http://localhost:9000';

export default defineConfig({
	testDir: './tests/e2e',
	fullyParallel: true,
	forbidOnly: !!process.env.CI,
	// Retrying a mutation could launch another bake or repeat a deletion.
	retries: 0,
	reporter: [['list'], ['html', { open: 'never' }]],
	projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
	use: {
		baseURL: externalBaseURL ?? localBaseURL,
		storageState: optionalEnv('PLAYWRIGHT_STORAGE_STATE'),
		trace: 'on',
		screenshot: 'only-on-failure',
	},
	webServer: externalBaseURL
		? undefined
		: {
				command: './script/server',
				url: `${localBaseURL}/healthcheck`,
				reuseExistingServer: !process.env.CI,
				timeout: 180_000,
				gracefulShutdown: { signal: 'SIGTERM', timeout: 10_000 },
			},
});
