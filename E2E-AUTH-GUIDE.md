# Choosing an authentication approach for Playwright E2E tests

For **web applications using Playwright Test**. The goal is to run application tests without depending on live Google authentication. Keep tests of real Google integration separate.

## Choose your starting point

| Your situation | Start with |
|---|---|
| You need to test application journeys, and can replace the application's identity resolver | **1. Supply a test identity** |
| Your application accepts a signed session or cookie, and you can configure local trust | **2. Issue a test credential** |
| Your browser uses a small provider API, or you need precise simulated responses from browser or backend dependencies | **3. Intercept authentication interactions**, using Playwright routes or WireMock at the appropriate boundary |
| You need to exercise redirects, the backend callback and session creation | **4. Run a local identity provider** |
| You need to confirm the application actually works with Google | **5. Use real test credentials**, in a separate, non-isolated suite |

These can be combined. For example, use a local provider for login tests and reuse its resulting session for ordinary application tests.

## 1. Supply a test identity

**Good when:** most tests concern authenticated application behaviour, rather than how the user signs in. Particularly useful when production authentication already happens upstream and the application has an identity-provider interface.

**How to implement**

1. Keep the application's protected routes and authentication action.
2. In isolated test/development wiring, replace the identity resolver with one returning a known test user.
3. Start that application with Playwright's `webServer` and test pages normally. Add controlled identity variants if the application has different user permissions.

