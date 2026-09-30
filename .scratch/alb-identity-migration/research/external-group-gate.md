# External group-membership gate

## Findings

Amigo does not use an external group gate today, but its installed Guardian CDK version provides one through `GuPlayApp`'s `googleAuth` configuration. Janus is a stable configuration example: it enables `googleAuth` and supplies an `allowedGroups` list at the application-load-balancer boundary.

Guardian CDK implements this option using a Cognito user pool federated with Google. Pre-sign-up and pre-authentication Lambda triggers receive `ALLOWED_GROUPS` and invoke the shared `deploy-PROD-gatekeeper-lambda`; only successful users reach the ALB target action. The construct requests `openid`, `email`, and `profile`, maps Google's email and name attributes into Cognito, and installs an `AuthenticateCognitoAction` ahead of the target group.

The group check is therefore available as infrastructure owned by this repository once Amigo replaces its hand-written direct Google `authenticateOidc` action with `GuPlayApp`'s `googleAuth` option and carries across its three allowed groups. The resulting configuration in `cdk/lib/amigo.ts`, following the pinned Janus example, is the required configuration reference.

This changes the expected identity-provider contract from direct Google OIDC to the Cognito-backed ALB flow. The application validates the assertion against the expected ALB signer while Cognito owns the upstream identity and group checks.

## Sources

- [Amigo ALB OIDC listener configuration](../../../cdk/lib/amigo.ts).
- [Amigo group-membership check](../../../app/controllers/Login.scala).
- [Amigo configured allowed groups](../../../app/components/AppComponents.scala).
- [Janus `googleAuth.allowedGroups` configuration](https://github.com/guardian/janus/blob/086baf54777bf03a61b5b038977300253632a322/cdk/lib/janus.ts#L94-L112).
- [Guardian CDK v63.6.2 `googleAuth` contract and implementation](https://github.com/guardian/cdk/blob/v63.6.2/src/patterns/ec2-app/base.ts).
- [Google: OpenID Connect `hd` parameter](https://developers.google.com/identity/openid-connect/openid-connect#hd-param).
