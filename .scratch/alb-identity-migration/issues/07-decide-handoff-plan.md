Type: grilling
Status: resolved
Blocked by: 05, 06, 08

## Question

What implementation sequence, focused tests, and acceptance criteria make the resolved identity, integration, and infrastructure decisions complete enough for an implementation handoff?

## Answer

Use four ordered changes: a Guardian CDK release exposing typed Google-auth resources; an Amigo infrastructure deployment enabling the Cognito group gate and runtime configuration; an Amigo application migration replacing legacy auth; and a dedicated cleanup after a seven-day soak.

Require focused Guardian CDK construct tests, Amigo template assertions, JWT and claim-translation unit tests, `AuthAction` request tests, and mode/configuration wiring tests. Promotion requires CODE configuration and member-access verification plus invalid-assertion and unprotected-route checks, followed by equivalent PROD smoke checks. Deployed CI end-to-end testing remains out of scope.

The complete implementation sequence, component contract, test matrix, rollout, rollback, cleanup, and acceptance criteria are in the [ALB identity migration specification](../spec.md).
