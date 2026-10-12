# Isolated authentication testing for web applications with Playwright

Research date: 12 October 2026.

Scope: web applications using Playwright Test.

## Executive summary

For engineering guidance, use five categories: **substitute application identity**, **provision a test-issued credential or session**, **intercept authentication interactions**, **run a local identity provider**, and **authenticate against the real provider**. This is a proposed taxonomy based on the different boundaries demonstrated by the Guardian implementations, rather than an industry-standard classification.[^1][^2][^3][^4][^5] **Authenticating in advance and reusing state is an optimisation that can accompany several categories**, not an independent solution to obtaining valid authentication.[^6] Moving Google authentication upstream is likewise an architectural decision that creates a convenient application identity boundary; it does not make production authentication disappear.[^7][^8] For the required Playwright web-app suites, the recommended default is controlled application identity or locally issued sessions for ordinary user journeys, with a smaller local-provider or interception suite for authentication integration and separately scoped real-provider checks.[^1][^3][^4][^6]

## Scope and terminology

This report covers **web applications only**, with **Playwright Test as the required E2E runner**. “Isolated” means that the suite does not require live Google authentication or other uncontrolled external services; localhost services and deterministic test infrastructure are acceptable. This is a working definition for the guidance, not a claim that every reviewed suite is fully hermetic.

Separate three questions when choosing an approach:

1. **Where is the authentication boundary replaced or supplied?**
2. **How is the resulting identity/session reused?**
3. **What behaviour is this test intended to prove?**

These are recommended decision dimensions: the examples combine different provider, application and browser boundaries, while Playwright supplies mechanisms for reusing their resulting state.[^1][^2][^3][^4][^6]

Do not interchange **ID token**, **access token** and **application session**. Google ID tokens assert identity; access tokens authorise Google API calls; an application's own session represents its accepted login. GSI also separates authentication from authorisation APIs. A supplied access token does not automatically establish a server-side application session.[^9][^10][^11]

## 1. Suggested category titles

### Comparison matrix

The judgements below are engineering recommendations derived from the cited mechanisms, not measured performance results.

| Category | What the suite supplies or replaces | Useful coverage | Main trade-off | Guardian example |
|---|---|---|---|---|
| **A. Substitute application identity** | An application identity resolver returns a controlled principal | Authenticated pages, workflows and identity-dependent behaviour | Omits provider authentication and, depending on the seam, credential validation | Amigo #1912[^1][^12] |
| **B. Provision a test-issued credential or session** | A trusted test issuer generates the credential the application actually accepts | Real credential parsing/verification and application authorisation | Requires test-controlled trust and coherent permission fixtures; skips interactive provider login | Workflow Frontend and Flexible Restorer Panda cookies[^3][^13] |
| **C. Intercept and simulate authentication interactions** | Browser API, script, redirect or server transport returns controlled responses | Application code reached through those interactions, including selected errors | Hand-written contracts can diverge from the provider; browser interception cannot cover server calls | Amigo #1914; Interview Questions #260[^2][^5] |
| **D. Run a local authentication provider** | A local OAuth/OIDC server replaces Google | Real application redirects, callback and session handling against a local provider | Local provider behaviour is not Google behaviour; separate Google APIs may still need stubs | Amigo #1915; Grid[^4][^14] |
| **E. Authenticate against the real provider** | Real test credentials and live provider-issued state | Actual external-provider compatibility and configuration | Not strictly isolated; account and provider conditions affect execution | Playwright documents login-and-reuse mechanics; Google documents automation restrictions[^6][^15] |

These categories are not mutually exclusive at suite level. Classify **each replacement boundary**: for example, “local OIDC provider, group-checker dependency stub, and reused application session” accurately describes a hybrid. Amigo #1915 is such a hybrid, not simply browser interception with a library.[^4]

### Two dimensions that should not become competing categories

**State reuse: authenticate once, then reuse.** Playwright can capture authentication in a setup project or fixture and load `storageState` into fresh test contexts. This makes subsequent tests faster, but the setup must still obtain accepted authentication somehow. That setup could use real Google, a local provider, a test login API, or a locally signed session.[^6][^16]

**Architecture: delegate authentication upstream.** An app can resolve identity from a verified upstream assertion instead of calling Google directly. Its E2E suite can then substitute the resolver or test the assertion contract under local trust. The architecture enables a seam; the selected test technique still belongs to A, B, C or D. Amigo's ALB/Cognito design demonstrates that separation.[^7][^8][^12]

“Providing a token in advance” therefore needs a qualifier: **cached real credential**, **locally test-issued credential**, or **synthetic input to a mocked verifier**. These imply materially different trust and coverage. Panda's signed test cookies are category B, not merely saved results of a live Google login.[^3][^13][^17]

## 2. What the three Amigo comparisons actually demonstrate

The revisions below are the inspected PR heads, not assertions about currently deployed code. All three use Playwright, but their authentication coverage differs.[^1][^2][^4]

