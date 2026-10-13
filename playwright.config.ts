import { defineConfig } from '@playwright/test';
import path from 'node:path';

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:9000',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'docker compose -f tests/e2e/docker/docker-compose.yml up -d --wait && sbt -batch run',
    // Keeps every AWS call on the LocalStack container and away from real credentials.
    env: {
      AWS_ENDPOINT_URL: 'http://localhost:4566',
      AWS_SHARED_CREDENTIALS_FILE: path.resolve(__dirname, 'tests/e2e/docker/aws-credentials'),
      AWS_CONFIG_FILE: '/dev/null',
    },
    url: 'http://localhost:9000/healthcheck',
    timeout: 300_000,
    stdout: 'pipe',
  },
});
