Type: grilling
Status: resolved
Blocked by: 05, 06

## Question

What authentication path should a possible future CI end-to-end workflow use to exercise protected Amigo routes without introducing a production bypass, and which enabling work belongs in this migration specification versus being explicitly deferred?

## Answer

Ruled out of scope. This migration provides no CI end-to-end authentication support and does not test the deployed ALB/Cognito gate. In-process tests may inject a deterministic identity provider directly, but the specification requires no CI workflow, credentials, users, groups, listener rules, endpoints, or future machine-authentication design.
