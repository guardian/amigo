# AMIgo end-to-end tests

Implements `specs/basic-ui-test-plan.md` with six navigation and visibility tests. Each scenario has its own test file. Tests are read-only and never submit forms, select roles, bake images or delete records.

## Run

Start AMIgo at `http://localhost:9000`, then run from the repository root:

```bash
npm run test:e2e
```

The root `playwright.config.ts` discovers all Playwright end-to-end tests recursively under `tests/e2e`, including both `*.spec.*` and `*.test.*` files. Keep end-to-end tests in this directory. Shared browser settings are configured centrally, so new end-to-end tests should import `test` from `@playwright/test` without repeating those settings.

The generator seed is `tests/e2e/seed.spec.ts`, alongside the scenario files. It is excluded from the automated suite by `testIgnore` and is only used for Playwright test generation setup.

Local tests use `http://localhost:9000`. The suite does not start the application or change its data in this mode.

## Offline CI application

CI runs the same browser tests against an isolated Play application:

```bash
AMIGO_E2E_SERVER=true mise exec java sbt node -- npm run test:e2e
```

This mode starts `e2e.E2EServer` from the Scala test classpath on `127.0.0.1:9100` and stops it after the tests. Playwright waits for `/recipes` to render successfully, which exercises the populated Prism cache, rather than checking only that a port is listening.

The server uses the production routes, navigation controllers, templates and filters, with the existing development identity in `Mode.Dev`. No ALB headers are required. Its local Prism stub serves all five expected datasets with `stale: false`; the normal Prism cache refresh still runs. Read-only DynamoDB fixtures contain one synthetic base image and recipe, with no bakes. Unsupported operations and writes fail explicitly.

The bootstrap does not load AWS configuration, fetch rotating secrets, perform STS calls or start baking, housekeeping or notification jobs. It lives entirely under `test/e2e`, is never packaged with the deployed application, and does not change production authentication or startup. No AWS credentials or access to the Prism deployment are needed.

The `E2E` job in `.github/workflows/ci.yaml` uses the Node and Scala setup actions with `.tool-versions`, runs `npm ci` before reading the installed Playwright version, restores a browser cache keyed by that version and `package-lock.json`, installs headless Chromium and its system dependencies, compiles the test bootstrap, and runs this mode. Server startup uses the `sbt` on `PATH`, so mise is not required on the CI runner. Results remain terminal-only.

## Coverage and output

The suite checks homepage visibility, each primary navigation destination, one section-to-section transition and the brand-link return home. Each destination has a main heading and one content landmark check. The transition test checks the page shell only, without repeating destination content checks. Assertions cover URLs, the document title and usable navigation, without depending on particular records, counts or table columns.

Visual regression testing is out of scope. Traces, screenshots and videos are not recorded, and diagnostic test output is not retained, including on failure. Playwright's last-run metadata is sent to the operating system's null device, so no `.last-run.json` file is retained. Results are reported in the terminal.
