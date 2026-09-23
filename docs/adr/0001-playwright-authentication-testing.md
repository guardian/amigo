---
status: proposed
---

# Layer authentication testing for Playwright

We will separate application browser testing from authentication integration and deployed edge verification. Most Playwright tests will use a framework-neutral authentication boundary and a test-only application composition, while a smaller suite will exercise a complete local OpenID Connect (OIDC) flow. This keeps routine tests hermetic and reliable without losing targeted coverage of security-sensitive integrations.

## Context

Amigo currently has two authentication layers:

- the Application Load Balancer (ALB) authenticates users against Google using OIDC;
- the Play application uses `play-googleauth` and authorises users by Google Group membership.

The application also initialises AWS and other external integrations during startup. A browser test therefore cannot currently start a representative application without AWS credentials, Google credentials, and access to live services.

The primary goal is to test application behaviour after authentication. Routine tests must run locally and in CI without real Google identities, shared credentials, AWS access, or external network calls. Edge rejection is outside the scope of the main Playwright suite.

## Decision

### Application browser tests

Application code will depend on a framework-neutral authentication contract. An authenticated identity contains only:

- `subject`;
- `email`;
- authentication source.

Framework-specific middleware, such as a Play `ActionBuilder`, will adapt the production authentication mechanism to this contract.

The test application composition will provide three standard states:

- anonymous;
- authorised;
- unauthorised.

A test-only login endpoint or equivalent test authenticator may establish these states. It must be constructed only in the test application and must not be activatable in a production artefact through configuration.

Playwright will generate fresh authenticated `storageState` through a setup project for each test run. Authentication state must be written to Playwright's cleaned output directory and must not be committed. External application dependencies unrelated to the behaviour under test will use in-memory adapters.

These tests will be described as **application E2E** or **browser acceptance** tests because they deliberately do not exercise the production identity provider.

### Authentication integration tests

A smaller suite will exercise a complete local OIDC authorisation-code flow, including:

- authorisation, token, user-info, discovery, and JWKS endpoints;
- configurable subject, email, and group claims;
- authorised and unauthorised identities;
- expired tokens and identity-provider failures;
- callback handling, session creation, and redirects.

The shared standard will define protocol requirements and fixtures rather than mandate one implementation. `no.nav.security:mock-oauth2-server` is a suitable JVM reference implementation and can also run as a standalone container.

### ALB identity integration

Applications that consume identity from an ALB must validate the signature, expiry, and expected ALB ARN in the `x-amzn-oidc-data` header. Network restrictions on application targets are defence in depth and do not replace this validation.

Application E2E tests will replace the identity adapter. Separate contract tests will generate representative ES256-signed headers and exercise the production ALB adapter. A local proxy will not be required.

### Cognito feasibility investigation

Moving group authorisation out of the application and into Cognito remains a conditional architecture option, not a Playwright technique. Cognito does not automatically verify Google Workspace group membership.

The minimal design to investigate is:

1. Google federates through a Cognito user pool.
2. A pre-token Lambda queries Google Groups.
3. The Lambda fails closed when the user is not a member or the lookup fails.
4. The ALB uses `authenticate-cognito`.
5. The application validates ALB-signed identity claims but performs no group check.

This moves, rather than removes, the Google service-account dependency. Before adoption, the design would require explicit review of delegated credentials, latency, Google API quotas, caching, membership-revocation freshness, failure handling, and operational ownership.

## Testing layers

Approaches are ranked in this order:

1. A test authenticator or test-only login endpoint for application behaviour.
2. A local OIDC provider for authentication integration.
3. ALB identity-adapter contract tests where the application consumes edge identity.
4. A minimal deployed smoke test for ALB or Cognito wiring, where suitable managed test identities exist.

Routine CI will not depend on real Google login. Without dedicated managed test identities, deployed verification may remain manual or operational.

## Rejected approaches

We will not use the following as the basis of routine browser testing:

- direct fabrication of Play session cookies;
- committed or long-lived `storageState` files;
- browser network interception for server-side authentication;
- globally disabling authentication;
- trusting unsigned identity headers;
- a local proxy used only to imitate ALB;
- automated real-Google UI login in routine CI.

## Consequences

The application must support a lightweight test composition and explicit adapters for effectful dependencies. This requires more structure than reusing live credentials, but gives deterministic local and CI execution, clearer security boundaries, and portable fixtures across Guardian applications.

The approach intentionally provides different assurance at each layer. Application E2E tests prove browser-visible application behaviour, local OIDC tests prove login integration, contract tests prove edge identity parsing, and deployed checks prove infrastructure wiring.
