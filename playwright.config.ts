import { defineConfig, devices } from "@playwright/test";

const appURL = "http://localhost:9000";
const isCI = Boolean(process.env.CI);

// eslint-disable-next-line import/no-default-export -- Playwright loads a default-exported configuration.
export default defineConfig({
  testDir: "./tests/e2e",
  testIgnore: "**/seed.spec.ts",
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  preserveOutput: "always",
  reporter: isCI
    ? [["github"], ["html", { open: "never" }]]
    : [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: appURL,
    headless: true,
    trace: "on",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: 'sbt -batch "E2E / run"',
    url: appURL,
    reuseExistingServer: !isCI,
    timeout: 60_000,
    stdout: "pipe",
    stderr: "pipe",
  },
});
