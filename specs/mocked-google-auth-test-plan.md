# AMIgo end-to-end test plan with mocked Google authentication

## Purpose and scope

Reproduce the six visibility and navigation scenarios in [#1912](https://github.com/guardian/amigo/pull/1912) while keeping AMIgo's Google authentication flow and mocking its external HTTP responses. Compare this with #1912's development-identity approach without changing the UI assertions, data fixtures, browser or viewport.

The harness is now implemented. See `tests/e2e/README.md` for commands and implementation details. Production authentication is unchanged. The scenario descriptions below remain the specification; implementation differences are recorded at the end.

The six comparison tests remain read-only. Forms, role selection, baking, deletion, data correctness, visual regression, mobile coverage and comprehensive accessibility testing remain out of scope. Authentication contract checks are a separate prerequisite suite, not extra scenarios counted in the six-test comparison.

## Evidence and current state

- Inspected branch `kc/mock`, which uses `com.gu.play-googleauth` version `42.0.0`. It does not contain #1912's tracked Playwright configuration or isolated application bootstrap.
- #1912 targets `kc/auth-removal-impl`. Its `authentication.AuthAction` and `IdentityProviderWiring` do not exist on this branch. Reuse its UI tests and non-authentication fixtures, but do not copy its authentication wiring unchanged.
- `app/components/AppComponents.scala` constructs `GoogleAuthConfig`, `GoogleGroupChecker`, `AuthAction` and `controllers.Login`.
- `app/controllers/Login.scala` performs identity verification, checks membership of **at least one** configured Google group, then creates the authenticated session.
- `conf/routes` exposes `/login` and `/oauth2callback`; all five UI destinations use the authentication action.
- Inspected the pinned dependency's `auth.scala`, `actions.scala`, `model.scala`, `groups.scala` and `internal/DirectoryService.scala` at tag `v42.0.0`, rather than assuming the latest OAuth implementation.
- On 7 October 2026, a temporary Playwright seed explored the existing application at `http://localhost:9000`. The homepage, stylesheet, four navigation destinations and Base images → Roles → AMIgo journey satisfied the assertions below.
- The existing process was started as `Test / runMain e2e.E2EServer`. It already served `/` without a new Google login. This establishes UI landmarks only: it is **not evidence** that Google response interception or the proposed authentication harness works. Do not reuse this process for the comparison.

## Authentication boundary and chosen approach

Playwright `page.route()` and `browserContext.route()` intercept browser requests only. They cannot intercept the backend's Play WS requests or the Google Java client's requests.

Use two interception boundaries:

1. **Browser:** intercept the initial protected navigation, fetch its real authentication redirect and execute the real `/login` action without following redirects. Replace only the resulting Google authorisation redirect with a redirect to AMIgo's real `/oauth2callback`, carrying the original `state` unchanged and a unique synthetic authorisation code. Install this route before navigating to AMIgo. Do not render a real Google login page or contact Google.
2. **Test JVM:** intercept the actual outbound authentication HTTP traffic using a local mock HTTP/HTTPS proxy. Return fixture discovery, token, user-info, service-account token and Directory API responses. Retain the real `Login`, `GoogleAuthConfig`, `AntiForgeryChecker`, `AuthAction` and `GoogleGroupChecker`.

The proxy needs a test-only certificate authority trusted by the disposable test JVM. Configure both Play WS and the Google Java HTTP client to use it. The Java client constructs its own transport, so configuring only Play WS is insufficient. Verify proxy support for both clients before building the six-test suite. Never disable certificate verification or change the host machine's trust store.

Keep the proxy, trust material and settings within the test harness. Bind fixture-control endpoints to loopback, stop them with the application, and fail unknown outbound requests explicitly. Allow only the expected authentication destinations through this mock boundary; do not forward unmatched requests to Google. Browser CDN requests for existing UI assets are separate from backend authentication traffic.

This network-level design avoids adding endpoint configuration or test branches to production login code. Its additional proxy and TLS setup is an intentional cost to measure against #1912's simpler identity substitution.

**Not equivalent:** mocking `GoogleGroupChecker.retrieveGroupsFor` can test the application's any-group rule but bypasses Directory HTTP handling and the service-account request. Hand-writing a Play session, replacing `AuthAction`, fulfilling AMIgo's page responses or returning a prebuilt identity also bypasses the authentication flow. Do not present these shortcuts as full response interception.

### Expected request chain and response contracts

| Boundary | Request | Mock response and required evidence |
|---|---|---|
| Backend, Play WS | GET `https://accounts.google.com/.well-known/openid-configuration` | JSON containing `authorization_endpoint`, `token_endpoint` and `userinfo_endpoint`. Keep endpoint URLs stable and consistently mapped by the proxy. The dependency caches discovery globally, so it need not happen once per test. |
| Browser | Navigation to the discovery document's `authorization_endpoint` | A redirect to the configured callback with the received `state` and a synthetic `code`. Check `client_id`, `response_type=code`, `scope`, `redirect_uri`, `hd` and `max_auth_age` against the test configuration. |
| Backend, Play WS | POST to `token_endpoint` | JSON with `access_token`, `token_type`, `expires_in` and `id_token`. Check the code, test client configuration, redirect URI and `grant_type=authorization_code` in the request body. |
| Backend, Play WS | GET to `userinfo_endpoint` | JSON containing at least `name`, `given_name`, `family_name` and `email`; use consistent optional `sub` and `picture`. Verify that the request uses the synthetic access token returned by the token response. |
| Backend, Google credentials transport | Service-account OAuth token request, when credentials need refreshing | Synthetic access-token response. Use an ephemeral, locally generated test service-account key and local test configuration, never a real service account. Confirm this separate transport is intercepted. Refresh may be cached rather than repeated for every login. |
| Backend, Google Directory transport | Groups list request with `userKey` equal to the fixture email | JSON containing `groups`, whose entries have `email` matching the configured group identifiers. Include `groups: []` explicitly for the no-membership case. Verify the request reached the proxy and the real checker parsed its response. |
| Browser to AMIgo | GET `/oauth2callback?code=...&state=...`, then saved destination | The real application verifies state, checks groups, sets its session and redirects to the original protected destination. Never fulfil this request in Playwright. |

The pinned library parses the ID token into `JwtClaims`, requiring `iss`, `sub`, `azp`, `email`, `at_hash`, `email_verified`, `aud`, `iat` and `exp`, with optional `hd`. Use internally consistent synthetic values and an expiry sufficiently in the future. Match the pinned parser's contract; do not claim this harness proves JWT signature, issuer or audience verification that the library does not perform.

Use a synthetic `@guardian.co.uk` identity and fake configured group email addresses. All test values are disposable. Do not obtain live OAuth credentials, export real browser storage or include real session material in reports.

## Isolated application and seed

Backport the non-authentication isolation from #1912: synthetic read-only Dynamo data, local Prism datasets, real controllers, templates, assets and filters. Avoid the normal `AppLoader`, which loads remote configuration and starts AWS-dependent components and jobs. Do not instantiate the full production `AppComponents` to run these tests.

The new test-only bootstrap must:

- Construct the current branch's real Google authentication components, with `enforceValidity=true`, `guardian.co.uk` domain restriction and the existing 90-day `maxAuthAge`.
- Use local test configuration and a local snapshot provider for anti-forgery and Play session signing, without remote configuration or secret rotation.
- Retain the CSRF, security-header and CSP filters.
- Register the real login and callback routes as well as the protected UI routes.
- Never initialise AWS clients, start schedulers or allow writes to fixture data.
- Prove that a fresh unauthenticated request is redirected, even if the harness runs in development mode.
- Stop the mock proxy, Prism stub and application on completion.

**Seed:** `tests/e2e/auth/fixtures.ts`, exporting the authenticated Playwright fixture. This is a fixture module, not a seventh comparison test. Each test gets a new browser context. The fixture installs browser authorisation interception and a unique successful backend response scenario before the first navigation.

Do not preload authenticated storage. The first protected navigation in **each** comparison test must run through the real callback. Authenticate once per test, not once per navigation. The fixture should record callback completion and matching token, user-info and groups requests without imposing counts on discovery or service-account refresh.

For scenario 1.6, the fixture must not navigate to `/` in advance: `/base-images` is the initial protected destination. Otherwise the test would not verify preservation of a deep-link login origin.

Give every test and retry a unique code, email and fixture identifier. Correlate backend requests through the authorisation code, token and email, not a mutable global "current scenario". Account for the library's global discovery cache and per-email group cache. Do not reset shared caches underneath parallel tests.

## Shared UI expectations

Preserve `tests/e2e/support.ts` from #1912, including these assertions:

- Destination URL has the expected path **and the configured application origin**.
- Document title is `AMIgo`.
- Exact level-one page heading is visible.
- The navigation landmark and all five links are visible with the expected `href`: AMIgo `/`, Base images `/base-images`, Roles `/roles`, Recipes `/recipes`, Housekeeping `/housekeeping`.
- No unexpected sign-in redirect remains after authentication.
- Assertions use Playwright's auto-waiting expectations, not fixed sleeps or `networkidle`.
- No test depends on particular records, counts, table columns or backend warning counts.

Use the same desktop Chromium viewport and application data for both architecture runs. Keep authentication observations in the fixture so the six scenario bodies and names remain unchanged.

## 1. Basic UI visibility and navigation

**Seed:** `tests/e2e/auth/fixtures.ts` (planned, fresh context and successful response-mocked login).

### 1.1. Homepage is visible

**File:** `tests/e2e/homepage-is-visible.spec.ts`

**Steps:**

1. Open `/` with no existing authenticated session.
   - expect: The successful response-mocked login completes and returns to `/` on the application origin.
   - expect: The shared UI expectations hold with the heading `¡Hola AMIgo!`.
   - expect: `AMIgo is a self-serve AMI bakery.` is visible.
2. Request `/assets/stylesheets/main.css` from the application.
   - expect: HTTP status is 200 and the content type is `text/css`, allowing normal content-type parameters.

### 1.2. Base images navigation

**File:** `tests/e2e/base-images-navigation.spec.ts`

**Steps:**

1. Open `/` and complete the successful response-mocked login.
2. Click **Base images** in the primary navigation.
   - expect: The shared UI expectations hold at `/base-images` with the heading `Base images`.
   - expect: The base-image table is visible. Do not assert columns, counts or records.
   - expect: The existing authenticated session is accepted without another callback.

### 1.3. Roles navigation

**File:** `tests/e2e/roles-navigation.spec.ts`

**Steps:**

1. Open `/` and complete the successful response-mocked login.
2. Click **Roles** in the primary navigation.
   - expect: The shared UI expectations hold at `/roles` with the heading `Roles`.
   - expect: `Choose a role from the list to see more details.` is visible.
   - expect: The existing authenticated session is accepted without another callback.
   - expect: An empty role list is acceptable. Do not select a role or inspect its details.

### 1.4. Recipes navigation

**File:** `tests/e2e/recipes-navigation.spec.ts`

**Steps:**

1. Open `/` and complete the successful response-mocked login.
2. Click **Recipes** in the primary navigation.
   - expect: The shared UI expectations hold at `/recipes` with the heading `Recipes`.
   - expect: The section heading `Recipes in use` is visible.
   - expect: The existing authenticated session is accepted without another callback.
   - expect: Empty sections are acceptable. Do not assert recipe records.

### 1.5. Housekeeping navigation

**File:** `tests/e2e/housekeeping-navigation.spec.ts`

**Steps:**

1. Open `/` and complete the successful response-mocked login.
2. Click **Housekeeping** in the primary navigation.
   - expect: The shared UI expectations hold at `/housekeeping` with the heading `Housekeeping`.
   - expect: `Orphaned Bakes` is visible.
   - expect: The existing authenticated session is accepted without another callback.
   - expect: Backend warning text or counts do not determine success if the expected page renders.
3. Leave the page without submitting the form.
   - expect: No delete request or other state-changing request is made.

### 1.6. Navigation remains usable and returns home

**File:** `tests/e2e/navigation-remains-usable-and-returns-home.spec.ts`

**Steps:**

1. Open `/base-images` directly with no existing authenticated session.
   - expect: The response-mocked login returns to `/base-images`, not `/`.
   - expect: The shared UI expectations hold with the heading `Base images`.
2. Click **Roles** in the primary navigation.
   - expect: The shared UI expectations hold at `/roles` with the heading `Roles`.
3. Click the **AMIgo** brand link.
   - expect: The shared UI expectations hold at `/` with the heading `¡Hola AMIgo!`.
   - expect: Navigation remains usable throughout and there is no additional login callback.
   - expect: Do not repeat the individual destination content checks from scenarios 1.2 and 1.3.

## 2. Authentication contract checks

Run these separately from the six-test comparison. Use browser checks for the successful journey and reload behaviour, and Scala HTTP/component tests for failure redirects and mock transport contracts. Failed callbacks redirect to `/login`, which itself redirects to Google; do not invent a persistent app login page or allow an automatic retry to conceal the failure.

For callback failures, observe the application response before following redirects, verify the existing error-flash behaviour where applicable, and independently verify that a protected request is still rejected. Backend exceptions remain available in captured server diagnostics, surfaced when a run fails rather than streamed during passing tests.

| Check | Setup and expected outcome |
|---|---|
| Unauthenticated protection | Request each of `/`, `/base-images`, `/roles`, `/recipes` and `/housekeeping` without a session and without following redirects. Each redirects to `/login` and preserves its requested destination. This rules out accidentally running the bypass architecture. |
| Successful login and session reuse | Use valid state, identity and membership of one configured group. Observe callback and all required backend exchanges. Reload the destination and navigate to another protected page: both succeed without another callback. The session was issued by AMIgo, not preloaded by the test. |
| Any-group authorisation | Parameterise membership in each configured group, one at a time. Each succeeds. Membership only in unrelated groups, and `groups: []`, fails and grants no protected-page access. This specifically guards AMIgo's any-group rule rather than the dependency's all-groups rule. |
| Invalid or missing state | Start a real login to establish the browser's session, then alter or omit callback state. Verification fails before token, user-info and groups requests. Do not replace or disable `AntiForgeryChecker`. |
| Token, user-info and Directory failures | Parameterise an HTTP error and malformed response at each applicable boundary. The login does not grant protected access, subsequent stages are not reached, and the failure is visible through the existing response/logging path. Use the pinned library's expected error JSON shape when testing structured errors. |
| Disallowed identity domain | Return a well-formed ID token with an email outside `guardian.co.uk`. Login is rejected before user-info and group lookup. An `hd` hint alone is not proof of domain enforcement. |
| Expired identity | Return a syntactically valid identity with `exp` in the past. The protected action rejects it. Do not assume the callback itself rejects it: the pinned action checks expiry when reading the session. A separate test may establish a short-lived valid session and verify rejection after expiry without fixed sleeps. |
| Both backend transports are intercepted | With an empty credential/group cache, complete a login and observe both the Play WS requests and the Google client's credential refresh and Directory request. A failing or unmatched proxy request fails the test rather than reaching the live service. |
| Isolation and cache behaviour | Run two distinct identities concurrently, then retry a failing identity. Their response scenarios must not leak. Repeated discovery or credential-refresh calls are not required, but one identity must never inherit another's group response. |

No logout scenario is included because this checkout has no logout route.

## Implementation sequence and acceptance gates

1. Backport the read-only UI harness and six test files from #1912, adapting only wiring that depends on its auth-removal base branch. Add tracked Node/Playwright manifests and configuration using the reference version and `.tool-versions`; use mise locally.
2. Build the test-only HTTP/HTTPS response proxy and isolated Google-auth bootstrap. First prove the discovery and Directory transports are both intercepted. If either transport ignores proxy settings, solve that transport's explicit test configuration before proceeding; do not fall back to mocking a returned identity or group set.
3. Add the per-test browser authorisation route and fixtures. Prove real callback state validation, group authorisation and session creation with the separate contract checks.
4. Run the six unchanged UI scenarios against the new architecture. Assert interception evidence at fixture teardown so a green UI test cannot conceal a missing mock.
5. Run the same six scenarios against #1912's isolated development-identity architecture. Use separate clean application processes and the same browser version, viewport and synthetic datasets.
6. Add the mocked-auth run to CI only after it works without live Google or AWS access. Capture mock-request summaries alongside browser traces and server output.

The implementation provides `npm run test:e2e` and the `chromium-google-mocked` project selector. Local runs may share the mocked-auth harness; CI starts a clean server. The per-test authentication assertions prevent an unrelated or bypass server from silently passing. Use separate ports if both architectures run concurrently. Reusing a process serving HTTP 200 at `/` is not sufficient evidence that it uses the intended architecture.

## Architecture comparison

| Criterion | #1912 development identity | Google response interception |
|---|---|---|
| UI coverage | Same six read-only scenarios | Same six read-only scenarios |
| Authentication coverage | Exercises the replacement development identity | Exercises current Google login, callback, any-group authorisation and session enforcement with synthetic provider responses |
| Setup and dependencies | Isolated app, synthetic data and Prism stub | Same data isolation, plus both-client proxy configuration, test-only TLS trust and provider response contracts |
| Session setup | Development identity | A real callback per test; no preloaded identity |
| Expected maintenance pressure | Replacement identity wiring | Pinned provider JSON contracts, Google client transport behaviour and caches |
| Failure diagnosis | Browser trace and server output | Browser trace, server output and correlated backend mock-request journal |

Do not decide from one green run or claim timings that have not been measured. On the same runner, record startup duration separately from six-test duration, perform at least five clean runs of each architecture with retries disabled, and report failures before retries, median timings, setup changes and extra dependencies. Do not mix the authentication contract suite into the six-test timing.

Use browser traces to diagnose UI failures, not as proof of server-side HTTP interception. The mock journal must identify stages and scenario IDs without logging session cookies, OAuth state, request authorisation headers or key material.

### Completion criteria for the eventual implementation

- Exactly the six reference scenarios pass with matching UI assertions under each architecture.
- Every mocked-auth scenario reaches AMIgo's actual callback from a fresh context; the deep-link scenario preserves `/base-images`.
- Server-side request evidence shows identity and group responses were intercepted, including service-account refresh in a cold-cache contract check.
- Negative contract checks demonstrate that the real authentication boundary remains active.
- No live Google/AWS credentials, remote configuration, production-data writes or authentication bypass are needed.
- Results and measured comparison costs are documented separately from this plan.

## Implementation observations

- The six UI scenarios are implemented with the original reference names and assertions, importing the authenticated fixture through `tests/e2e/support.ts`.
- The test server uses ports 9100/9101 by default so it cannot accidentally reuse the previously running auth-removal server on port 9000.
- URL-filtered interception of the external authorisation endpoint did not reliably intercept browser redirect chains. The fixture instead executes the real protected action and login action, replacing only the external redirect as described above. The actual callback remains unmocked.
- The installed Google Directory client uses `admin.googleapis.com`; the proxy supports that host and the older `www.googleapis.com` alias.
- Play WS and the Google Java client were both observed using the mock proxy, including a real synthetic service-account token refresh.
- Negative authentication cases are implemented as Playwright API checks in fresh test contexts, sharing the same backend harness. This replaces the initially proposed Scala HTTP checks without changing the failure assertions.
- The harness uses Scala 2.13 and the project's existing Play/Future style. No Scala 3 or concurrency-framework migration is needed.
- The CI job runs the browser suite, mock contract checks and TypeScript checks. The test server is a compiled test-classpath entry point rather than a live-reloading development runner.
- Cross-architecture timings and a final architecture recommendation are not implementation results. Use the comparison procedure above on the reference branch and this branch before deciding.
