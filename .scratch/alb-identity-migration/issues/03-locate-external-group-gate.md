Type: research
Status: resolved

## Question

Where is the asserted Google group-membership gate for Amigo's ALB authentication configured, how is it associated with this service, and what stable configuration reference can the migration specification use as its prerequisite evidence?

## Answer

The installed Guardian CDK supports a Cognito-backed ALB group gate through `GuPlayApp.googleAuth.allowedGroups`, with Janus providing a pinned configuration example. Amigo does not use it yet: the migration must replace the hand-written direct Google OIDC action with this construct option and carry across the current allowed groups. The resulting Amigo CDK configuration is the prerequisite reference. See [External group-membership gate](../research/external-group-gate.md).
