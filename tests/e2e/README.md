# AMIgo end-to-end tests with mock Google OAuth

The six read-only Playwright scenarios match [#1912](https://github.com/guardian/amigo/pull/1912) at commit `10c179959d65960e5ee683a970d4c3a6d13aed1f`: homepage visibility, Base images, Roles, Recipes, Housekeeping, and section-to-section navigation with return home. Assertions cover the application origin and path, title, main heading, primary navigation and destination content. The homepage also checks that the application stylesheet is served.

No scenarios create or edit records, select roles, bake images or delete data. Visual regression testing is out of scope.

## Run locally

Use mise and the root `.tool-versions` for Node, Java and sbt:

```bash
mise install
mise exec -- npm ci
mise exec -- npx playwright install --with-deps --only-shell chromium
mise exec -- npm run test:e2e
```

Playwright starts `sbt -batch "E2E / run"` on `http://localhost:9000`, waits for `/healthcheck`, and stops the server afterwards. Stop any existing application using port 9000 first. Existing servers are deliberately never reused, so a run cannot accidentally target the normal application or another auth approach.

The mock OAuth server and Prism stub run on automatically allocated loopback ports inside the application JVM. No Docker, AWS credentials, Google account, service-account certificate or real Google client registration is needed. Browser tests still load the application's existing public CDN assets, just as in #1912.

For interactive mode:

```bash
mise exec -- npm run test:e2e:ui
```

Open `http://localhost:9323/`. Forward port 9323 when using a dev container. Play's development server reloads application and test-bootstrap changes on the next request. Restart the UI after changing dependencies, build settings or Playwright configuration. New scenarios should import `test` from `./support` to use the login fixture.

## Authentication

`test/e2e/E2EGoogleAuth.scala` runs `no.nav.security:mock-oauth2-server:6.0.4` as a test-scoped JVM dependency, with interactive login and Google-compatible claims for the synthetic user `navigation@guardian.co.uk`.

Each scenario starts with a fresh browser context. The shared fixture in `tests/e2e/support.ts`:

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

The non-auth mocks are reused from #1912:

* `E2EPrismStub` serves all five expected Prism datasets with `stale: false`, one synthetic AWS account and no instances, launch configurations, launch templates or copied images. The normal Prism refresh still runs.
* `E2EFixtures` provides the same read-only DynamoDB records: one synthetic base image, one recipe, no bakes and no bake logs. Unknown tables, writes and unsupported operations fail explicitly.
* The real routes, navigation controllers, templates, assets and filters run. Bake execution is disabled. AWS configuration loading, rotating-secret retrieval, STS, baking, housekeeping schedulers and notifications are not started.

The bootstrap lives entirely on the test classpath and rejects modes other than `Mode.Dev`. The OAuth and Prism servers stop with the application, including on reload. The normal development and production loaders, auth configuration and login controller are unchanged.

## Comparison with #1912

| Aspect | #1912 | This approach |
| --- | --- | --- |
| Navigation scenarios and data | Six scenarios, local Prism, read-only DynamoDB | Same |
| Authentication | Development identity, Google login removed | Real Google auth action and login controller against a local OAuth provider |
| Browser setup | Navigate directly to the app | Sign in through the mock provider once per scenario |
| Additional dependencies | Playwright | Playwright plus one test-scoped JVM OAuth server |
| Additional wiring | Development identity provider | Discovery-cache replacement, Google-compatible claims and a Directory API stub |
| What it exercises | Navigation after an auth bypass | Navigation after redirects, code exchange, user info, state checks and session creation |
| What it cannot establish | Real authentication behaviour | Real Google behaviour, Directory API integration or ID-token validation absent from the existing client |

Embedding the server avoids managing containers and separate provider ports. The extra setup is mostly concentrated in `E2EGoogleAuth.scala`, `E2EApplicationLoader.scala` and the browser fixture. The discovery-cache seam and Google-specific token shape are the main maintenance costs when comparing ease of use.

## Checks and artefacts

```bash
mise exec -- sbt -batch "testOnly e2e.*"
```

Scala checks cover the fixtures, rejection of non-development bootstraps, the complete OAuth login and deep-link return, rejection of forged state, expired-session redirection and strict group lookups.

Every Playwright run retains traces under `test-results/`, takes screenshots on failure and produces an HTML report under `playwright-report/`. Both directories are ignored by Git. Inspect a completed run with `mise exec -- npx playwright show-report`.

The `E2E` CI job installs Node and Scala using `.tool-versions`, installs npm dependencies and headless Chromium, runs the same command, and uploads reports and traces even when tests fail. Server logs are visible in the terminal and Playwright UI's Output panel.
