import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';

const appURL = 'http://localhost:9000';
const isCI = Boolean(process.env.CI);
const compose = 'docker compose -f tests/e2e/docker/docker-compose.yml';

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  preserveOutput: 'always',
  reporter: isCI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: appURL,
    headless: true,
    trace: 'on',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: `${compose} up -d --wait && ${compose} run --rm seed && sbt -batch run`,
    // Keeps every AWS call on the moto container and away from real credentials.
    env: {
      AWS_ENDPOINT_URL: 'http://localhost:5050',
      AWS_SHARED_CREDENTIALS_FILE: path.resolve(__dirname, 'tests/e2e/docker/aws-credentials'),
      AWS_CONFIG_FILE: '/dev/null',
    },
    url: `${appURL}/healthcheck`,
    reuseExistingServer: false,
    timeout: 300_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
