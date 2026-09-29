Type: research
Status: resolved

## Question

What `play-googleauth` types, request shape, and session semantics do Amigo's current `AuthAction` call sites depend on, and what is the smallest app-owned replacement that preserves those call sites while allowing the dependency, OAuth callback, and Google API calls to be removed?

## Answer

Controllers require only an action builder that supports the existing invocation forms and exposes `request.user.email` and `request.user.fullName`. An app-owned identity plus Play authenticated action can preserve that surface; controller changes are limited to imports while wiring remains concentrated in `AppComponents`. See [`play-googleauth` compatibility boundary](../research/play-googleauth-compatibility-boundary.md).
