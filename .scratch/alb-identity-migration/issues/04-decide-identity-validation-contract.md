Type: grilling
Status: resolved
Blocked by: 01

## Question

What exact verified-header and claim-translation contract should Amigo enforce for the Guardian CDK Cognito-backed group gate, including required claims, `fullName` fallback order, rejection behaviour, and the expected ALB identity?

## Answer

Amigo trusts only `x-amzn-oidc-data`; it ignores the unsigned identity and access-token headers. Validation requires ES256, a valid signature from the regional ALB public key identified by `kid`, an unexpired token with at most 60 seconds of clock skew, and an exact match for the expected ALB signer ARN. This is the target-validation contract documented by AWS; Cognito issuer and client headers remain covered by the ALB signature but are not independently pinned.

The validated payload must contain a non-empty `sub`, a non-empty `email`, and `email_verified: true`. Resolve `fullName` from non-empty `name`, then non-empty `given_name` and `family_name` joined with whitespace, then email. The Cognito group gate is the sole authorisation owner; Amigo performs no additional domain or group check.

Cache ALB public keys by `kid`. On an unknown `kid`, refresh once before rejecting the request. Missing headers, malformed tokens, failed key retrieval, failed validation, or invalid required claims return `401 Unauthorized` with no login redirect or legacy fallback.

Authentication failures may log a reason category and request-correlation data, but must never log the JWT, access token, or decoded claims.
