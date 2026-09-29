# `play-googleauth` compatibility boundary

## Findings

- Amigo's protected controllers depend on `AuthAction[AnyContent]` as an action builder. They use its standard invocation form, its explicit body-parser form, and only `request.user.email` and `request.user.fullName` from the authenticated request.
- `play-googleauth` defines `AuthAction` as an `ActionBuilder` and `ActionRefiner`, producing a Play `Security.AuthenticatedRequest` whose user is the library's `UserIdentity`.
- The library's `UserIdentity` contains more state than Amigo consumes: `sub`, `email`, first and last names, expiry, and avatar URL. Its `fullName` is derived from first and last names.
- The legacy login path serialises `UserIdentity` into the Play session under the library's identity key. `AuthAction` reads and validates that session identity and redirects failures to the configured login route. Amigo's controllers do not directly depend on those session details.
- Subclassing the library action would retain the unwanted dependency and configuration contract. The smallest dependency-free replacement is an app-owned `UserIdentity(email, fullName)` and app-owned action builder/refiner producing a Play authenticated request.
- Preserving the `authAction` value name, invocation forms, and `request.user` members limits controller changes to replacing six imports. Construction changes remain concentrated in `AppComponents`.
- Removing the dependency also removes `LoginSupport`, `GoogleGroupChecker`, `GoogleAuthConfig`, OAuth routes, service-account setup, and the Google-group configuration path.

## Sources

- [Guardian `play-googleauth` Play 3 action source](https://github.com/guardian/play-googleauth/blob/main/play-v30/src/main/scala/com/gu/googleauth/actions.scala).
- [Amigo login controller](../../../app/controllers/Login.scala).
- [Amigo component wiring](../../../app/components/AppComponents.scala).
- [Amigo routes](../../../conf/routes).
- [Amigo dependency declaration](../../../build.sbt).
- Current controller usages in `RootController`, `BaseImageController`, `HousekeepingController`, `RoleController`, `RecipeController`, and `BakeController`.
