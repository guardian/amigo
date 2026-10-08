# AMIgo end-to-end tests

Implements `specs/basic-ui-test-plan.md` with six navigation and visibility tests. Each scenario has its own test file. Tests are read-only and never submit forms, select roles, bake images or delete records.

## Run

Run from the repository root without starting AMIgo separately:

```bash
npm run test:e2e
```

The root `playwright.config.ts` discovers all Playwright end-to-end tests recursively under `tests/e2e`, including both `*.spec.*` and `*.test.*` files. Keep end-to-end tests in this directory. Shared browser settings are configured centrally, so new end-to-end tests should import `test` from `@playwright/test` without repeating those settings.

All Playwright runs start an isolated application with synthetic data at `http://localhost:9000` and stop it afterwards. Stop the full development app before running tests, as it uses the same port. Node, Java and sbt must be available on `PATH`; use mise with the repository's `.tool-versions` to manage their versions.

## Interactive UI

To start Playwright's browser-based interactive UI with an isolated application and synthetic data, without starting the full app separately:

```bash
npm run test:e2e:ui
```

The UI uses the same isolated application described below at `http://localhost:9000`. Playwright starts it when initialising the test environment and stops it when the UI closes.

Open `http://localhost:9323` in your browser, select individual tests or run the whole suite, and inspect their steps and browser view. Forward port 9323 to your host when using a dev container. This uses your own browser rather than requiring a desktop window inside the container. Stop the UI with Ctrl+C.

The offline application and the full development app both use port 9000, so run only one at a time. Stop the full app before starting the UI. If Playwright reports that the application URL is already in use, stop the existing app or older UI session and restart with `npm run test:e2e:ui`.

If the test list stays on **Loading...**, open `http://localhost:9323/` again rather than refreshing an existing `/trace/uiMode.html?ws=...` tab. The `ws` parameter identifies a particular server session and becomes stale when the UI server restarts. Do not bookmark the redirected URL.

### Testing code changes

Keep `npm run test:e2e:ui` running, save your application changes, then rerun the relevant test in the UI. Play's development server recompiles and reloads the application on the next request. Controllers, templates, routes, assets and the fixtures under `test/e2e` can change without restarting the UI or server process. Use the UI's watch controls to rerun tests when the test files themselves change.

The first request after a Scala or template edit may take longer while compilation completes. Compilation failures appear in the server output and fail the test rather than using the previous application. Build-definition, dependency and Playwright configuration changes still require restarting the UI.

Use `localhost` in your browser, not `0.0.0.0`: the latter is a server bind address, and browsers do not support the UI's service worker over plain HTTP at that address. If the terminal announces a port other than 9323, another process is already using 9323 and Playwright has selected a different port. Stop the previous UI with Ctrl+C and restart the command so the forwarded port reaches the current session.

## Isolated test application

CI runs the same browser tests with the same command:

```bash
npm run test:e2e
```

Playwright starts `sbt -batch "E2E / run"` on `localhost:9000` and stops it after the tests. This scoped Play development server compiles the test classpath and uses `e2e.E2EApplicationLoader`, without changing the normal development or production application loader. Playwright waits for the configured application URL to render successfully.

Play's development asset loader serves CSS, JavaScript and images through the production asset routes. The homepage test checks that the main stylesheet returns HTTP 200 with a CSS content type.

The server uses the production routes, navigation controllers, templates and filters, with the existing development identity in `Mode.Dev`. No ALB headers are required. Its local Prism stub serves all five expected datasets with `stale: false`; the normal Prism cache refresh still runs. Read-only DynamoDB fixtures contain one synthetic base image and recipe, with no bakes. Unsupported operations and writes fail explicitly.

The bootstrap does not load AWS configuration, fetch rotating secrets, perform STS calls or start baking, housekeeping or notification jobs. It lives entirely under `test/e2e`, is never packaged with the deployed application, and does not change production authentication or startup. Its Prism stub is stopped with the application on each reload. No AWS credentials or access to the Prism deployment are needed.

The `E2E` job in `.github/workflows/ci.yaml` uses the Node and Scala setup actions with `.tool-versions`, runs `npm ci` before reading the installed Playwright version, restores a browser cache keyed by that version and `package-lock.json`, installs headless Chromium and its system dependencies, and runs the tests. Server startup uses the `sbt` on `PATH`, so mise is not required on the CI runner. CI reports results with GitHub annotations and generates an HTML report.

## Coverage and output

The suite checks homepage visibility, each primary navigation destination, one section-to-section transition and the brand-link return home. Each destination has a main heading and one content landmark check. The transition test checks the page shell only, without repeating destination content checks. Assertions cover URLs, the document title and usable navigation, without depending on particular records, counts or table columns.

Visual regression testing is out of scope. All runs record traces and retain generated test artefacts under `test-results`, including for passing tests. Screenshots are captured on failure; videos are not recorded. Headless runs retain Playwright's `.last-run.json` metadata and generate an HTML report under `playwright-report`, without opening it automatically. Both output directories are ignored by Git.

Playwright's UI records traces with application snapshots; retaining those files lets you inspect completed tests and their steps.

## Server-side logging

Playwright forwards the test server's standard output and standard error to the UI's **Output** panel and to the terminal during headless runs. The development Logback configuration in `conf/logback.xml` already enables `DEBUG` for the `application` logger; other loggers use their configured levels.

For additional Playwright server-startup and readiness diagnostics, run:

```bash
DEBUG=pw:webserver npm run test:e2e:ui
```

If Playwright reuses an application already running on port 9000, its logs remain in the terminal that started that application. Stop that application before launching the UI if you want Playwright to capture its server logs.
