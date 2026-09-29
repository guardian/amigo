## Destination

An implementation-ready specification for removing Amigo's direct identity-provider authentication and authorisation calls, populating its existing user claims from verified ALB OIDC headers, and preserving the current protected surface with minimal application changes.

## Notes

- This effort plans the change; implementing it is out of scope.
- Keep the existing controller-level `authAction` call sites and request-user contract where practical, but remove the `play-googleauth` dependency to prove the legacy path is unused.
- Protect exactly the actions currently wrapped by `AuthAction`; `/healthcheck` and static assets remain outside application authentication.
- Missing, malformed, or unverifiable ALB identity returns `401 Unauthorized` with no legacy login fallback.
- Email is mandatory. Resolve `fullName` from `name`, then `given_name` plus `family_name`, then email.
- Local development uses an explicitly configured non-production identity provider that cannot activate in `PROD`.
- Move the existing allowed groups into `GuPlayApp.googleAuth.allowedGroups`, following Janus's Cognito-backed ALB gate; the resulting Amigo CDK configuration is the required evidence. An end-to-end non-member test is not required by this effort.
- Request `openid email profile`, subject to research confirming the resulting Google and ALB claims.
- Consult the `research`, `grilling`, `domain-modeling`, and `direct-style-scala` skills as appropriate.

## Decisions so far

- [ALB OIDC header contract](issues/01-alb-oidc-header-contract.md): trust only the verified ALB-signed claims JWT and pin it to the expected listener identity.
- [`play-googleauth` compatibility boundary](issues/02-play-googleauth-compatibility-boundary.md): an app-owned authenticated action can preserve controller behaviour with only import changes.
- [Locate the external group gate](issues/03-locate-external-group-gate.md): Guardian CDK's Cognito-backed `googleAuth.allowedGroups` option provides the gate, with Janus as the configuration reference.

## Not yet specified

## Out of scope

- Implementing the migration described by the specification.
- Changing which Google groups are authorised or introducing application roles.
- Expanding authentication to routes that are not currently wrapped by `AuthAction`.
- Requiring an end-to-end non-member test as evidence of the external group gate.
