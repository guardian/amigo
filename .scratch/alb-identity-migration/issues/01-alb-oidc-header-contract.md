Type: research
Status: resolved

## Question

For the AWS ALB `authenticate-oidc` action configured with Google's OIDC endpoints and `openid email profile`, which headers and claims reach Amigo, and what exact signature, signer, issuer, audience/client, expiry, and anti-spoofing checks must the target application perform before trusting them?

## Answer

Use only the ALB-signed ES256 JWT in `x-amzn-oidc-data`. Verify its signature with the regional ALB public key, require the expected signer ARN, algorithm, and expiry before translating UserInfo claims, and restrict target ingress to the ALB. See [ALB OIDC header contract](../research/alb-oidc-header-contract.md).
