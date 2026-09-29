# ALB identity migration

## Goal

Remove Amigo's direct Google OAuth and Directory API calls. Authenticate and authorise users at the ALB through Guardian CDK's Cognito-backed Google group gate, then populate the existing `request.user.email` and `request.user.fullName` contract from a verified ALB identity assertion.

## Non-goals

- Change the three authorised Google groups or introduce application roles.
- Protect routes that are not currently wrapped by `AuthAction`; `/healthcheck` and static assets remain unprotected by the application action.
- Add target-side TLS, authentication-specific monitoring, or CI/deployed end-to-end authentication.
- Require a non-member end-to-end test.

## Prerequisite: Guardian CDK

Add a typed, stable result to `GuEc2App`/`GuPlayApp` for enabled Google authentication, for example `googleAuthResources`, exposing the generated Cognito `UserPool` and `UserPoolClient`. Keep it absent when Google authentication is disabled.

Do not make Amigo search the construct tree by child ID and do not copy the Cognito/gatekeeper implementation into Amigo. Add Guardian CDK construct tests, release the change, and update Amigo from `@guardian/cdk` 63.6.2 to that release.

## Infrastructure

Configure `GuPlayApp.googleAuth` with:

- the three canonical `@guardian.co.uk` email addresses corresponding to the groups currently loaded from `auth.google.departmentGroupId`, `auth.google.dataScientistsGroupId`, and `auth.google.multimediaGroupId`, hardcoded in `cdk/lib/amigo.ts`;
- `cognitoAuthStage: "PROD"`;
- `sessionTimeoutInMinutes: 15`;
- the default stage-specific `/:stage/deploy/amigo/google-auth-credentials` JSON secret containing `clientId` and `clientSecret`.

Before implementation, read the existing non-secret group values once with an authorised deployment profile. If the current values are immutable Google group IDs, resolve each one to its canonical group email. Preserve the same three authorised groups and confirm whether CODE and PROD currently differ rather than silently changing either set. Do not use SSM lookups or unresolved CloudFormation tokens for `allowedGroups`: Guardian CDK validates the `@guardian.co.uk` suffix at synth time, and the authorisation policy should remain visible in review.

The Guardian CDK construct owns `openid email profile`, Cognito federation, group-gate Lambda triggers, the ALB Cognito action, and IdP egress. Remove Amigo's hand-written `authenticateOidc` action, `ClientId` CloudFormation parameter, `clientSecret` reference, and separately created IdP egress security group.

Preserve private-subnet placement and the security-group rule that permits port 9000 only from the ALB security group. Keep ALB-to-target HTTP for this migration.

### Runtime values

The application requires these values outside Play `Mode.Dev`:

- expected signer ARN: read the existing `/infosec/waf/services/<stage>/amigo-alb-arn` SSM parameter;
- expected issuer: `https://cognito-idp.eu-west-1.amazonaws.com/<user-pool-id>`;
- expected client ID: the generated Cognito user-pool client ID.

Use the exposed Guardian CDK resources to publish the issuer as `/<stage>/deploy/amigo/auth.alb.issuer` and the client ID as `/<stage>/deploy/amigo/auth.alb.clientId`. Fail application startup outside development when any expected value is absent.

Before deployment, create the Guardian CDK JSON secret without deleting the legacy credentials and add Cognito's generated Google callback URL to the existing Google OAuth client.

## Application design

Create an app-owned authentication package containing:

- `UserIdentity(email: String, fullName: String)`;
- a typed authentication-failure model;
- an injectable identity-provider interface;
- `AlbIdentityProvider`, which validates and translates `x-amzn-oidc-data`;
- a bounded, caching ALB public-key provider using Play WS;
- a fixed development provider selected only in Play `Mode.Dev`;
- generic `AuthAction[A]`, implemented with Play's `AuthenticatedRequest`.

`AuthAction` only refines requests and converts every authentication failure to `401 Unauthorized`. Keep the existing controller constructor shape, `authAction` name, normal and explicit-body-parser invocation forms, and `request.user.email/fullName` access. Controller changes should be limited to replacing imports.

Do not create an identity session. Validate the ALB assertion on every protected request. Unit and in-process tests inject deterministic providers directly; no runtime test bypass is permitted.

Add Nimbus JOSE JWT for ES256/JWT handling. Remove `play-googleauth` and any Google service-account dependencies that become unused.

## Validation contract

Trust only `x-amzn-oidc-data`. Ignore `x-amzn-oidc-identity` and `x-amzn-oidc-accesstoken`.

For every protected request:

