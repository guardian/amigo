# AMIgo end-to-end tests with mocked Google authentication

This implements `specs/mocked-google-auth-test-plan.md`. The six UI scenarios and their assertions come from [#1912](https://github.com/guardian/amigo/pull/1912). Unlike that PR's development identity, these tests retain the current Google login controller, anti-forgery validation, group authorisation, session creation and authentication action.

## Run

Use the repository's `.tool-versions` through mise. Java supplies `keytool`; the launcher also requires OpenSSL. No Google or AWS credentials are needed.

```bash
mise exec -- npm ci
mise exec -- npx playwright install chromium
mise exec -- npm run test:e2e
```

On a fresh Linux machine, install Chromium's system dependencies with `npx playwright install --with-deps chromium`. CI does this automatically.

The launcher owns an isolated app on `http://127.0.0.1:9100` and an authentication mock proxy on port 9101. Local runs can reuse the mocked-auth server already started by another local run or UI session; CI always starts a clean server. Every UI test still verifies the real authentication redirect and backend mock responses, so an authentication bypass cannot silently pass. The normal development app on port 9000 can stay running. To use different ports, set both `E2E_PORT` and `E2E_PROXY_PORT`; each must be an available port between 1024 and 65535.

Run just the six comparison scenarios or just the authentication contracts:

```bash
mise exec -- npm run test:e2e -- --project=chromium-google-mocked
mise exec -- npm run test:e2e:auth
mise exec -- npm run test:e2e:ui
```

Additional focused checks:

```bash
mise exec -- npm run typecheck:e2e
mise exec -- npm run test:e2e:mock
mise exec -- sbt --client 'testOnly e2e.*'
```

The UI runs the same isolated server and fixtures. Scala/template edits require restarting this harness because its `Test / runMain` server uses compiled jars, rather than Play's live-reloading development runner. This is a difference from #1912's development-server approach.

## What is intercepted

There are two distinct boundaries:

1. The browser fixture fetches the first protected navigation without following redirects. It verifies that the real authentication action redirects to `/login`, executes the real login action, and replaces **only** the Google authorisation redirect with a synthetic callback code and the original signed state. All cookies come from AMIgo's responses. It does not preload a session or fulfil a protected page with fake HTML.
2. The test JVM sends Google discovery, user-token, user-info, service-account token and Directory API requests through a local HTTP/HTTPS response proxy. The proxy terminates TLS locally and never opens an upstream connection. Both Play WS and the Google Java HTTP clients use it. Unknown requests fail explicitly.

The first boundary avoids Playwright's redirect-chain routing limitations: a URL-filtered route for the external Google endpoint alone did not reliably intercept the redirected navigation. Unexpected browser Google requests are blocked. The real `/oauth2callback` still receives the code and state and creates the authenticated session.

Each run generates an ephemeral TLS certificate, private key, service-account key, session-signing value and disposable trust store in a private temporary directory. Only the forked test JVM trusts this certificate; no host-machine or production trust store is modified. The pinned Google group checker builds a legacy transport with its own cached certificate store, so the test JVM also adds its disposable certificate to that in-memory store. Certificate and hostname verification remain enabled.

Each test registers a unique identity and authorisation code. Response scenarios are correlated by code, token and email, not a global current-user switch. Discovery and service-account refresh may be cached; their cold-run occurrence is checked separately. Every UI test must complete exactly one browser callback and observe the backend token → user-info → groups sequence.

## Isolated application

`test/e2e/E2EServer.scala` runs only on the test classpath. It constructs the real routes, UI controllers, templates, assets and filters with Google authentication enabled. It does not invoke the production `AppLoader`, load remote configuration, initialise AWS clients or start baking, housekeeping, notification or scheduling jobs.

The read-only Dynamo fixtures and local Prism datasets come from #1912. Writes and unsupported operations fail explicitly. The mock proxy is implemented using Node's built-in HTTP/TLS modules, so it needs no separate proxy service or third-party mocking package.

The forked test JVM loads Mockito's existing Byte Buddy agent at startup with `-javaagent`. Dynamic agent loading is disabled for that JVM, avoiding Java 21's attachment warning and ensuring the harness does not depend on runtime self-attachment. Class-data sharing is also disabled with `-Xshare:off` because Mockito appends bootstrap classes, which prevents that optimisation and otherwise produces a JVM warning. These settings do not apply to the production JVM.

Production authentication and startup code are unchanged. The existing Scala 2.13 dialect and Future-based Play APIs are retained.

## Coverage and diagnostic output

The `chromium-google-mocked` project contains exactly the six navigation/visibility scenarios from #1912. The `auth-contracts` project separately covers unauthenticated routes, session reload, each allowed group independently, missing/invalid state, recovery after rejected state, wrong domain, expired identity, missing membership, HTTP failures and malformed responses.

Authentication tests inspect failure redirects without following them. `/login` is a redirecting action, not an application login page; a failed login must not be hidden by an automatic successful retry.

Browser traces and failure screenshots are retained under `test-results/<run-id>`, and the HTML report is in `playwright-report/<run-id>`. Each invocation gets its own identifier, inherited by its workers, so UI and headless processes cannot delete each other's active traces or reports. Passing runs show test results without streaming application, proxy or sbt logs. Server output is captured in `test-results/server-<port>.log`; a failing run prints its last 64 KiB, and failed tests include that tail as an HTML-report attachment. This also preserves details for server-startup failures. The log belongs to the server port so local runs reusing a UI server can find its diagnostics.

Application logging remains enabled, including deliberately rejected authentication cases. Tests attach a backend request journal containing stage and scenario identifiers, without authorisation headers, key material or cookies. Browser traces contain only disposable test authentication material.

For a report from a particular run, use `npx playwright show-report playwright-report/<run-id>`. CI uploads the complete report and test-result roots. Restart UI sessions launched before changing the Playwright configuration so they use the new output paths.

The library's pinned ID-token parser does not verify every standard OIDC claim or the provider signature. These tests preserve its existing behaviour; they do not claim to validate verification that the production dependency does not perform.

## Compare with #1912

Run the six-test project here and the six reference tests on #1912's branch using separate checkouts and clean server processes. Keep browser version, viewport, synthetic data and worker count equal. For timings, disable retries:

```bash
mise exec -- npm run test:e2e -- --project=chromium-google-mocked --workers=2 --retries=0
```

Measure startup separately from scenario duration and repeat clean runs rather than comparing one result. The Google-mocked architecture covers the real authentication boundary but adds proxy, TLS, response-contract and cache maintenance. The reference architecture is simpler for UI-only coverage but does not exercise this Google login flow. No cross-architecture benchmark or recommendation is claimed by this implementation.
