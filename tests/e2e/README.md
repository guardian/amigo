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

Docker with Compose is also required. Playwright starts the containers in [docker/docker-compose.yml](docker/docker-compose.yml), then the normal app with `sbt -batch run` on `http://localhost:9000`. Stop any app already using port 9000 first.

| Container | Replaces | Config |
| --- | --- | --- |
| `aws` (LocalStack 4.14.0, the last release without an auth token) | AWS | [docker/seed.sh](docker/seed.sh) writes the DEV SSM parameters; DynamoDB starts empty |
| `oauth` (`mock-oauth2-server`) | Google OpenID Connect | [docker/oauth.json](docker/oauth.json) |
| `prism` (WireMock) | Prism | [docker/prism/prism.json](docker/prism/prism.json) |

The app uses dummy credentials from [docker/aws-credentials](docker/aws-credentials), and `AWS_CONFIG_FILE=/dev/null` so your own AWS profiles are never read. Containers keep running between runs. Stop them with:

```bash
docker compose -f tests/e2e/docker/docker-compose.yml down
```

For interactive mode:

```bash
mise exec -- npm run test:e2e:ui
```

Open `http://localhost:9323/`. Forward port 9323 when using a dev container. Play's development server reloads application changes on the next request. New scenarios should import `test` from `./support` to use the login fixture.

## Authentication

The `oauth` container signs in the synthetic user `navigation@guardian.co.uk` with Google-compatible claims.

Each scenario starts with a fresh browser context. The shared fixture in `tests/e2e/support.ts`:

1. Visits the protected homepage and verifies redirection to the mock provider.
2. Checks the client ID, callback URL and presence of OAuth state.
3. Submits the mock provider's sign-in form.
4. Checks that the callback includes an authorisation code and preserves state, then waits for the authenticated homepage.

There is no cookie injection, saved authentication state, browser route interception of OAuth responses or development-auth bypass. The normal `com.gu.googleauth.AuthAction` and `controllers.Login` perform the code exchange, user-info request, email-domain restriction, state verification and Play session creation. Session validity enforcement remains enabled.

The `auth.mockOidcDiscoveryUrl` config key covers the two parts no container can replace. `AppComponents` refuses to start if it is set outside `Mode.Dev`. When set:

* The mock server's discovery document is loaded into `GoogleAuth.discoveryDocumentHolder`, because `play-googleauth` hard-codes Google's discovery URL.
* The Google Groups lookup reports the user as a member of every required group, because `mock-oauth2-server` doesn't implement the Directory API. The email-domain check still runs.

The token mapping includes `azp` and a synthetic `at_hash` because `play-googleauth` requires those fields when parsing Google's ID token. This client does not verify ID-token signatures, issuer, audience or `at_hash`. The mock server signs tokens and verifies the bearer token at its user-info endpoint, but these tests must not be interpreted as proving client-side cryptographic validation or real Google account/consent behaviour.

## Comparison with #1912

| Aspect | #1912 | This approach |
| --- | --- | --- |
| Navigation scenarios and data | Six scenarios, local Prism, read-only DynamoDB | Same scenarios, local Prism, empty DynamoDB |
| Authentication | Development identity, Google login removed | Real Google auth action and login controller against a local OAuth provider |
| Browser setup | Navigate directly to the app | Sign in through the mock provider once per scenario |
| Additional dependencies | Playwright | Playwright and Docker |
| Additional wiring | Development identity provider | A dev-only mock OIDC config key and a configurable Prism URL |
| What it exercises | Navigation after an auth bypass | The production wiring, plus redirects, code exchange, user info, state checks and session creation |
| What it cannot establish | Real authentication behaviour | Real Google behaviour, Directory API integration, ID-token validation absent from the existing client, or differences between LocalStack and AWS |

## Artefacts

Every Playwright run retains traces under `test-results/`, takes screenshots on failure and produces an HTML report under `playwright-report/`. Both directories are ignored by Git. Inspect a completed run with `mise exec -- npx playwright show-report`.

The `E2E` CI job installs Node and Scala using `.tool-versions`, installs npm dependencies and headless Chromium, runs the same command, and uploads reports and traces even when tests fail. App logs are visible in the terminal and Playwright UI's Output panel. Container logs are available with `docker compose -f tests/e2e/docker/docker-compose.yml logs`.