1. Parse the JWT without using unverified claims for identity or authorisation.
2. Require `alg` to be ES256 and constrain `kid` to the expected ALB key identifier format.
3. Fetch the key only from `https://public-keys.auth.elb.eu-west-1.amazonaws.com/<kid>`.
4. Cache keys by `kid` in a fixed-size cache. For an unknown `kid`, refresh once before failing closed.
5. Verify the signature and expiration, allowing at most 60 seconds of clock skew.
6. Require exact matches for the expected ALB `signer`, Cognito `iss`, and generated `client` header values.
7. Require a non-empty `sub`, a non-empty `email`, and boolean `email_verified: true` in the payload.
8. Resolve `fullName` from non-empty `name`, then the non-empty parts of `given_name` and `family_name` joined with one space, then email.

The Cognito group gate is the sole authorisation owner. Do not repeat domain or group checks in Amigo.

Missing headers, malformed tokens, key-fetch failures, validation failures, and invalid required claims return `401` without redirect or fallback. Logs may contain a failure category and request-correlation data, but never tokens or decoded claims.

## Legacy application removal

Remove:

- `controllers.Login` and the `/login` and `/oauth2callback` routes;
- `GoogleAuthConfig`, `GoogleGroupChecker`, `LoginSupport`, and legacy `AuthAction` wiring;
- Google group and service-account loading from `AppComponents`;
- the `play-googleauth` dependency;
- now-unused Google authentication imports and configuration.

Retain the service-account object, its instance download, development setup, legacy OAuth credentials, old configuration values, and the previous application artefact during the rollback window even though the new application no longer consumes them.

## Focused tests

### Guardian CDK

- Google authentication exposes typed user-pool and client resources when enabled and no result when disabled.
- Existing group-gate resources, triggers, scopes, and listener action remain unchanged.

### Amigo CDK

- Focused template assertions cover the Cognito action, the three resolved allowed-group addresses, PROD gatekeeper stage, 15-minute session, generated runtime parameters, and ALB-to-target security-group rule.
- The template no longer contains Amigo's direct OIDC listener action or extra IdP egress group.

### Scala

- Claim translation covers every `fullName` fallback and rejects missing or unverified email and missing `sub`.
- JWT tests use ephemeral EC keys and cover valid tokens, wrong signature or algorithm, wrong signer/issuer/client, expiration and skew boundaries, malformed input, key-fetch failure, and unknown-`kid` refresh/cache behaviour.
- `AuthAction` returns `401` for each failure class and preserves authenticated requests for both default and explicit body parsers.
- Wiring tests prove `Mode.Dev` selects the fixed provider, other modes select ALB validation, and missing production values fail startup.
- Existing protected controllers continue to compile and tests confirm health/assets remain outside `AuthAction`.

Run Scala formatting, compilation, and tests, plus CDK formatting, lint, build, tests, and synth. Update the CDK snapshot only for intended resource changes.

## Delivery sequence

1. **Guardian CDK release:** expose typed Google-auth resources, test, and release.
2. **Amigo gate and configuration:** bump Guardian CDK; create the JSON credential secret and Google callback; enable `googleAuth`; publish Cognito runtime values; deploy to CODE.
3. **Amigo application migration:** add ALB identity validation and app-owned `AuthAction`; remove active legacy auth code and dependency; deploy to CODE.
4. **CODE acceptance:** confirm the Cognito action and allowed-group configuration, successful protected use by a group member, expected runtime values, `401` for invalid app-level assertions, and unchanged health/assets.
5. **PROD rollout:** repeat infrastructure then application deployment and perform the same smoke checks.
6. **Seven-day soak:** retain legacy credentials, service-account material, setup, configuration, and old artefact. Roll back the application first and infrastructure second if required.
7. **Cleanup release:** remove retained legacy secrets and parameters, service-account object/download, development setup, and obsolete configuration after the soak.

Monitoring remains unchanged: retain existing application and ALB access logging and add no authentication-specific alarms or dashboards.

## Acceptance criteria

- Protected requests receive the same `email` and `fullName` controller contract from a verified, deployment-bound ALB assertion.
- Invalid identity fails closed with `401`; no protected request redirects to or falls back to legacy login.
- Group membership is enforced by the Guardian CDK Cognito gate using the existing three groups.
- Amigo makes no Google OAuth, UserInfo, or Directory API call and loads no service-account credentials.
- `play-googleauth`, `Login`, legacy OAuth routes, and active legacy auth configuration are absent from the migrated application.
- Production cannot activate the development or test identity providers.
- Health and static assets retain their current authentication behaviour.
- All focused Scala and CDK checks pass, CODE acceptance succeeds, PROD smoke checks succeed, and cleanup waits for the completed seven-day soak.
