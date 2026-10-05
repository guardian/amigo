import { devNull } from 'node:os';
import { defineConfig } from '@playwright/test';

process.env.PLAYWRIGHT_LAST_RUN_OUTPUT_FILE = devNull;

const offlineServer = process.env.AMIGO_E2E_SERVER === 'true';
const offlineURL = 'http://127.0.0.1:9100';

export default defineConfig({
  testDir: './tests/e2e',
  testIgnore: '**/seed.spec.ts',
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  outputDir: './test-results/e2e',
  preserveOutput: 'never',
  reporter: 'list',
  webServer: offlineServer
    ? {
        command: 'sbt -batch "Test / runMain e2e.E2EServer"',
        url: `${offlineURL}/recipes`,
        timeout: 30_000,
        reuseExistingServer: false,
        gracefulShutdown: { signal: 'SIGTERM', timeout: 15_000 },
        stdout: 'pipe',
      }
    : undefined,
  use: {
    baseURL: offlineServer ? offlineURL : 'http://localhost:9000',
    browserName: 'chromium',
    headless: true,
    viewport: { width: 1440, height: 900 },
  },
});
