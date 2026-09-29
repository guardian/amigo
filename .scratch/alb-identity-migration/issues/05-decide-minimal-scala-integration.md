Type: grilling
Status: resolved
Blocked by: 01, 02, 04

## Question

What is the smallest Scala integration that preserves the current controller `authAction` call sites and `request.user` behaviour while replacing `play-googleauth` with an app-owned ALB-backed action and a production-safe local-development identity source?

## Answer

Replace `play-googleauth` with an app-owned `UserIdentity(email, fullName)` and generic `AuthAction[A]` built on Play's `AuthenticatedRequest`. Preserve the existing controller constructor parameters, normal and explicit-parser invocation forms, `authAction` value name, and `request.user` members; controller changes should be limited to imports.

Keep identity request-scoped and stateless. An injectable identity-provider interface returns either an authenticated identity or a typed authentication failure. `AlbIdentityProvider` validates and translates `x-amzn-oidc-data` on every protected request; `AuthAction` only refines the Play request and converts failures to `401 Unauthorized`. Do not create a Play identity session.

Select a fixed configured development identity only when Play is running in `Mode.Dev`. All other modes use the ALB provider, with no runtime switch or production bypass. Require explicit expected signer ARN, Cognito issuer, and client ID configuration outside development and fail application startup when any is absent.

Use Nimbus JOSE JWT for ES256 and JWT handling. Keep ALB public-key retrieval behind a small provider using Play WS and a bounded `kid` cache, so token validation and key-fetch behaviour can be tested independently.

The injectable provider supports unit and in-process application tests. A future CI end-to-end workflow must not activate the development provider in a deployed production-mode application; its authentication path is a separate decision.
