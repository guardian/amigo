# End-to-end tests

These tests cover homepage visibility, Base images, Roles, Recipes, Housekeeping, and section-to-section navigation with return home. Assertions cover the application origin and path, title, main heading, primary navigation and destination content. The homepage also checks that the application stylesheet is served.

No scenarios create or edit records, select roles, bake images or delete data.

## Why TypeScript, and why a separate suite?

Playwright's [full UI mode](https://playwright.dev/docs/test-ui-mode) belongs to the Node-based Playwright Test runner, which discovers JavaScript and TypeScript tests. Its test selection, watch mode, action timeline, DOM snapshots, locator picker and integrated debugging depend on tests running through that runner.

These browser scenarios must therefore remain TypeScript to preserve the full Playwright web UI. They are not wrappers around Scala tests, and there is no duplicate Scala browser suite. Scala is still used for the isolated Play application bootstrap, local service fixtures and two isolation guardrails.

## Run locally

Run this command from the repository root:

```bash
npm --prefix e2e/tests run test:e2e
```

Playwright starts `sbt -batch "E2E / run"` from the repository root on `http://localhost:9000`, waits for `/healthcheck`, and stops the server afterwards. Stop any existing application using port 9000 first. Existing servers are deliberately never reused, so a run cannot accidentally target the normal application or another auth approach.

The mock OAuth server and Prism stub run on automatically allocated loopback ports inside the application JVM. No Docker, AWS credentials, Google account, service-account certificate or real Google client registration is needed. Browser tests still load the application's existing public CDN assets.

## Full Playwright web UI

```bash
npm --prefix e2e/tests run test:e2e:ui
```

Open `http://localhost:9323/`. In a dev container, forward port 9323 and use the forwarded URL. Select a test or use Run all in the sidebar; use watch mode to rerun tests after edits. The UI includes filtering, action-by-action traces, DOM snapshots, network and console inspection, the locator picker and debugging. The browser runs headlessly, so the web UI does not require a graphical display on the host.

The UI binds to `0.0.0.0` for container port forwarding. Only expose it on a trusted local network; do not publish port 9323 publicly.

Play's development server reloads application and Scala bootstrap changes on the next request. Restart the UI after changing dependencies, build settings or Playwright configuration. New browser scenarios should import `test` from `./support` to use the login fixture.

## Authentication

`test/e2e/E2EGoogleAuth.scala` runs `no.nav.security:mock-oauth2-server:6.0.4` as a test-scoped JVM dependency, with interactive login and Google-compatible claims for the synthetic user `navigation@guardian.co.uk`.

Each scenario starts with a fresh browser context. The shared fixture in `e2e/tests/support.ts`:

1. Visits the protected homepage and verifies redirection to the mock provider.
2. Checks the client ID, callback URL and presence of OAuth state.
3. Submits the mock provider's sign-in form.
4. Checks that the callback includes an authorisation code and preserves state, then waits for the authenticated homepage.

There is no cookie injection, saved authentication state, browser route interception of OAuth responses or development-auth bypass. The normal `com.gu.googleauth.AuthAction` and `controllers.Login` perform the code exchange, user-info request, email-domain restriction, state verification and Play session creation. Session validity enforcement remains enabled.

There are two test-only seams:

* `play-googleauth` 42.0.0 hard-codes Google's discovery URL. `E2EApplicationLoader` fetches the mock server's discovery document and supplies it through `GoogleAuth.discoveryDocumentHolder`, the library's public discovery cache. This avoids a production endpoint override or a fork of the login controller. It does couple this experiment to that library API.
* Google Groups uses Google's Directory API, which is not OAuth and is not implemented by `mock-oauth2-server`. A strict Mockito `GoogleGroupChecker` returns one synthetic allowed group for the expected email and fails unexpected lookups. AMIgo's normal group-membership decision still runs; no real service-account credentials are loaded.

The token mapping includes `azp` and a synthetic `at_hash` because `play-googleauth` requires those fields when parsing Google's ID token. This client does not verify ID-token signatures, issuer, audience or `at_hash`. The mock server signs tokens and verifies the bearer token at its user-info endpoint, but these tests must not be interpreted as proving client-side cryptographic validation or real Google account/consent behaviour.

## Non-auth isolation

The non-auth mocks are:

* `E2EPrismStub` serves all five expected Prism datasets with `stale: false`, one synthetic AWS account and no instances, launch configurations, launch templates or copied images. The normal Prism refresh still runs.
* `E2EFixtures` provides the same read-only DynamoDB records: one synthetic base image, one recipe, no bakes and no bake logs. Unknown tables, writes and unsupported operations fail explicitly.
* The real routes, navigation controllers, templates, assets and filters run. Bake execution is disabled. AWS configuration loading, rotating-secret retrieval, STS, baking, housekeeping schedulers and notifications are not started.

The bootstrap lives entirely on the Scala test classpath and rejects modes other than `Mode.Dev`. The OAuth and Prism servers stop with the application, including on reload. The normal development and production loaders, auth configuration and login controller are unchanged.

## Checks and artefacts

```bash
sbt --client test
npm --prefix e2e/tests run test:e2e
```

The first command runs the standard Scala tests, including `test/e2e/E2EIsolationSpec.scala`'s two guardrails: both bootstrap entry points reject non-development mode, and the DynamoDB fixture rejects writes. These checks are discovered normally by `sbt test`, without a separate configuration or CI step. They do not start the application or local OAuth/Prism servers. The browser scenarios already cover bootstrap startup, fixture-backed navigation and successful OAuth login, so there are no duplicate Scala checks for those paths. The second command runs all six TypeScript browser scenarios.

Every Playwright run retains traces under `e2e/tests/test-results/`, takes screenshots on failure and produces an HTML report under `e2e/tests/playwright-report/`. These directories and ScalaTest reports under `target/` are ignored by Git. Inspect a completed browser run with:

```bash
npm --prefix e2e/tests exec -- playwright show-report e2e/tests/playwright-report
```

The main CI job runs the Scala isolation guardrails as part of its standard `sbt test` command. The separate `E2E` CI job installs Node and Scala using `.tool-versions`, installs this suite's npm dependencies and headless Chromium, runs the browser scenarios, and uploads browser reports and traces even when tests fail. Server logs are visible in the terminal and the UI's Output panel.
