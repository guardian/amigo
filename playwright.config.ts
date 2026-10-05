import { devNull } from 'node:os';
import { defineConfig } from '@playwright/test';

process.env.PLAYWRIGHT_LAST_RUN_OUTPUT_FILE = devNull;

export default defineConfig({
  testDir: './tests/e2e',
  testIgnore: '**/seed.spec.ts',
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  outputDir: './test-results/e2e',
  preserveOutput: 'never',
  reporter: 'list',
  use: {
    baseURL: process.env.AMIGO_BASE_URL ?? 'http://localhost:9000',
    browserName: 'chromium',
    headless: true,
    viewport: { width: 1440, height: 900 },
  },
});
