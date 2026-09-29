# ALB OIDC header contract

## Findings

- After authenticating the user, ALB forwards three headers: signed `x-amzn-oidc-data`, unsigned plaintext `x-amzn-oidc-identity`, and unsigned plaintext `x-amzn-oidc-accesstoken`. Only `x-amzn-oidc-data` is suitable as Amigo's identity source.
- `x-amzn-oidc-data` is an ALB-created JWT, not Google's ID token. ALB exchanges the authorisation code, calls Google's UserInfo endpoint with the access token, and signs the returned claims using ES256.
- The JWT header contains `alg`, `kid`, `signer`, `iss`, `client`, and `exp`. The payload contains claims returned by UserInfo, including `sub` and any available requested profile claims.
- Amigo must verify the ES256 signature using the regional ALB public-key endpoint derived from `kid`, and require the expected ALB ARN in `signer` before trusting any claim. The specification should also pin the expected issuer, OAuth client identifier, algorithm, and unexpired header time rather than accepting structurally valid tokens from another listener configuration.
- Targets should accept traffic only from the ALB security group. HTTPS between ALB and target is required if claims must be encrypted in transit.
- Google documents that `email` is returned when the `email` scope is requested. Profile claims such as `name`, `given_name`, and `family_name` may still be absent, supporting the agreed `fullName` fallback.
- Google's `hd` request parameter only optimises account selection and must not be used as access control. The current CDK configuration therefore proves authentication through Google, not domain or group authorisation.
- If the combined user claims and access token exceed 11 KB, ALB returns HTTP 500 and increments `ELBAuthUserClaimsSizeExceeded`. The ALB client login flow has a fixed 15-minute timeout and returns HTTP 401 when exceeded.

## Sources

- [AWS: Authenticate users using an Application Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/listener-authenticate-users.html), especially “Authentication flow” and “User claims encoding and signature verification”.
- [Google: OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect), especially authentication request parameters, claims, and UserInfo behaviour.
- [Amigo ALB OIDC listener configuration](../../../cdk/lib/amigo.ts).