**Example code:** Amigo [#1912](https://github.com/guardian/amigo/pull/1912).

- [Development identity provider](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/app/authentication/DevelopmentIdentityProvider.scala#L7-L12)
- [Production/development wiring](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/app/components/IdentityProviderWiring.scala#L23-L35)
- [E2E application components](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/test/e2e/E2EComponents.scala#L31-L53)

**Watch out:** this does not test credential verification or provider login. Keep the substitution out of production wiring and cover the real authentication boundary separately. The example supplies one fixed identity; additional role variants would be an extension.

## 2. Issue a test credential

**Good when:** you want the application to perform its real credential verification and authorisation, without completing provider login. Panda applications are a concrete example.

**How to implement**

1. Configure the isolated application to trust a test signing key, never a production key.
2. Generate a fresh credential with the identity, expiry and claims the application expects. Seed matching permission data.
3. Install the cookie using `context.addCookies()` before navigation, or populate the application's expected authentication storage.

**Example code:** [guardian/workflow-frontend](https://github.com/guardian/workflow-frontend).

- [Generate local signing keys](https://github.com/guardian/workflow-frontend/blob/b3d6cc96dbf7a99b48a417e50394625db3ad8e2a/e2e-tests/setup/panDomainKeys.ts#L16-L27)
- [Seed trusted keys and permissions](https://github.com/guardian/workflow-frontend/blob/b3d6cc96dbf7a99b48a417e50394625db3ad8e2a/e2e-tests/setup/stack/seedS3.ts#L25-L50)
- [Create the signed Panda cookie](https://github.com/guardian/workflow-frontend/blob/b3d6cc96dbf7a99b48a417e50394625db3ad8e2a/e2e-tests/setup/panDomainCookie.ts#L36-L57)
- [Install it in the browser fixture](https://github.com/guardian/workflow-frontend/blob/b3d6cc96dbf7a99b48a417e50394625db3ad8e2a/e2e-tests/steps/fixtures.ts#L77-L87)

For permission variants, see [guardian/flexible-restorer's identities](https://github.com/guardian/flexible-restorer/blob/338202a3721f360debe5b3d40eecd718cccb3ae8/e2e-tests/setup/panDomainCookie.ts#L12-L73) and [access-denied tests](https://github.com/guardian/flexible-restorer/blob/338202a3721f360debe5b3d40eecd718cccb3ae8/e2e-tests/steps/authentication.steps.ts#L34-L66).

**Watch out:** an arbitrary cookie or fabricated Google token will not work with a real verifier. You need control of the accepted test trust. Expired or invalid credentials may trigger live reauthentication unless that path is also isolated.

## 3. Intercept authentication interactions

**Good when:** the provider contract is small, you cannot easily change provider configuration, or you need specific failures such as malformed responses or unavailable endpoints.

**How to implement**

1. Identify every interaction: browser script/API calls, redirects, backend token exchange, userinfo and any group lookup.
2. Register browser mocks with `context.route()` before navigation. For server-originated requests, use a server-side stub or interception mechanism; Playwright browser routes cannot intercept them.
3. Return realistic responses, assert important request fields, and fail unexpected external calls.

### Backend HTTP stubbing with WireMock

**Good when:** the application server calls token, userinfo, authentication or group endpoints and you want controlled responses without writing a bespoke HTTP server. Particularly useful for testing rejected credentials, malformed responses, delays and connection failures.

**How to implement**

1. Start local [WireMock OSS](https://wiremock.org/docs/standalone/java-jar/) alongside the app. Point the application's outbound endpoints at it where configuration allows.
2. [Register mappings](https://wiremock.org/docs/stubbing/) matching the request method, path and relevant headers/body; supply successful or deliberately failing responses.
3. Use Playwright's API request support to configure and [verify requests through WireMock's admin API](https://wiremock.org/docs/verifying/#verifying-via-the-json--http-api), while browser assertions check the application outcome. Prefer a per-worker instance with a backend configured to use it; otherwise correlate requests and never globally reset a server shared by concurrent tests.

**Existing Guardian examples:** these are backend integration tests, not ready-made Playwright login suites.

- [guardian/identity: stub Okta authentication success/rejection](https://github.com/guardian/identity/blob/47c8696df218170217a8f4dbf47afb0037c214b6/integration/src/test/scala/com/gu/identity/integration/util/OktaApiStubs.scala#L139-L167), then [call the application's real authentication endpoint and check its response](https://github.com/guardian/identity/blob/47c8696df218170217a8f4dbf47afb0037c214b6/integration/src/test/scala/com/gu/identity/integration/servlet/AuthServletIntegrationTest.scala#L14-L59).
- [guardian/discussion-api: stub its Identity API authentication callback](https://github.com/guardian/discussion-api/blob/5d87ad19dcfece5294d7e5a1761bfc4a833ff93a/integration/src/test/scala/com/gu/discussion/api/integration/IdentityApiMock.scala#L157-L180).

**Watch out:** WireMock core is an HTTP stub server, not a complete OAuth/OIDC provider. Its [JWT extension](https://wiremock.org/docs/jwt/) can generate signed tokens and JWKS, but redirects, code lifecycle and validation still need modelling. Prefer option 4 when you want a provider library to handle the routine protocol conversation.

If endpoints cannot be changed, WireMock also supports [HTTPS forward-proxy interception](https://wiremock.org/docs/proxying/#browser-proxying-of-https). Each backend client must use that proxy and trust its certificates; configuring Playwright's browser proxy is not enough. Disable automatic proxy pass-through and avoid forwarding fallback stubs for isolated tests. This adds TLS/client-configuration complexity compared with local endpoint substitution.

**Simple browser example:** [guardian/interview-questions#260](https://github.com/guardian/interview-questions/pull/260).

- [Mock the GSI API, Drive upload and document navigation](https://github.com/guardian/interview-questions/blob/1cfc3a1b06fe578b818ada2d27dc5b53cdd1e103/tests/e2e/googleExportMock.ts#L34-L70)
- [Drive the export UI and check the captured upload](https://github.com/guardian/interview-questions/blob/1cfc3a1b06fe578b818ada2d27dc5b53cdd1e103/tests/e2e/interview-script.spec.ts#L26-L54)

That example uses an access token for a browser-side export feature; it does not establish a server login or process identity claims.

**Browser plus backend example:** Amigo [#1914](https://github.com/guardian/amigo/pull/1914).

- [Browser redirect/state fixture](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/tests/e2e/auth/fixtures.ts#L45-L144)
- [Backend provider-response proxy](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/tests/e2e/auth/provider.mjs#L103-L169)
- [Application proxy/trust configuration](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/test/e2e/E2EServer.scala#L19-L55)
- [Authentication contract tests](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/tests/e2e/auth/authentication.spec.ts#L38-L115)

**Watch out:** mocks can encode incorrect assumptions. The bespoke Amigo token fixture works with its dependency's decoder; it is not evidence of Google signature verification. If your application verifies signatures, supply properly signed local tokens and trusted local keys instead.

## 4. Run a local identity provider

**Good when:** your tests need the application's real login redirect, callback and session creation, and provider endpoints can be configured or replaced.

**How to implement**

1. Start a local OAuth/OIDC provider, such as `mock-oauth2-server` or `oidc-provider`.
2. Point application discovery/endpoints at it. Configure the expected client, callback, claims and local signing trust without disabling checks your application normally performs.
3. Let Playwright complete the local login flow. Stub separate external dependencies, such as Google Groups, explicitly.

**Example code:** Amigo [#1915](https://github.com/guardian/amigo/pull/1915).

- [Configure the local provider and group-checker stub](https://github.com/guardian/amigo/blob/a6048df73039dd12116dd8541b43518ed6968c10/test/e2e/E2EGoogleAuth.scala#L18-L85)
- [Replace discovery with the local provider](https://github.com/guardian/amigo/blob/a6048df73039dd12116dd8541b43518ed6968c10/test/e2e/E2EApplicationLoader.scala#L29-L44)
- [Playwright login fixture](https://github.com/guardian/amigo/blob/a6048df73039dd12116dd8541b43518ed6968c10/e2e/tests/support.ts#L3-L21)

For Panda with local OIDC, see [guardian/grid's provider configuration](https://github.com/guardian/grid/blob/0ace378425d3dd1e1f324729eedd48dce9c85f98/dev/oidc-provider/make-provider.js#L1-L33) and [Playwright login steps](https://github.com/guardian/grid/blob/0ace378425d3dd1e1f324729eedd48dce9c85f98/e2e-tests/steps/common.steps.ts#L6-L44).

**Watch out:** a local provider tests your integration against that provider, not Google's behaviour. Check claim placement and protocol defaults. Amigo's example tests a successful identity and stubs group membership; extend it if you need rejected identities or Directory API coverage.

**Where WireMock fits:** the official [WireMock OAuth/OIDC demonstration](https://github.com/wiremock/wiremock-spring-boot-oauth2-demo/blob/5d191445b90501fc2efefa3069eccec3534cf9e8/wiremock-oauth2/mappings/oauth-oidc-stubs.json#L46-L145) combines HTTP mappings, templates and the JWT extension to simulate a provider. Choose this when precise endpoint control matters and you accept maintaining the protocol fixtures. It is not a turnkey provider: do not assume the sample implements production code replay, client or redirect validation. Its [stock client scopes omit `openid`](https://github.com/wiremock/wiremock-spring-boot-oauth2-demo/blob/5d191445b90501fc2efefa3069eccec3534cf9e8/src/main/resources/application.yml#L4-L17), so successful demo login does not itself prove ID-token verification.

## 5. Use real test credentials

**Good when:** you specifically need evidence that real Google configuration, consent, federation or API access still works. **Not suitable as the prerequisite for an isolated suite.**

**How to implement**

1. Use authorised non-production accounts and a separate test project, with credentials supplied securely.
2. Authenticate in a Playwright setup project, wait for completion and save state.
3. Load that state into fresh test contexts. Keep this suite separately selectable and regenerate expired state.

**Example code:** Playwright's official [setup-project login and state reuse](https://playwright.dev/docs/auth#basic-shared-account-in-all-tests), [API login](https://playwright.dev/docs/auth#authenticate-with-api-request), and [per-worker account fixture](https://playwright.dev/docs/auth#moderate-one-account-per-parallel-worker). These are runner patterns, not a verified Guardian live-Google implementation or a Google-specific selector recipe.

**Watch out:** authenticating once moves the external dependency into setup; it does not remove it. Google may block automated sign-ins or require additional verification. Do not try to bypass those controls. Never commit saved authentication state.

## Apply these rules whichever option you choose

- **Reuse is separate from acquisition.** `storageState` can reuse locally or remotely established authentication. It does not issue credentials or extend their validity.
- **Keep authorisation real where possible.** Include a valid identity without access, not just successful login.
- **Avoid account collisions.** Use separate identities/data for parallel workers when tests mutate shared state.
- **Check isolation explicitly.** Cover browser and backend traffic, including assets and reauthentication. Record unexpected requests and fail the test; merely aborting them may leave the test passing.
- **Account for service workers.** For straightforward browser routing, consider `serviceWorkers: 'block'`; retain separate worker-enabled coverage if the application needs them.

Runner details: [authentication/state](https://playwright.dev/docs/auth), [network routing](https://playwright.dev/docs/network), and [service-worker limitations](https://playwright.dev/docs/network#missing-network-events-and-service-workers).

**Recommended default:** use option 1 or 2 for most application journeys, a smaller option 3 or 4 suite for authentication contracts, and option 5 only for separately scoped provider checks.

For pinned-source evidence, qualifications and research limitations, see [the full research report](E2E-AUTH-RESEARCH.md). The linked implementations were inspected, not executed during this research.
