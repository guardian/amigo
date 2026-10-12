import { defineConfig, devices } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { join } from 'node:path';

const appURL = `http://127.0.0.1:${process.env.E2E_PORT ?? '9100'}`;
const isCI = Boolean(process.env.CI);
// Workers inherit this identifier; independent CLI invocations get their own.
const runId = process.env.AMIGO_E2E_RUN_ID ??= randomUUID();

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  outputDir: join('test-results', runId),
  reporter: isCI
    ? [['github'], ['./tests/e2e/auth/server-log-reporter.ts'], ['html', { open: 'never', outputFolder: join('playwright-report', runId) }]]
    : [['list'], ['./tests/e2e/auth/server-log-reporter.ts'], ['html', { open: 'never', outputFolder: join('playwright-report', runId) }]],
  preserveOutput: 'always',
  use: {
    baseURL: appURL,
    headless: true,
    trace: 'on',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium-google-mocked',
      testIgnore: '**/auth/**',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'auth-contracts',
      testMatch: '**/auth/*.spec.ts',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'node tests/e2e/auth/run-server.mjs',
    url: `${appURL}/healthcheck`,
    reuseExistingServer: !isCI,
    timeout: 180_000,
    gracefulShutdown: { signal: 'SIGTERM', timeout: 20_000 },
    stdout: 'ignore',
    stderr: 'pipe',
  },
});
