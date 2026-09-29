Type: grilling
Status: resolved
Blocked by: 01, 03, 04

## Question

What CDK, configuration, network-boundary, observability, deployment-order, rollback, and legacy-cleanup requirements must the specification include so verified ALB identity is the only production path and the Guardian CDK group gate remains a proven prerequisite?

## Answer

Configure `GuPlayApp.googleAuth` with the existing three allowed groups, `cognitoAuthStage: "PROD"`, its built-in `openid email profile` scopes, and a 15-minute session timeout. Hardcode the three canonical `@guardian.co.uk` group email addresses in Amigo CDK; do not load `allowedGroups` from SSM or pass unresolved CloudFormation tokens. Remove Amigo's hand-written direct Google `authenticateOidc` listener action and its separately added IdP egress security group because the Guardian CDK option owns both authentication and IdP egress.

Preserve the strict runtime validation contract without relying on construct child IDs or copying Guardian CDK's Cognito implementation. First enhance Guardian CDK to expose the generated user pool and user-pool client as stable properties, release it, and bump Amigo. Reuse the existing `/infosec/waf/services/<stage>/amigo-alb-arn` parameter for the expected signer ARN. Publish the newly generated Cognito issuer and user-pool client ID under Amigo's stage-specific runtime configuration path; the current Google OAuth `ClientId` parameter is not the Cognito client ID and cannot satisfy this check.

Keep ALB-to-instance traffic on HTTP for this migration. Preserve the construct's private-subnet placement and security-group rule that restricts port 9000 ingress to the ALB security group. Target-side TLS is separate hardening outside this effort.

Create the Guardian CDK JSON Secrets Manager value containing the Google OAuth client ID and secret, and add the generated Cognito callback URL to the Google client. Do not delete or overwrite the legacy client ID, client secret, service-account material, or configuration before rollback has expired.

Roll out in phases. In CODE, deploy the Guardian CDK enhancement and Cognito gate/configuration first, verify group-gated access and emitted claims, then deploy the application migration. Repeat that sequence in PROD. Retain the legacy application artefact, OAuth credentials, service-account object, and configuration for a seven-day soak. Roll back the application first and the infrastructure second. After the soak, remove the legacy secrets and parameters, the service-account download and development setup, `Login`, OAuth routes, Google-auth configuration, and obsolete dependencies.

Leave monitoring as it is. Do not add authentication-specific alarms or dashboards in this migration; retain existing application and ALB access logging, without tokens or decoded claims.