| Implementation | Inspected PR head | Authentication boundary |
|---|---|---|
| [#1912](https://github.com/guardian/amigo/pull/1912) | `10c179959d65960e5ee683a970d4c3a6d13aed1f` | Development identity resolver |
| [#1914](https://github.com/guardian/amigo/pull/1914) | `dc90a12c44ddc2bc6c81533e865e31311408b299` | Browser redirect substitution plus server HTTPS interception |
| [#1915](https://github.com/guardian/amigo/pull/1915) | `a6048df73039dd12116dd8541b43518ed6968c10` | Local OAuth provider plus group-checker method stub |

### #1912: substitute identity, not remove production authentication

The E2E components require development mode and use a provider that returns a configured identity for every request. The application authentication action remains in the path, but this provider does not validate a token or initiate Google authentication. The Playwright tests directly navigate to pages and assert rendered content.[^1][^12][^18]

The description “getting rid of auth from within the app altogether” needs qualification. At this head, production Amigo still verifies ALB-signed identity assertions, and the architectural change is already present in the PR's base. The E2E change exploits an existing development identity seam; it does not itself introduce all of the Google-auth removal.[^7][^19]

**Assessment:** a strong, simple boundary for application journeys when provider integration is covered separately. It does not prove production ALB assertion verification or Google admission. The current fixed development identity also should not be presented as a demonstrated multi-role fixture framework.[^1][^7][^12]

### #1914: bespoke interception at browser and server boundaries

The browser fixture checks the application's unauthenticated redirect and generated Google authorisation parameters. It preserves the application's original state while redirecting to a synthetic callback. Separately, a Node-built-in HTTP/HTTPS proxy supplies discovery, code exchange, userinfo, delegated service-account token exchange and Directory API group responses to the backend. The real application login controller and group checker remain active.[^2][^20][^21]

This gives substantially more application-authentication contract coverage than navigation alone: the tests include state failures, permitted groups, wrong domain, expiry and provider-response failures, and assert backend event sequences. Some checks use Playwright's API request fixture rather than browser navigation, so describe them as authentication contracts alongside browser E2E tests.[^22]

**Assessment:** useful when retaining existing hard-coded provider endpoints and validating precise outbound interactions matters. Its bespoke proxy, TLS/trust configuration and credential fixtures create more maintenance than simply stubbing the principal. It does not exercise Google's login UI.[^2][^20][^21]

### #1915: local provider, not equivalent transport interception

This version uses `no.nav.security:mock-oauth2-server:6.0.4`, replaces the Google discovery cache with local discovery, and lets Playwright navigate through the local authorisation page. The fixture fills the local subject field, submits sign-in, checks callback state, and waits for the authenticated application before running page assertions.[^4][^23]

Google Groups is replaced with a Mockito method stub returning an allowed group for the fixed fixture identity. Thus the OAuth browser/callback path runs, but delegated service-account and Directory API HTTP calls do not. Its demonstrated coverage is happy-path navigation rather than #1914's broader negative matrix.[^4][^23]

**Assessment:** less bespoke protocol infrastructure, with a real local redirect/code-exchange interaction. Library usage does not automatically mean higher coverage: downstream authorisation dependencies and failure cases still need deliberate tests.[^4][^24]

### Important cryptographic qualification

The inspected `play-googleauth` v42.0.0 decoder reads the JWT header and claims but does not consume its signature. It checks the email domain, obtains userinfo, and constructs the application's identity. Its anti-forgery **state** JWT is separately verified; `enforceValidity` controls subsequent session-identity expiry, not Google ID-token signature verification. This explains why #1914's placeholder-signature ID token is compatible with that dependency.[^25][^26][^27]

Do **not** claim these mocks demonstrate Google ID-token cryptographic verification. Equally, do not infer a general production vulnerability from this observation: Google distinguishes tokens obtained directly through an authenticated backend HTTPS exchange from tokens supplied by a browser/client. A browser-posted GSI credential needs the relevant token verification; the trust channels are different.[^28][^29]

For an application that does verify token signatures, preserve that verifier in isolated tests and use local signing keys/JWKS. A locally signed token can demonstrate the application's verifier contract; it still does not demonstrate Google's key infrastructure or policies.[^24][^29]

## 3. The simpler browser-side case: Interview Questions

[guardian/interview-questions#260](https://github.com/guardian/interview-questions/pull/260), at head `1cfc3a1b06fe578b818ada2d27dc5b53cdd1e103`, is an **authorised Google Docs export feature**, not a server login-session example. The browser obtains an access token and uploads directly to Drive. The inspected path does not decode ID-token claims.[^5][^30]

Its mock fulfils the GSI script with a minimal `google.accounts.oauth2.initTokenClient` implementation, triggers the application's callback with a synthetic access token, captures the Drive upload, and supplies the resulting document navigation. Playwright drives the actual export UI and checks the upload and document URL.[^5][^31]

**Recommended use:** this is a good illustration of category C when the application only needs a small browser API contract and does not consume identity claims. Do not generalise its synthetic access token into a recipe for server-side login. It does not test consent, genuine Drive permission enforcement, or Google's UI.[^5][^30]

## 4. Panda: pre-setting a cookie while retaining verification

In [guardian/workflow-frontend](https://github.com/guardian/workflow-frontend), the E2E setup generates RSA keys, seeds matching Panda settings and permissions into LocalStack, signs a synthetic identity with expiry and MFA fields, and installs its cookie before browser navigation. The application still checks domain, MFA policy and Workflow permissions.[^3][^32]

[guardian/flexible-restorer](https://github.com/guardian/flexible-restorer) follows the same pattern with local/test-only static keys and identity variants for different permissions. Its tests include an authenticated identity without application access and verify the access-denied page.[^13][^33]

**The cookie's existence is not enough.** Panda credentials are signed, self-contained payloads; the verifier checks the signature, then expiry and application validation. The app must trust the test key, and permission fixtures must agree with the identity. These are test-issued credentials under isolated trust, not arbitrary cookies accepted by production.[^17][^34]

Fresh accepted credentials take the authenticated path without initiating Google login. Missing, rejected or expired credentials can enter reauthentication, and the inspected Workflow settings retain Google's discovery URL. Consequently, the seeded happy path is not proof that every suite path is offline.[^35]

An alternative exists in [guardian/grid](https://github.com/guardian/grid): its Playwright flow logs into a local `oidc-provider`, and Panda settings point to that local discovery endpoint. This demonstrates category D rather than auth-cookie seeding. A nearby browser cookie in that flow controls image blur, not authentication.[^14][^36]

## 5. Upstream Google authentication: clarify option 4

At the #1912 head, Amigo uses an **ALB → Cognito → Google federation** arrangement, with Guardian Cognito-trigger/Gatekeeper Lambdas checking admission through configured Google groups. ALB authenticates before forwarding to the app; the Lambda is not shown minting the JWT Amigo verifies.[^8][^37]

Amigo reads `x-amzn-oidc-data` and verifies ES256, expected ALB signer, header expiry and signature using the ALB key endpoint before resolving identity. It requires the expected identity claim types, including `email_verified` as the string `"true"`. This is not the same as accepting an arbitrary email header.[^7][^38]

AWS distinguishes this signed claims assertion from the separately forwarded access token and unsigned subject header. The ALB assertion is not a Google ID token. Amigo's identity provider consumes the signed claims header, not the forwarded access token.[^7][^39]

**Recommendation:** describe this architecture as **“delegate provider authentication and resolve verified upstream identity”**. Test application journeys at the identity seam, test assertion verification separately, and retain upstream integration coverage. Do not expose a production bypass or teach the browser to send arbitrary supposedly trusted identity headers.[^7][^12][^38]

This fits applications whose business logic needs an identity rather than a Google API token. It is not a drop-in substitute for an application legitimately calling Google APIs on the user's behalf: the ID-token/access-token distinction still applies. Whether such token use is organisationally discouraged is the user's supplied policy context, not independently established by this research.[^9][^10][^39]

[guardian/pan-domain-lambda](https://github.com/guardian/pan-domain-lambda) is a different boundary: its inspected handler verifies a Panda cookie and returns an API Gateway Allow/Deny policy. Do not cite it as the implementation of Amigo's ALB/Cognito federation.[^40]

## 6. Playwright implementation guidance

The following recommendations use Playwright mechanisms, rather than adding another E2E runner.

### Prepare authentication before navigation

Use a setup project dependency or worker-scoped fixture for session acquisition, then seed each fresh browser context. Playwright recommends setup projects over bare global setup for runner-managed reporting, fixtures and tracing. Wait for login completion before capturing state.[^6][^16]

`storageState` captures cookies and localStorage; IndexedDB capture requires `indexedDB: true` in supported versions. It does not capture sessionStorage through the normal state mechanism; the documented workaround restores that separately with an origin-guarded `addInitScript`. Install state, cookies, scripts and routes before the app first needs them.[^41][^42]

Prefer context-wide routes for multi-page auth flows: page routing does not intercept a popup's first request. Use `context.addCookies` for cookies rather than attempting to override a forbidden `Cookie` header through route continuation.[^43][^44]

### Keep identities and server data isolated

Shared-account state is suitable only when tests cannot interfere through server-side changes. Playwright recommends one account per parallel worker when tests mutate shared account state. Browser isolation alone does not isolate the database; the recommendation is to allocate coherent users, permissions and data, with an execution namespace where concurrent runs could collide.[^6][^32]

Saved authentication state can impersonate its account, must not be committed, and does not extend credential expiry. Regenerate it when needed. UI mode does not run the setup project by default, which can otherwise leave expired state in local runs.[^6]

### Close both network boundaries

`page.route` and `context.route` sit in the browser network path. They cannot intercept a server-to-Google code exchange or Directory lookup. `APIRequestContext` also sends requests outside the browser page's routing boundary, even when it shares browser cookies.[^45][^46][^47]

For straightforward browser mocks, consider `serviceWorkers: 'block'`, because worker-handled requests can evade ordinary routing. If the application relies on service workers, blocking them changes the tested behaviour, so provide separate worker-enabled coverage rather than silently assuming equivalence.[^48]

**Recommended isolation check:** permit only expected local destinations, fulfil known mocks, record unexpected traffic, abort it and explicitly fail the test on those records. Aborting a request does not itself guarantee a failed test. Apply equivalent controls to backend/provider traffic; browser routing alone is not a complete egress firewall.[^45][^49]

Be careful with `route.fetch`, route fall-through and redirects: fetching performs real network access, `continue` bypasses remaining route handlers, and redirect handling has documented limits. Mocking auth alone also does not isolate unrelated assets: all three inspected Amigo heads retain external CDN assets.[^49][^50]

## 7. Choosing the approach

This decision sequence is recommended synthesis from the examples above.

| Question | Suggested starting point |
|---|---|
| Are you testing ordinary authenticated application journeys, not login? | A controlled identity seam (A), or a trusted locally issued application credential (B). |
| Must the application verifier and permission checks run? | B, with realistic identities, local trust and permission fixtures. |
| Must redirects, callback handling and session creation run? | A local provider (D), or controlled transport responses (C) at every actual boundary. |
| Is Google consumed only through a small browser API without identity-claim processing? | A narrow browser API/script mock (C), as in Interview Questions. |
| Is provider behaviour hard-coded, or are malformed responses/outages central to the test? | Targeted browser/server interception (C); consider its maintenance cost. |
| Does the architecture already provide a verified principal upstream? | Substitute the application's identity resolver for journeys; keep verifier/upstream coverage separate. |
| Are you trying to prove real Google registration, consent or federation still works? | Separate real-provider integration checks (E), explicitly not isolated. |

Supporting examples and mechanisms: application seams,[^1][^12] signed Panda credentials,[^3][^17] local providers,[^4][^14] browser-only export mocking,[^5] backend interception,[^20][^21] and live-provider limitations.[^15][^51]

### Library versus bespoke mocking

Prefer an established local protocol library when the objective includes a real protocol conversation. NAV's mock server implements code handling, discovery, JWKS, token signing and userinfo; `oidc-provider` provides a broader configurable provider implementation. Neither guarantees exact Google behaviour.[^24][^52]

Check the actual feature contract rather than the word “OIDC”. NAV 6.0.4 has permissive test-login behaviour and does not require a missing PKCE verifier in the inspected helper. `oidc-provider`'s defaults and claim placement also need deliberate configuration. Adding a library reduces protocol plumbing; it does not eliminate fixture design or missing-negative-case risks.[^53][^54]

### Keep three layers of coverage

Recommended coverage allocation:

1. **Most browser journeys:** controlled identity or local sessions, focused on application outcomes.
2. **Smaller isolated auth-contract suite:** real application verification/callback/session code, local issuer or targeted failures, including unauthorised identities.
3. **Separate provider checks:** real upstream interoperability where required, with explicit external dependency and execution policy.

This is a proposed balance, not a claim that the reviewed repositories already implement all three layers.[^1][^22][^33][^38][^51]

For layer 2, select cases for the actual contract: missing/invalid state; wrong issuer/audience/signature or expiry where verified; provider denial/outage; valid identity without access; logout/session expiry; and identity leakage between workers. Do not assert a nonce, PKCE or cryptographic rejection test proves a check that the application's verifier never performs.[^22][^25][^26][^33][^53]

## 8. Real credentials, automation and isolation

Real Google test credentials offer genuine provider compatibility, but cannot make an externally dependent suite isolated. Google says it may block sign-in from browsers controlled by software automation and sometimes requires additional verification. These sources support **possible blocking or challenges**, not inevitable CAPTCHA on every OAuth login; no bypass approach is recommended.[^15][^51]

Likewise, do not state that every Google sign-in test account requires allowlisting or that every authorisation expires in seven days. Google's testing-status guidance includes an exception for basic name/email/profile/OpenID scopes. Its OAuth policy recommends separate projects for testing and production.[^55][^56]

## Confidence assessment

**High confidence:** classification of the supplied PR implementations; the browser/backend interception distinction; Panda test-signing and verification; Amigo's development-versus-production identity seam; and the inspected `play-googleauth` v42.0.0 decoder behaviour. These are supported by pinned source and official documentation, not just PR titles.[^1][^2][^4][^17][^25][^45]

**Recommendations and assumptions:** the five-category taxonomy, default coverage allocation and decision matrix are synthesis. “Isolation” is defined above to permit controlled local dependencies. The requirement for Playwright and web-only scope, and the policy discouraging application reuse of Google tokens, come from the user.

**Not verified:** no suite was executed and no deployed AWS stack was inspected. Repository code demonstrates intended paths and test assertions, not runtime pass status, complete egress isolation or currently deployed revisions. The CDK Lambda artifact reference is not an immutable source-commit reference, so inspected Lambda code cannot prove the deployed binary revision.[^57]

**Version provenance:** Amigo and Interview Questions findings use PR heads; Panda examples use reported indexed revisions, with relevant Scala verifier releases inspected separately. The `play-googleauth` release resolves to `314210c2e77b2d5ccc6ef2c8a9c3150432eeb3b8`; its indexed default revision was different and was not substituted for v42.0.0. NAV library findings use 6.0.4; the inspected `oidc-provider` source declares 9.12.2 and is not asserted to be Grid's installed version.[^25][^52][^58]

**Secondary-source limitations:** the requested [Medium article](https://medium.com/@delyce35/end-to-end-testing-with-playwright-managing-google-authentication-across-tests-fbca95eeb991) returned HTTP 403 with a blocking page; the requested [Autonoma article](https://getautonoma.com/blog/how-to-test-google-oauth-login) returned HTTP 404 with `DEPLOYMENT_NOT_FOUND` during research. Their contents were not inferred or used. Official web documentation is live, so check APIs against the installed Playwright version.[^41]

## Footnotes

[^1]: [guardian/amigo: test/e2e/E2EComponents.scala:31-53](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/test/e2e/E2EComponents.scala#L31-L53).
[^2]: [guardian/amigo: tests/e2e/auth/fixtures.ts:45-107](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/tests/e2e/auth/fixtures.ts#L45-L107).
[^3]: [guardian/workflow-frontend: e2e-tests/setup/panDomainCookie.ts:36-57](https://github.com/guardian/workflow-frontend/blob/b3d6cc96dbf7a99b48a417e50394625db3ad8e2a/e2e-tests/setup/panDomainCookie.ts#L36-L57); [e2e-tests/steps/fixtures.ts:77-87](https://github.com/guardian/workflow-frontend/blob/b3d6cc96dbf7a99b48a417e50394625db3ad8e2a/e2e-tests/steps/fixtures.ts#L77-L87).
[^4]: [guardian/amigo: test/e2e/E2EGoogleAuth.scala:18-85](https://github.com/guardian/amigo/blob/a6048df73039dd12116dd8541b43518ed6968c10/test/e2e/E2EGoogleAuth.scala#L18-L85).
[^5]: [guardian/interview-questions: tests/e2e/googleExportMock.ts:34-70](https://github.com/guardian/interview-questions/blob/1cfc3a1b06fe578b818ada2d27dc5b53cdd1e103/tests/e2e/googleExportMock.ts#L34-L70).
[^6]: [Playwright: Authentication, including shared accounts, worker accounts, expiry and state protection](https://playwright.dev/docs/auth).
[^7]: [guardian/amigo: app/authentication/AlbIdentityProvider.scala:46-141](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/app/authentication/AlbIdentityProvider.scala#L46-L141).
[^8]: [guardian/cdk: src/patterns/ec2-app/base.ts:576-618](https://github.com/guardian/cdk/blob/820366c5ad463d1619d265016a02909d08804db5/src/patterns/ec2-app/base.ts#L576-L618); [src/patterns/ec2-app/base.ts:634-675](https://github.com/guardian/cdk/blob/820366c5ad463d1619d265016a02909d08804db5/src/patterns/ec2-app/base.ts#L634-L675).
[^9]: [Google OpenID Connect: ID-token payload](https://developers.google.com/identity/openid-connect/openid-connect#an-id-tokens-payload).
[^10]: [Google Identity Services: separated authentication and authorisation](https://developers.google.com/identity/gsi/web/guides/overview#separated_authentication_and_authorization_moments).
[^11]: [guardian/play-googleauth: play-v30/src/main/scala/com/gu/googleauth/actions.scala:206-214](https://github.com/guardian/play-googleauth/blob/314210c2e77b2d5ccc6ef2c8a9c3150432eeb3b8/play-v30/src/main/scala/com/gu/googleauth/actions.scala#L206-L214); [Playwright: API authentication](https://playwright.dev/docs/auth#authenticate-with-api-request).
[^12]: [guardian/amigo: app/components/IdentityProviderWiring.scala:23-35](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/app/components/IdentityProviderWiring.scala#L23-L35); [app/authentication/DevelopmentIdentityProvider.scala:7-12](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/app/authentication/DevelopmentIdentityProvider.scala#L7-L12).
[^13]: [guardian/flexible-restorer: e2e-tests/setup/panDomainCookie.ts:12-73](https://github.com/guardian/flexible-restorer/blob/338202a3721f360debe5b3d40eecd718cccb3ae8/e2e-tests/setup/panDomainCookie.ts#L12-L73); [e2e-tests/setup/seedS3.ts:31-55](https://github.com/guardian/flexible-restorer/blob/338202a3721f360debe5b3d40eecd718cccb3ae8/e2e-tests/setup/seedS3.ts#L31-L55).
[^14]: [guardian/grid: e2e-tests/steps/common.steps.ts:6-44](https://github.com/guardian/grid/blob/0ace378425d3dd1e1f324729eedd48dce9c85f98/e2e-tests/steps/common.steps.ts#L6-L44).
[^15]: [Google Account Help: supported browsers and software-automated sign-in](https://support.google.com/accounts/answer/7675428?hl=en&co=GENIE.Platform%3DDesktop).
[^16]: [Playwright: setup projects versus global setup](https://playwright.dev/docs/test-global-setup-teardown).
[^17]: [guardian/pan-domain-node: src/panda.ts:15-32](https://github.com/guardian/pan-domain-node/blob/9b4ab8878052cde8fbbac9d9d49ec2647a604202/src/panda.ts#L15-L32); [src/utils.ts:48-62](https://github.com/guardian/pan-domain-node/blob/9b4ab8878052cde8fbbac9d9d49ec2647a604202/src/utils.ts#L48-L62).
[^18]: [guardian/amigo: tests/e2e/support.ts:1-43](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/tests/e2e/support.ts#L1-L43); [tests/e2e/homepage-is-visible.spec.ts:5-14](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/tests/e2e/homepage-is-visible.spec.ts#L5-L14).
[^19]: [guardian/amigo: app/components/IdentityProviderWiring.scala:12-35 at #1912 base](https://github.com/guardian/amigo/blob/2aa92534df52d94690f0be0affba486b0e3faa58/app/components/IdentityProviderWiring.scala#L12-L35).
[^20]: [guardian/amigo: tests/e2e/auth/provider.mjs:103-169](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/tests/e2e/auth/provider.mjs#L103-L169); [tests/e2e/auth/provider.mjs:214-229](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/tests/e2e/auth/provider.mjs#L214-L229).
[^21]: [guardian/amigo: test/e2e/E2EComponents.scala:49-82](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/test/e2e/E2EComponents.scala#L49-L82); [test/e2e/E2EServer.scala:19-55](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/test/e2e/E2EServer.scala#L19-L55).
[^22]: [guardian/amigo: tests/e2e/auth/authentication.spec.ts:38-115](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/tests/e2e/auth/authentication.spec.ts#L38-L115); [tests/e2e/auth/fixtures.ts:110-144](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/tests/e2e/auth/fixtures.ts#L110-L144).
[^23]: [guardian/amigo: test/e2e/E2EApplicationLoader.scala:29-44](https://github.com/guardian/amigo/blob/a6048df73039dd12116dd8541b43518ed6968c10/test/e2e/E2EApplicationLoader.scala#L29-L44); [e2e/tests/support.ts:3-62](https://github.com/guardian/amigo/blob/a6048df73039dd12116dd8541b43518ed6968c10/e2e/tests/support.ts#L3-L62).
[^24]: [navikt/mock-oauth2-server: src/main/kotlin/no/nav/security/mock/oauth2/grant/AuthorizationCodeHandler.kt:38-108](https://github.com/navikt/mock-oauth2-server/blob/74793e90fbcb796ff9eeabde5fd6efcbb864f28d/src/main/kotlin/no/nav/security/mock/oauth2/grant/AuthorizationCodeHandler.kt#L38-L108); [src/main/kotlin/no/nav/security/mock/oauth2/token/OAuth2TokenProvider.kt:44-189](https://github.com/navikt/mock-oauth2-server/blob/74793e90fbcb796ff9eeabde5fd6efcbb864f28d/src/main/kotlin/no/nav/security/mock/oauth2/token/OAuth2TokenProvider.kt#L44-L189).
[^25]: [guardian/play-googleauth: play-v30/src/main/scala/com/gu/googleauth/model.scala:17-48](https://github.com/guardian/play-googleauth/blob/314210c2e77b2d5ccc6ef2c8a9c3150432eeb3b8/play-v30/src/main/scala/com/gu/googleauth/model.scala#L17-L48).
[^26]: [guardian/play-googleauth: play-v30/src/main/scala/com/gu/googleauth/auth.scala:131-148](https://github.com/guardian/play-googleauth/blob/314210c2e77b2d5ccc6ef2c8a9c3150432eeb3b8/play-v30/src/main/scala/com/gu/googleauth/auth.scala#L131-L148); [play-v30/src/main/scala/com/gu/googleauth/auth.scala:255-295](https://github.com/guardian/play-googleauth/blob/314210c2e77b2d5ccc6ef2c8a9c3150432eeb3b8/play-v30/src/main/scala/com/gu/googleauth/auth.scala#L255-L295).
[^27]: [guardian/play-googleauth: play-v30/src/main/scala/com/gu/googleauth/actions.scala:18-51](https://github.com/guardian/play-googleauth/blob/314210c2e77b2d5ccc6ef2c8a9c3150432eeb3b8/play-v30/src/main/scala/com/gu/googleauth/actions.scala#L18-L51); [guardian/amigo: tests/e2e/auth/provider.mjs:24-48](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/tests/e2e/auth/provider.mjs#L24-L48).
[^28]: [Google OpenID Connect: obtaining ID-token information and backend HTTPS trust](https://developers.google.com/identity/openid-connect/openid-connect#obtainuserinfo).
[^29]: [Google: verify Google ID tokens received by a backend](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).
[^30]: [guardian/interview-questions: client/src/components/exportToGoogleDoc.tsx:30-98](https://github.com/guardian/interview-questions/blob/1cfc3a1b06fe578b818ada2d27dc5b53cdd1e103/client/src/components/exportToGoogleDoc.tsx#L30-L98).
[^31]: [guardian/interview-questions: tests/e2e/interview-script.spec.ts:26-54](https://github.com/guardian/interview-questions/blob/1cfc3a1b06fe578b818ada2d27dc5b53cdd1e103/tests/e2e/interview-script.spec.ts#L26-L54).
[^32]: [guardian/workflow-frontend: e2e-tests/setup/panDomainKeys.ts:16-27](https://github.com/guardian/workflow-frontend/blob/b3d6cc96dbf7a99b48a417e50394625db3ad8e2a/e2e-tests/setup/panDomainKeys.ts#L16-L27); [e2e-tests/setup/stack/seedS3.ts:25-50](https://github.com/guardian/workflow-frontend/blob/b3d6cc96dbf7a99b48a417e50394625db3ad8e2a/e2e-tests/setup/stack/seedS3.ts#L25-L50); [app/controllers/PanDomainAuthActions.scala:15-38](https://github.com/guardian/workflow-frontend/blob/b3d6cc96dbf7a99b48a417e50394625db3ad8e2a/app/controllers/PanDomainAuthActions.scala#L15-L38).
[^33]: [guardian/flexible-restorer: app/auth/PanDomainAuthActions.scala:17-33](https://github.com/guardian/flexible-restorer/blob/338202a3721f360debe5b3d40eecd718cccb3ae8/app/auth/PanDomainAuthActions.scala#L17-L33); [e2e-tests/steps/authentication.steps.ts:34-66](https://github.com/guardian/flexible-restorer/blob/338202a3721f360debe5b3d40eecd718cccb3ae8/e2e-tests/steps/authentication.steps.ts#L34-L66).
[^34]: [guardian/pan-domain-authentication: pan-domain-auth-verification/src/main/scala/com/gu/pandomainauth/service/CookieUtils.scala:54-61, v22](https://github.com/guardian/pan-domain-authentication/blob/8b10dd00a47557386184f4dcabc3ab69bd4f8915/pan-domain-auth-verification/src/main/scala/com/gu/pandomainauth/service/CookieUtils.scala#L54-L61); [pan-domain-auth-verification/src/main/scala/com/gu/pandomainauth/PanDomain.scala:38-74](https://github.com/guardian/pan-domain-authentication/blob/8b10dd00a47557386184f4dcabc3ab69bd4f8915/pan-domain-auth-verification/src/main/scala/com/gu/pandomainauth/PanDomain.scala#L38-L74).
[^35]: [guardian/pan-domain-authentication: pan-domain-auth-play/src/main/scala/com/gu/pandomainauth/action/Actions.scala:242-290, v22](https://github.com/guardian/pan-domain-authentication/blob/8b10dd00a47557386184f4dcabc3ab69bd4f8915/pan-domain-auth-play/src/main/scala/com/gu/pandomainauth/action/Actions.scala#L242-L290); [same path:242-290, v13](https://github.com/guardian/pan-domain-authentication/blob/0937e08b0904db18c32f492be3fc2fd900c62d26/pan-domain-auth-play/src/main/scala/com/gu/pandomainauth/action/Actions.scala#L242-L290); [guardian/workflow-frontend: e2e-tests/fixtures/pan-domain-settings/local.dev-gutools.co.uk.settings:9-12](https://github.com/guardian/workflow-frontend/blob/b3d6cc96dbf7a99b48a417e50394625db3ad8e2a/e2e-tests/fixtures/pan-domain-settings/local.dev-gutools.co.uk.settings#L9-L12).
[^36]: [guardian/grid: e2e-tests/setup/provision.ts:128-154](https://github.com/guardian/grid/blob/0ace378425d3dd1e1f324729eedd48dce9c85f98/e2e-tests/setup/provision.ts#L128-L154); [dev/oidc-provider/make-provider.js:1-33](https://github.com/guardian/grid/blob/0ace378425d3dd1e1f324729eedd48dce9c85f98/dev/oidc-provider/make-provider.js#L1-L33).
[^37]: [guardian/amigo: cdk/lib/amigo.ts:342-383](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/cdk/lib/amigo.ts#L342-L383); [guardian/cognito-auth-lambdas: src/cognito-lambda/main.go:26-54](https://github.com/guardian/cognito-auth-lambdas/blob/669fb2bc67cb134a3e5fd87220e297c10bcebfdf/src/cognito-lambda/main.go#L26-L54); [src/gatekeeper-lambda/main.go:29-61](https://github.com/guardian/cognito-auth-lambdas/blob/669fb2bc67cb134a3e5fd87220e297c10bcebfdf/src/gatekeeper-lambda/main.go#L29-L61).
[^38]: [guardian/amigo: app/authentication/AlbPublicKeyProvider.scala:24-85](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/app/authentication/AlbPublicKeyProvider.scala#L24-L85); [test/authentication/AlbIdentityProviderSpec.scala:55-205](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/test/authentication/AlbIdentityProviderSpec.scala#L55-L205).
[^39]: [AWS ALB: user claims encoding and signature verification](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html#user-claims-encoding).
[^40]: [guardian/pan-domain-lambda: src/index.js:4-47](https://github.com/guardian/pan-domain-lambda/blob/363962f1584165b74cc4d879313d635da8f016c2/src/index.js#L4-L47).
[^41]: [Playwright: browserContext.storageState, including IndexedDB option](https://playwright.dev/docs/api/class-browsercontext#browser-context-storage-state).
[^42]: [Playwright: sessionStorage workaround](https://playwright.dev/docs/auth#session-storage); [browserContext.addInitScript](https://playwright.dev/docs/api/class-browsercontext#browser-context-add-init-script).
[^43]: [Playwright: page.route limitations](https://playwright.dev/docs/api/class-page#page-route); [browserContext.route](https://playwright.dev/docs/api/class-browsercontext#browser-context-route).
[^44]: [Playwright: browserContext.addCookies](https://playwright.dev/docs/api/class-browsercontext#browser-context-add-cookies); [route.continue forbidden header overrides](https://playwright.dev/docs/api/class-route#route-continue).
[^45]: [Playwright: how request interception works](https://playwright.dev/docs/network#how-request-interception-works).
[^46]: [Google OAuth web-server flow: backend code exchange](https://developers.google.com/identity/protocols/oauth2/web-server#exchange-authorization-code); [guardian/play-googleauth: play-v30/src/main/scala/com/gu/googleauth/groups.scala:32-56](https://github.com/guardian/play-googleauth/blob/314210c2e77b2d5ccc6ef2c8a9c3150432eeb3b8/play-v30/src/main/scala/com/gu/googleauth/groups.scala#L32-L56).
[^47]: [Playwright: API testing](https://playwright.dev/docs/api-testing#introduction); [APIRequestContext cookie sharing](https://playwright.dev/docs/api/class-apirequestcontext).
[^48]: [Playwright: missing network events and service workers](https://playwright.dev/docs/network#missing-network-events-and-service-workers); [service-worker routing and limitations](https://playwright.dev/docs/service-workers).
[^49]: [Playwright: route.abort](https://playwright.dev/docs/api/class-route#route-abort), [route.fetch](https://playwright.dev/docs/api/class-route#route-fetch), [route.continue](https://playwright.dev/docs/api/class-route#route-continue), and [redirect limitations](https://playwright.dev/docs/network#redirects).
[^50]: [guardian/amigo: app/views/layout.scala.html:13-16, #1912](https://github.com/guardian/amigo/blob/10c179959d65960e5ee683a970d4c3a6d13aed1f/app/views/layout.scala.html#L13-L16); [same path:52-55, #1914](https://github.com/guardian/amigo/blob/dc90a12c44ddc2bc6c81533e865e31311408b299/app/views/layout.scala.html#L52-L55); [same path:52-55, #1915](https://github.com/guardian/amigo/blob/a6048df73039dd12116dd8541b43518ed6968c10/app/views/layout.scala.html#L52-L55).
[^51]: [Google Account Help: additional verification can be required](https://support.google.com/accounts/answer/114129); [Google OAuth policies](https://developers.google.com/identity/protocols/oauth2/policies).
[^52]: [navikt/mock-oauth2-server: src/main/kotlin/no/nav/security/mock/oauth2/http/OAuth2HttpRequestHandler.kt:64-108](https://github.com/navikt/mock-oauth2-server/blob/74793e90fbcb796ff9eeabde5fd6efcbb864f28d/src/main/kotlin/no/nav/security/mock/oauth2/http/OAuth2HttpRequestHandler.kt#L64-L108); [panva/node-oidc-provider: README.md:14-39](https://github.com/panva/node-oidc-provider/blob/f72e14e0de04ce8f32973153e6bb6ea7bdec6722/README.md#L14-L39).
[^53]: [navikt/mock-oauth2-server: src/main/kotlin/no/nav/security/mock/oauth2/login/LoginRequestHandler.kt:26-35](https://github.com/navikt/mock-oauth2-server/blob/74793e90fbcb796ff9eeabde5fd6efcbb864f28d/src/main/kotlin/no/nav/security/mock/oauth2/login/LoginRequestHandler.kt#L26-L35); [src/main/kotlin/no/nav/security/mock/oauth2/extensions/NimbusExtensions.kt:44-53](https://github.com/navikt/mock-oauth2-server/blob/74793e90fbcb796ff9eeabde5fd6efcbb864f28d/src/main/kotlin/no/nav/security/mock/oauth2/extensions/NimbusExtensions.kt#L44-L53).
[^54]: [panva/node-oidc-provider: docs/README.md:833-846](https://github.com/panva/node-oidc-provider/blob/f72e14e0de04ce8f32973153e6bb6ea7bdec6722/docs/README.md#L833-L846); [docs/README.md:5088-5100](https://github.com/panva/node-oidc-provider/blob/f72e14e0de04ce8f32973153e6bb6ea7bdec6722/docs/README.md#L5088-L5100).
[^55]: [Google Cloud: OAuth audience and publishing status](https://support.google.com/cloud/answer/15549945).
[^56]: [Google OAuth policies: separate testing and production projects](https://developers.google.com/identity/protocols/oauth2/policies#use-separate-projects-for-testing-and-production).
[^57]: [guardian/cdk: src/patterns/ec2-app/base.ts:576-598, Lambda artifact reference](https://github.com/guardian/cdk/blob/820366c5ad463d1619d265016a02909d08804db5/src/patterns/ec2-app/base.ts#L576-L598).
[^58]: [guardian/play-googleauth: version.sbt:1](https://github.com/guardian/play-googleauth/blob/314210c2e77b2d5ccc6ef2c8a9c3150432eeb3b8/version.sbt#L1); [panva/node-oidc-provider: package.json:1-4](https://github.com/panva/node-oidc-provider/blob/f72e14e0de04ce8f32973153e6bb6ea7bdec6722/package.json#L1-L4).
