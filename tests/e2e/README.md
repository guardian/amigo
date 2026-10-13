# AMIgo end-to-end tests with mock Google OAuth

Read-only Playwright navigation tests that sign in through the real Google auth flow against a local mock provider.

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

Open `http://localhost:9323/`. Forward port 9323 when using a dev container. Traces of failed tests and an HTML report are written to `test-results/` and `playwright-report/`.

## Authentication

The `oauth` container immediately signs in `navigation@guardian.co.uk` with Google-compatible claims. The normal `AuthAction` and `controllers.Login` do the code exchange, user-info request, email-domain check, state verification and session creation.

The `auth.mockOidcIssuerUrl` config key covers the two parts no container can replace. `AppComponents` refuses to start if it is set outside `Mode.Dev`. When set:

* `GoogleAuth.discoveryDocumentHolder` points at the mock issuer's endpoints, because `play-googleauth` hard-codes Google's discovery URL.
* The Google Groups check is skipped, because `mock-oauth2-server` doesn't implement the Directory API. The email-domain check still runs.

The token mapping includes `azp` and a synthetic `at_hash` because `play-googleauth` requires them when parsing the ID token. These tests don't cover ID-token validation or real Google behaviour.
