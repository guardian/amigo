# AMIgo significant-functionality test plan

## Purpose and evidence

AMIgo manages base images and Ansible roles, combines them into recipes, launches Packer bakes, reports AMI/package/usage data, requests encrypted copies, and retires unused infrastructure. The principal users are authorised engineers maintaining recipes and operating bakes; unauthenticated or unauthorised users must not access protected data or actions. There is no separate administrator role in the inspected controllers.

This plan is based on all application routes, controllers, Twirl views, browser JavaScript, and the relevant data, scheduling, Packer, notification and housekeeping implementations inspected on 21 September 2026. Browser setup succeeded, but navigation to http://localhost:9000 failed with ERR_CONNECTION_REFUSED. No live application workflows were exercised. AppLoader loads AWS configuration; AppComponents connects to AWS/Prism and registers housekeeping jobs, so launching the application blindly is unsafe. Source-derived expectations below require confirmation on an isolated instance; they are not passing test results.

## Execution contract and isolation

Every scenario starts with a fresh browser context and an empty, isolated application dataset, then explicitly creates only its listed fixtures. An existing authorised storage-state file may initialise a new context, except in authentication tests. Never depend on a previous scenario or its data. Use a unique e2e-<run>-<case> prefix, create fixtures through a controlled server-side fixture harness or the UI, assert stored state after mutations, and clean up only resources owned by that case in finally/teardown. A cleanup failure is a failed test, not permission to delete a wider set of resources. Boundary input rows and usage variants are separate parameterised cases with separate setup and cleanup.

Required harness, NOT supplied by the Playwright config: isolated DynamoDB tables; controlled S3 package objects; deterministic Prism accounts, instances, launch configurations, launch templates and copied images; a Packer/process double; captured SNS and email notifications; a controllable server clock and Quartz scheduler; controllable Google OAuth/group responses. No such reset API or dependency-injection test server was found in the existing app. Build these fixtures before automating mutation scenarios, or use an explicitly provisioned disposable account with an agreed cost/cleanup policy. Playwright page.route only mocks browser requests: it cannot replace server-side AWS, Prism, Google or Packer calls.

Use real server-rendered application pages for browser tests. Do not replace application HTML with static fixtures and call that end-to-end coverage. Keep scheduled bakes and housekeeping controlled independently: disabling amigo.scheduledBakes.enabled does not disable housekeeping. Real Google login and real image-copy/deletion checks are controlled integration checks, never unattended actions against production. Credentials, storage state, traces and reports remain local and uncommitted.

The configuration runs Chromium with full parallelism and no retries. By default Playwright starts script/server, waits for /healthcheck and stops the managed process afterwards; outside CI it may reuse an existing local server without stopping it. Setting PLAYWRIGHT_BASE_URL disables local process management and targets an externally managed isolated instance. Startup requires the deployTools AWS profile and isolated service configuration; managing the process does not isolate its data or disable housekeeping. Parallel mutation tests require independent fixtures and coordination of shared background jobs. The root seed.spec.ts is a read-only authenticated-home planner seed outside normal discovery; tests/e2e/healthcheck.spec.ts is the only normally discovered implemented test. All other file paths below are proposed destinations, not existing automated tests. Backend scenarios belong in Scala tests even though the plan tool records the common planner seed. The existing Scala suite is the starting point, not something to duplicate in the browser.

## Priorities and pass criteria

P0: unauthorised writes, accidental infrastructure creation/deletion, incorrect usage protection, lost bake identity/status, and executable untrusted output. P1: creation/editing/cloning, diagnostics, schedules, encrypted copies, API contracts and recoverable failures. Each scenario passes only when every stated expected result and persistence/non-mutation assertion holds. Any missing assertion, unexpected 5xx, false success, cross-fixture mutation, extra process/message, or leaked resource is a failure. A missing harness is BLOCKED, not skipped-as-passed. Requirements marked safety contract are desired protections to test and may currently fail; the implementation observations below must not be treated as approved behaviour.

Prefer role/label locators scoped to the relevant form, panel or row. Use the explicitly inspected role checkbox/input IDs where a custom-variable input has no accessible label. Obtain genuine CSRF tokens for positive POST tests. Use request contexts and disabled redirects to inspect negative HTTP contracts. Poll fixture state with a bounded timeout rather than sleeping for real bake/scheduler intervals. Freeze the server clock for EOL, retention and timeout boundaries; Playwright's browser clock does not control Scala time.

## Shared fixture vocabulary

Base A: ubuntu, a valid test AMI ID, support ending more than three months after the fixed clock, one builtin role, ordinary builder. Base B: a different source AMI and distribution, different builtin role, XLarge enabled. Recipe R: Base A, optional description/disk/schedule, two requested encrypted-copy accounts where needed. Role fixtures include a documented role, a role without README, and a parent -> child -> leaf dependency chain; use actual installed role IDs for browser cases and synthetic roles only in a controlled role catalogue. Bake fixtures include Running, Complete, Failed, TimedOut and DeletionScheduled, with immutable build identifiers and known log sequences. Prism variants include direct/source AMI usage and copied-AMI usage by each of instance, launch configuration and launch template, including launch-template-only and old-build-only usage. S3 package fixtures contain known unchanged, removed and added package/version lines. Fake account numbers and AMIs must never be sent to live AWS.

## Significant source observations to target, not silently fix

- Housekeeping view posts orphaned-bake, while HousekeepingController reads orphaned-bakes. The view also lacks the explicit CSRF field used by normal mutation forms. A redirect alone cannot prove deletion was scheduled.
- The base-image clone form also lacks an explicit CSRF field. Test actual middleware behaviour rather than disabling CSRF to make it pass.
- RecipeController.createRecipe renders newBaseImage on duplicate recipe ID. Verify recoverable recipe-specific error feedback and preservation of entered fields.
- RecipeController.cloneRecipe copies bakeSchedule but never calls bakeScheduler.reschedule. A copied schedule displayed in the UI is not evidence that a job exists.
- showRecipe's summary checks instances and launch configurations, but not launch templates; RecipeUsage.hasUsage and deletion checks do include templates.
- Bake deletion uses a disabled attribute on an anchor, which is not a security boundary. Direct POSTs and a newly introduced usage after confirmation must still be checked.
- Form validation bounds IDs and text but does not establish AMI existence, positive disk size, or exactly 12-digit account numbers. Distinguish current parser limits from desired domain validation; report gaps instead of inventing passing assertions.
- PackerRunner starts the process before applying the limit to monitor threads. Test actual child-process concurrency, not the number of running monitor threads.
- Bake log text and package diff fragments reach raw HTML rendering. Include non-executing-in-production injection regression fixtures.

## Coverage map and deliberate exclusions

| Surface | Coverage |
| --- | --- |
| /, /login, /oauth2callback, /healthcheck | Access/session/CSRF scenarios and implemented seed/healthcheck smoke |
| /base-images and all create/edit/clone/delete routes | Base-image lifecycle, validation, EOL and dependency protection |
| /roles and role hash/tab interactions | Role catalogue, dependency/usage navigation and variable round-tripping |
| /recipes and all create/edit/clone/delete routes | Recipe composition, conflicts, scheduling, cloning and protected deletion |
| /recipes/:id/bake and /recipes/:id/bakes/:number | Bake initiation, debug gating, status/log refresh, diagnostics and deletion |
| /recipes/:id/usages, /bake-usages, /recipes/:id/bakes/:number/packages | Source/copy usage joins and exact machine-readable contracts |
| /housekeeping and /housekeeping/deleteOrphans | Orphan selection, scan errors and tampered submissions |
| Scheduler, Packer, events, notifications, cleanup jobs | Separate backend/integration scenarios with clocks and service doubles |

Do not add individual tests for logos, decorative icons, colour shades, column alignment, generic Bootstrap behaviour, every static link, external documentation pages, or each Ansible role's internals. EOL warnings, usable form errors, correct AMI clipboard values and dependency navigation are functional, not cosmetic. Do not expect WebSockets/live updates: bake pages explicitly require refresh. Mobile/browser matrices, full AWS/CDK deployment tests and exhaustive role-parser combinations are outside this plan unless a requirement emerges. Retain and extend the existing model, role, package, Prism, Packer-parser and housekeeping tests for their pure logic. One supervised disposable-account acceptance bake/copy/delete journey verifies the external integration boundary; it is not the normal browser suite.

## Test Scenarios

### 1. Access, sessions and request integrity

**Seed:** `seed.spec.ts`

#### 1.1. P0 A1 - Protected reads and writes require a valid authorised session

**File:** `tests/e2e/access.spec.ts`

**Steps:**
  1. Start with fresh storage and a reset dataset containing Base A, Recipe R and one completed bake. In independent contexts, exercise no session, an expired session and a tampered session.
    - expect: No context inherits the authorised seed session.
  2. Request /, /base-images, /roles, /recipes, /housekeeping, recipe/bake detail, /bake-usages and the package endpoint with redirects disabled; send representative create, bake, clone and delete POSTs using otherwise valid payloads.
    - expect: Protected endpoints redirect to authentication or reject the request; they do not disclose protected HTML or JSON.
    - expect: No records, jobs, Packer processes, deletion markers or SNS messages are created.
  3. Repeat representative reads with an authorised session, then invalidate that session before submitting an open edit form.
    - expect: Authorised reads work; the expired form submission does not persist edits or appear successful.

#### 1.2. P0 A2 - OAuth grants access for any permitted group and fails closed otherwise

**File:** `test/controllers/LoginSpec.scala`

**Steps:**
  1. Start with a clean session and controlled OAuth/group-checker responses. Navigate through /login and /oauth2callback with a valid identity belonging to exactly one configured allowed group; parameterise each allowed group.
    - expect: Membership in one allowed group is sufficient; no requirement to belong to all groups is introduced.
    - expect: A session is established and the default destination is the application home.
  2. In new sessions repeat with no allowed membership, failed group lookup, invalid OAuth state, and rejected identity/token responses.
    - expect: No authenticated session is issued; the failure is surfaced/logged through the normal authentication flow.
    - expect: Protected endpoints remain inaccessible. Do not assert brittle Google-hosted page markup.

#### 1.3. P0 A3 - CSRF protects every mutation without breaking legitimate forms

**File:** `tests/e2e/csrf.spec.ts`

**Steps:**
  1. Reset fixtures and authenticate. Enumerate every POST in conf/routes: base-image and recipe create/edit/clone/delete, bake start/delete and orphan deletion. Submit otherwise valid requests with missing and invalid CSRF tokens, ensuring the test does not accidentally use a trusted bypass header.
    - expect: CSRF-requiring requests are rejected and all backing state and service-call counts remain unchanged.
  2. Use the real UI forms with valid CSRF state for a base-image clone, recipe mutation, bake action and orphan selection.
    - expect: Legitimate forms work under the real CSRF middleware.
    - expect: Missing fields in base-image clone/housekeeping are reported as defects if submissions fail; do not disable CSRF or inject a token into those forms to hide the problem.

### 2. Base images and support lifecycle

**Seed:** `seed.spec.ts`

#### 2.1. P1 B1 - Create and edit a base image with persistent builtin roles and builder settings

**File:** `tests/e2e/base-images.spec.ts`

**Steps:**
  1. Start with no base images and a known role catalogue. Open /base-images, choose Create new base image, and enter a unique ID, source AMI, Linux Distribution, description and End of Life Date. Select a builtin role and enter foo: bar, packages: [curl, wget]. Enable Requires XLarge builder instance and Save.
    - expect: Submission redirects to the new detail page; persisted data exactly matches the entered values and the authenticated creator.
    - expect: The selected role exposes its variable input and its parsed variables/dependencies appear in detail.
  2. Reload, then Edit. Change source AMI, distribution, EOL date and description; replace one builtin role and disable XLarge. Save and reopen Edit.
    - expect: The ID is unchanged, all edits persist, removed roles are absent, and creator metadata is retained while modification metadata updates.
    - expect: Base-image list and detail links resolve to this record; no duplicate record is created.

#### 2.2. P1 B2 - Base-image validation rejects invalid input without overwriting data

**File:** `tests/e2e/base-image-validation.spec.ts`

**Steps:**
  1. For each independent case reset data and open create or edit with an otherwise valid payload. Exercise ID lengths 2, 3, 50 and 51 on create; empty/21/22-character AMI ID; empty/10000/10001-character description; missing or malformed EOL date; and an unrecognised distribution posted directly.
    - expect: ID lengths 3 and 50, AMI length 21 and description lengths 0 and 10000 are accepted by the current form contract.
    - expect: Invalid boundary values and unknown distribution return 400 with relevant error feedback; there is no successful persistence.
    - expect: AMI string length validation is not proof of a valid AWS AMI; do not add an undocumented format assertion.
  2. Pre-create the submitted ID and submit create again with different data. For an edit validation failure, reload the original record.
    - expect: Duplicate creation returns 409 and identifies the ID conflict without changing the existing record.
    - expect: Invalid edits leave persisted values unchanged and permit correction from the error page.

#### 2.3. P1 B3 - Clone a base image without sharing mutable configuration

**File:** `tests/e2e/base-image-clone.spec.ts`

**Steps:**
  1. Start with Base A including builtin variables, EOL and XLarge settings. Clone to an unused ID and inspect the resulting record.
    - expect: The clone has the requested new ID and new creation metadata, with the source description, AMI, distribution, EOL, builtin roles/variables and builder setting copied.
    - expect: The source is unchanged.
  2. Edit the clone; independently try a too-short/too-long ID, an existing target ID, a missing source, and a legacy source with no Linux distribution.
    - expect: Editing the clone does not alter the source.
    - expect: Invalid ID gives clone failure feedback, duplicate target returns 409, missing source returns 404, and a legacy source without distribution gives the explicit failure message without creating a record.

#### 2.4. P0 B4 - Base-image deletion respects recipe references and confirmation

**File:** `tests/e2e/base-image-deletion.spec.ts`

**Steps:**
  1. Start with an unreferenced base image. Open Delete..., leave without confirming, and inspect stored state. Then reopen and confirm deletion.
    - expect: Opening or leaving confirmation never mutates data.
    - expect: Confirmed deletion removes only that base image, returns to the list, and subsequent detail is 404.
  2. In a fresh case create Recipe R referencing Base A but with no active AWS usage. Open Base A's deletion page and also submit a direct authenticated delete POST.
    - expect: A recipe reference alone blocks base-image deletion, regardless of active infrastructure usage.
    - expect: The confirmation explains the dependency; POST preserves the image and recipe and returns failure feedback.
  3. In a separate case add a recipe reference after opening an initially permitted confirmation, then confirm.
    - expect: The server rechecks references and refuses deletion; a stale confirmation cannot create a dangling recipe.

#### 2.5. P1 B5 - EOL warnings remain consistent across base-image and recipe views

**File:** `tests/e2e/base-image-eol.spec.ts`

**Steps:**
  1. Freeze server time. Create independent base images whose EOL is before now, equal to now, just inside three calendar months, exactly three calendar months, beyond three months, and absent for a legacy record. Reference each from a recipe.
    - expect: Expected classifications follow BaseImage.eolStatus: before now is End of Life; otherwise strictly before now plus three months is End of Life Soon; the exact three-month boundary and absent EOL are Supported.
    - expect: Boundary dates are set with a fixture clock rather than wall-clock sleeps.
  2. Inspect /base-images, /recipes, each base detail and each recipe detail, then change one EOL date and reload.
    - expect: Status text/date and warning meaning agree across all surfaces and update after editing.
    - expect: Legacy missing EOL is handled without a crash; no unsupported rule that EOL prevents baking is asserted.

### 3. Recipes and role composition

**Seed:** `seed.spec.ts`

#### 3.1. P1 R1 - Create, edit and clear optional recipe configuration

**File:** `tests/e2e/recipes.spec.ts`

**Steps:**
  1. Reset data, create Base A/Base B and known roles, then use Create new recipe. Enter a unique ID, description, disk size 16, Quartz schedule 0 0 3 * * ?, two comma-separated encrypted-copy account IDs with surrounding spaces, Base A and one custom role. Save.
    - expect: Detail and persisted record match the selection, trimmed account list, disk and schedule.
    - expect: Inherited base roles and recipe roles are distinguishable; authenticated creation metadata is recorded.
  2. Reload and edit: change to Base B, modify roles/variables, and clear description, disk size, schedule and encrypted-copy accounts. Save and reopen.
    - expect: Cleared optional values stay absent rather than retaining old values; account targets become empty and the previous scheduled job is removed.
    - expect: New base-image inheritance is reflected; ID and original creation metadata remain unchanged.

#### 3.2. P1 R2 - Recipe field boundaries and reference validation are recoverable

**File:** `tests/e2e/recipe-validation.spec.ts`

**Steps:**
  1. In isolated parameterised cases submit otherwise valid recipes with ID lengths 2/3/50/51, description lengths 0/10000/10001, non-integer/overflow disk size, a malformed Quartz schedule, a schedule over 50 characters, and alphabetic encrypted-copy account input. Exercise create and edit where the field exists.
    - expect: Valid form boundaries pass; rejected values return 400 and do not persist a partial recipe or change a scheduled job.
    - expect: Empty disk/schedule/accounts are allowed. Disk size is currently any parsed Int and account validation permits digits/whitespace/commas, not necessarily 12 digits; zero/negative disk and short digit-only accounts are tracked as domain-validation gaps, not assumed rejected.
  2. Pre-create the same recipe ID and submit conflicting create data. Separately submit with no base image or a base image removed after the form was loaded.
    - expect: Duplicate ID returns 409 and an actionable recipe-specific form error, preserving the existing record. The current newBaseImage error template is a regression target.
    - expect: Unknown/missing base image gives validation feedback and no recipe/job is created. Editing a record with an unknown selected base leaves the original reference intact.

#### 3.3. P1 R3 - Role variables round-trip and invalid selected roles cannot partially save

**File:** `tests/e2e/role-variables.spec.ts`

**Steps:**
  1. Start with Base A and Recipe R and repeat the UI flow once on base-image edit and once on recipe edit. Select a role and enter scalar, quoted string, list and dictionary values: foo: bar, note: 'two words', packages: [curl, wget], config: {mode: safe}. Save, reload and reopen edit.
    - expect: Typed values retain their meaning after parse/display/edit/save; compare maps rather than textual key order.
    - expect: Enabled roles show their variable inputs and detail links; an unchecked role's stale hidden input is not persisted.
  2. In a new case submit a selected role with malformed syntax such as packages: [curl, and inspect the persisted record and scheduler.
    - expect: The request returns 400 with Problem parsing roles, no partial save, and no rescheduling.
    - expect: Correcting the syntax permits the intended update; a syntax error is never silently converted to an empty role configuration.

#### 3.4. P1 R4 - Clone recipe settings, reset build identity and register its schedule

**File:** `tests/e2e/recipe-clone.spec.ts`

**Steps:**
  1. Reset data and create a scheduled Recipe R with roles, variables, disk size, encrypted-copy targets and several bakes. Clone to a new unused ID.
    - expect: The new recipe copies configuration and references the same base image, but does not copy bake records or reuse the source's build-number sequence.
    - expect: Source and clone can subsequently be edited independently.
  2. Inspect the controlled scheduler immediately, not just the displayed Build schedule. Trigger the clone's job once with a Packer double.
    - expect: Safety/behaviour contract: the copied schedule is active immediately and targets the clone, producing its own build identity.
    - expect: A missing job is a failure; current cloneRecipe omits reschedule and is expected to expose this gap.
  3. Independently submit an invalid clone ID, an existing target ID and a valid clone request for a missing source.
    - expect: No extra records/jobs are created; invalid ID has clear failure feedback, duplicate ID returns 409 and missing source returns 404.
    - expect: A failure must not be mistaken for success merely because it appears in an informational flash container.

#### 3.5. P1 R5 - Role catalogue exposes the dependencies and consumers needed to compose recipes

**File:** `tests/e2e/roles.spec.ts`

**Steps:**
  1. Start with a known parent -> child -> leaf role chain, one role without README, and fresh recipe/base-image consumers. Open /roles, select the parent and inspect README, Tasks, Dependencies and Used by.
    - expect: The selected role's actual README/tasks are shown, transitive dependencies are discoverable, and direct consumers agree with the underlying role/recipe/base-image data.
    - expect: No-README roles have usable Tasks content rather than an empty initial panel.
  2. Follow a dependency link, reload its hash URL directly, use browser Back, and follow recipe/base-image consumer links.
    - expect: Hash selection and history show the intended role, never stale content from another role.
    - expect: Consumer links open the correct entities. Leaf/unreferenced roles display honest empty states rather than broken panels.

### 4. Baking and diagnostics

**Seed:** `seed.spec.ts`

#### 4.1. P0 K1 - Manual bake creates a distinct build and starts the selected recipe exactly once

**File:** `tests/e2e/bake-start.spec.ts`

**Steps:**
  1. Start with a fresh Recipe R, next build number known, and an instrumented Packer double. Open the recipe and click Bake! once.
    - expect: A single POST creates one Running bake with the next build number and authenticated startedBy, starts one process request and redirects to that bake's detail.
    - expect: No real EC2 instance or SNS delivery is made by the routine test.
  2. From independent contexts submit two intentional bake requests concurrently for that same recipe; separately force build-number allocation to fail and request a missing recipe.
    - expect: Two intentional submissions produce two different build numbers without overwriting either record; do not assume undocumented click deduplication.
    - expect: Allocation failure returns an explicit 500 and launches no process; missing recipe returns 404 and launches none.

#### 4.2. P1 K2 - Refresh shows persisted bake progress, failure and timeout diagnostics

**File:** `tests/e2e/bake-status.spec.ts`

**Steps:**
  1. Start a fake Running bake, open its detail and append known log events and an AMI-created event through the real event pipeline.
    - expect: Before a refresh the page need not change; it explicitly instructs the user to refresh.
    - expect: After refresh, status, AMI and ordered log messages agree with persisted events and the same recipe/build identity.
  2. In separate reset cases emit process exit 0, non-zero exit, and a timeout transition. Reload bake detail and its recipe.
    - expect: Exit 0 appears as Complete, non-zero as Failed, and timeout as TimedOut; failed/incomplete bakes do not invent an AMI.
    - expect: Existing diagnostic logs remain readable, no stale success state is shown, and related recipe history reflects the same status.

#### 4.3. P0 K3 - Debug baking is gated by server stage, not merely by button visibility

**File:** `tests/e2e/bake-debug.spec.ts`

**Steps:**
  1. Start a DEV/CODE-profile isolated fixture with a process double. Use Bake with debug enabled and inspect the generated invocation.
    - expect: The debug action is available and the Packer invocation includes -debug. A normal Bake! does not include it.
  2. Start a PROD-profile fixture using doubles only, never a production deployment. Inspect the recipe and directly POST the bake route with debug=true.
    - expect: The debug button is absent and the server still forces debug off on the crafted request.
    - expect: No secret SSH material or real builder is created by the test.

#### 4.4. P1 K4 - Recent bakes, encrypted-copy status and AMI copying identify the correct build

**File:** `tests/e2e/bake-history.spec.ts`

**Steps:**
  1. Reset fixtures and seed 21 dated bakes for R, including mixed statuses and a completed bake with copies in two accounts, one account absent from the account-name lookup. Open the recipe.
    - expect: Exactly the most recent 20 bakes appear, newest first, and rows link to their own build number.
    - expect: Source AMI and encrypted copies are associated with the correct bake; copy owner, encryption tag and status reflect Prism, with account number fallback for an unknown name.
  2. Use the clipboard button on a source AMI and on a copied AMI, and open the newest bake. Directly navigate to the omitted older bake.
    - expect: Clipboard contents are the exact selected AMI ID, not the source ID for a copy or an adjacent row's ID.
    - expect: The older bake remains reachable by its detail URL. No arbitrary history pagination or live-update behaviour is assumed.

#### 4.5. P1 K5 - Package diagnostics compare with the previous successful bake and degrade clearly

**File:** `tests/e2e/bake-packages.spec.ts`

**Steps:**
  1. Reset fixtures with successful builds 1 and 3, failed build 2, and current build 4. Provide deterministic S3 package lists with one unchanged, one removed and one added/version-changed package. Open build 4.
    - expect: All installed packages are shown with package-manager header lines removed.
    - expect: Package Changes compares against build 3, not a failed or future bake; the previous-bake link points to build 3 and additions/removals have the correct meaning.
  2. Independently test a first successful bake, unchanged lists, missing previous list, missing current list and no configured data bucket.
    - expect: No prior list means no bogus comparison, while available current packages remain visible.
    - expect: Missing current packages or absent bucket gives an explanatory message, not a page crash or fabricated empty success.
    - expect: Unchanged package input reports no false changes.

### 5. Usage inventory and machine-readable contracts

**Seed:** `seed.spec.ts`

#### 5.1. P0 U1 - Usage joins include direct AMIs, copied AMIs, launch templates and old bakes

**File:** `tests/e2e/usages.spec.ts`

**Steps:**
  1. For each fresh case create R and an unrelated unused recipe. Provide exactly one usage, parameterising source/copy AMI and instance/launch-configuration/launch-template. Include an old used bake outside the recent-20 history.
    - expect: The fixture's expected consumer and originating bake are unambiguous; no other usage is present.
  2. Inspect recipe listing, base-image consumers, recipe summary, /recipes/:id/usages and bake detail; follow the usage-to-bake link.
    - expect: R is classified as in use everywhere, including launch-template-only and copied-AMI-only cases; the unrelated recipe remains unused.
    - expect: Usage rows identify the originating build, actual resource type/account and copy relationship; source/copy rows are not conflated.
    - expect: Old bake usage remains visible and protects deletion even when the bake is outside recent history. The current launch-template-only summary omission is a defect target.
  3. Reset Prism to an explicitly successful empty inventory and reload after its controlled refresh.
    - expect: The empty state says there are no usages and contains no stale resource links; a failed inventory fetch must not be substituted for this successful-empty fixture.

#### 5.2. P1 U2 - Package and bake-usage JSON contracts match stored data

**File:** `tests/e2e/api-contracts.spec.ts`

**Steps:**
  1. Reset fixtures, authenticate, create known package objects and direct/copied usages. GET /recipes/:id/bakes/:number/packages without redirect following.
    - expect: 200 JSON has the shape {"packages":["package/version line", ...]} with strings and no package-manager header lines.
    - expect: For a missing package object or unconfigured bucket, the endpoint returns 404 with an explanatory message, not an HTML login page counted as success.
  2. GET /bake-usages and inspect each entry; repeat with no usage records.
    - expect: 200 JSON is an array of {"bakeId":{"recipeId":"...","buildNumber":number},"packageListS3Location":"s3://<test-bucket>/packagelists/<recipe>--<build>.txt"}. Each entry refers to a used originating bake, including copied-image usage.
    - expect: No usages yields []. Do not assume deduplication: one originating bake can currently occur for multiple used AMIs.
    - expect: The missing-bucket unknown-bucket fallback is documented as a source-derived integration concern, not a valid real package location.

#### 5.3. P1 U3 - Missing entities and invalid paths do not mutate or cross-link data

**File:** `tests/e2e/not-found.spec.ts`

**Steps:**
  1. Reset to a small known dataset. Request missing base-image/recipe/bake detail, edit, usage and delete-confirmation routes; submit valid authenticated edit, clone, delete or bake payloads against missing IDs.
    - expect: Entity lookups return 404 and do not create substitute objects, jobs or deletion markers.
    - expect: Existing unrelated records are unchanged; missing bake package data follows its documented package 404 contract.
  2. Use a non-integer build-number route segment and an unknown application path.
    - expect: Routing rejects invalid input with a non-success 4xx response rather than executing a controller mutation or returning another build.

### 6. Protected destructive workflows

**Seed:** `seed.spec.ts`

#### 6.1. P0 D1 - Unused bake deletion is explicit, deferred and scoped to the selected bake

**File:** `tests/e2e/bake-deletion.spec.ts`

**Steps:**
  1. Start with two completed unused bakes and known encrypted copies. Open Delete bake and all associated AMIs... for one bake, then leave without confirming.
    - expect: The confirmation identifies recipe/build and explains delayed copy deletion; no marker, status or external message changes merely by visiting.
  2. Confirm Delete with a genuine token. Reload recipe and inspect both stored bake records while the housekeeping runner is paused.
    - expect: Only the selected bake becomes DeletionScheduled and is marked for deletion; it is not falsely presented as already physically removed.
    - expect: The other bake is unchanged. Actual message delivery and cleanup are verified separately in H4.

#### 6.2. P0 D2 - All consumer types block bake and recipe deletion, including stale confirmations

**File:** `tests/e2e/deletion-guards.spec.ts`

**Steps:**
  1. For each independent fixture create source/copy usage by one instance, launch configuration or launch template. Include an old used bake in a recipe with a newer unused bake. Open deletion confirmations and submit direct authenticated delete POSTs.
    - expect: Used bake and recipe deletion return 409 on POST; the confirmation explains why deletion is blocked and links to usage.
    - expect: Neither a disabled-looking link nor omission from recent history is treated as the protection. No deletion flags, scheduler changes or external messages occur.
  2. In a new case open an allowed confirmation with zero usages, then add a consumer and make it visible in the controlled Prism snapshot before confirming.
    - expect: The POST rechecks the latest available usage data and refuses deletion.
    - expect: Prism-cache freshness and the later housekeeping execution are separate consistency risks; do not claim this closes every distributed race.

#### 6.3. P0 D3 - Unused recipe deletion stops scheduling and marks all its bakes, not other recipes

**File:** `tests/e2e/recipe-deletion.spec.ts`

**Steps:**
  1. Reset fixtures with scheduled unused R containing both completed and failed bakes, plus an unrelated recipe. Open R's Delete... page, verify the bake count, and leave without confirming.
    - expect: No database or scheduler mutation occurs on confirmation-page navigation.
  2. Confirm deletion and inspect records/jobs before allowing background deletion.
    - expect: R is removed, its Quartz job is removed, and all R bakes are marked for deferred deletion.
    - expect: The shared base image and other recipe/jobs/bakes are untouched; the recipe list updates and R detail becomes 404.
    - expect: A future trigger for the deleted recipe creates no new bake.

### 7. Manual housekeeping

**Seed:** `seed.spec.ts`

#### 7.1. P0 H1 - Orphan selection is honoured, including empty selection and scan warnings

**File:** `tests/e2e/housekeeping.spec.ts`

**Steps:**
  1. Reset fixtures with two orphaned bakes and one bake attached to a valid recipe. Open /housekeeping, uncheck one orphan and submit Delete Orphaned Bakes.
    - expect: Only orphaned bakes appear as choices; only the selected orphan receives a deletion marker.
    - expect: The unchecked orphan and valid recipe bake remain intact. A successful redirect alone is insufficient.
    - expect: The orphaned-bake/orphaned-bakes mismatch and missing CSRF input are regression targets.
  2. In independent fresh cases submit with every orphan unchecked, with no orphans at all, and with one recipe decoding/scan error.
    - expect: Empty selections never imply delete-all.
    - expect: The scan error count is visible before manual confirmation so potentially incomplete inventory is not disguised as a clean scan.
    - expect: Manual override behaviour is tested explicitly; the automatic job must still stop on any scan error as covered in H3.

#### 7.2. P0 H2 - Crafted housekeeping requests cannot delete unrelated bakes

**File:** `tests/e2e/housekeeping-validation.spec.ts`

**Steps:**
  1. Start with an attached bake, one true orphan and one absent bake ID. Obtain a genuine token, then independently POST malformed IDs, absent IDs, duplicate IDs and an attached bake ID to /housekeeping/deleteOrphans using the controller's field name.
    - expect: Safety contract: malformed or non-orphan selections cannot mark an attached bake or create a synthetic record; errors are explicit or logged.
    - expect: A duplicate valid selection does not duplicate external deletion work.
  2. Inspect backing records and captured messages after each request rather than trusting redirect status.
    - expect: Only explicitly valid orphan targets may be marked.
    - expect: The current controller parses and marks posted IDs without checking orphanhood; report a failure if crafted attached IDs are accepted.

### 8. Backend lifecycle and integration boundaries

**Seed:** `seed.spec.ts`

#### 8.1. P1 S1 - Scheduling creates, replaces and removes jobs, and the kill switch stops automatic bakes

**File:** `test/schedule/BakeSchedulerSpec.scala`

**Steps:**
  1. Start with an empty controlled scheduler, fake server clock and Packer double. Create R with a valid schedule, edit its expression, clear it, then initialise a fresh scheduler from saved recipes.
    - expect: There is at most one job/trigger per scheduled recipe; edit replaces the old trigger, clearing removes it, and startup restores the saved scheduled recipes without duplicates.
  2. Trigger a scheduled recipe once with scheduled bakes enabled. Independently test disabled flag, absent/invalid enabled configuration, missing recipe and recipe with no schedule.
    - expect: Enabled scheduling creates one distinct bake startedBy scheduler with debug off.
    - expect: Disabled/missing/invalid configuration and missing/unscheduled recipes create no bake/process; reasons are logged.
    - expect: Use a controlled clock, not a real overnight wait; manual baking remains a separately tested route.

#### 8.2. P0 P1 - Generated Packer/Ansible configuration preserves architecture, roles and isolation

**File:** `test/packer/PackerBuildConfigGeneratorSpec.scala`

**Steps:**
  1. Reset pure fixtures with x86_64 and arm64 source metadata, Base A/Base B, builtin plus recipe roles, scalar/list/dictionary variables, optional disk size and optional package bucket. Generate playbook and Packer configuration without executing a process.
    - expect: Builtin roles precede recipe roles; variables retain intended values and architecture variables are correct.
    - expect: x86_64 maps to t3 and arm64 to t4g; ordinary/XLarge selections map to small/xlarge and XLarge polling is 15 seconds with 240 attempts.
    - expect: Source AMI, distribution-specific login/provisioners, disk mappings, recipe/build tags and configured VPC/subnet/profile/security group are correct.
    - expect: Package upload uses the build-specific S3 key and metadata only when a bucket exists; IMDSv2 remains required.
  2. Provide unsupported architecture and missing source metadata.
    - expect: Generation fails explicitly before a builder process is launched; it never silently chooses an arbitrary architecture or source AMI.

#### 8.3. P0 P2 - Actual Packer process concurrency is bounded and failures release resources

**File:** `test/packer/PackerRunnerSpec.scala`

**Steps:**
  1. Use an instrumented harmless Packer executable that records process start/exit and can block. Set maxInstances=2 and request three bakes with separate IDs.
    - expect: Safety contract: no more than two actual child processes may be active at any instant; counting only monitoring threads is insufficient.
    - expect: The third request starts only when a slot is released. Current process-start ordering is a defect target.
  2. Complete one process, fail another, and separately simulate executable start failure and metadata failure; inspect completion events and temporary paths.
    - expect: Every started bake has an observable outcome, freed capacity permits subsequent work, and logs/AMI events are attributed to the correct bake.
    - expect: Generated playbooks/configs/cache directories are removed after completion or reported for cleanup on failure; a failed process does not become Complete or silently hang forever.

#### 8.4. P1 N1 - AMI-created and failed-bake notifications carry the right targets and identity

**File:** `test/notification/NotificationSenderSpec.scala`

**Steps:**
  1. Use fresh event/storage fixtures and captured SNS/notification clients. Emit AMI-created for a bake with two target accounts, then a failure or timeout for a separate bake.
    - expect: AMI copy-request JSON contains sourceAmi, sourceRegion, targetAccounts, name, description and tags matching that bake.
    - expect: Failure/timeout notification targets only its configured accounts and includes correct recipe/log links and non-PROD stage annotation.
    - expect: Successful completion does not send a failure notification.
  2. Independently use no target accounts, missing notification configuration, a missing bake, and publisher failure.
    - expect: No-target or unavailable notification inputs do not send an invented notification; the reason is logged.
    - expect: Delivery failure is observable and does not relabel a failed bake as successful or send data to an unrelated topic/account.

#### 8.5. P0 H3 - Automatic retention and orphan detection respect age, usage and incomplete scans

**File:** `test/housekeeping/MarkOldUnusedBakesForDeletionSpec.scala`

**Steps:**
  1. With a fixed clock, seed used/unused bakes at exactly 30 days, 30 days plus 23 hours, and 31 days; include direct/copied usages of every resource type and 101 eligible unused bakes.
    - expect: The current rule uses getStandardDays > 30: only 31 complete days and older qualify, not 30 days plus a fraction.
    - expect: Used bakes are excluded and a run marks no more than 100 eligible bakes.
  2. Independently run orphan detection with complete recipe inventory, then with one recipe decode error. Include both orphaned and correctly attached bakes.
    - expect: A clean scan marks only bakes with no recipe.
    - expect: Any recipe scan error exceeds FAULT_TOLERANCE=0 and prevents automatic orphan marking; incomplete data cannot authorise mass deletion.
    - expect: Extend existing MarkOldUnusedBakesForDeletionSpec and MarkOrphanedBakesForDeletionSpec rather than reproducing this combinatorics in browser tests.

#### 8.6. P0 H4 - Deferred deletion publishes bounded source/copy targets and preserves retryable failures

**File:** `test/housekeeping/BakeDeletionSpec.scala`

**Steps:**
  1. Seed marked and unmarked bakes, with source/copy AMIs across accounts totalling more than ten images, and an instrumented publisher. Run one deletion pass.
    - expect: Only marked bakes supply targets; each AMI is paired with its correct owning account.
    - expect: Messages contain at most ten AMIs each; all expected source/copy targets are accounted for.
    - expect: After successful publication, only targeted bake/log records are removed. Publication is not proof that remote AMIs/snapshots have already disappeared.
  2. In a separate reset case fail publication before cleanup; also test a marked bake without an AMI and repeat a successful deletion pass.
    - expect: A failed publication is logged and affected bake/log data is retained for retry; no unrelated records disappear.
    - expect: No-AMI bakes are cleaned without inventing a cloud target; repeated processing must not expand the deletion set.
  3. For a supervised disposable-account acceptance run only, create one real bake requesting an encrypted copy, wait for Prism to report the copy, verify encryption/owner, remove all test usages, request deletion, and observe the external copier/housekeeper to completion.
    - expect: The expected encrypted copy is available in the nominated test account, then the intended source/copy images and relevant snapshots are retired according to the external service contract.
    - expect: Inventory evidence confirms no test resources remain. Stop and report a cleanup failure rather than deleting broader account resources.

#### 8.7. P0 H5 - Timeout cleanup targets only eligible builders and preserves terminal bake states

**File:** `test/housekeeping/TimeOutLongRunningBakesSpec.scala`

**Steps:**
  1. Freeze server time and use fake EC2/BakesRepo clients. Run bake timeout against Running bakes exactly two hours old and just older, a completed/failed bake, and an old Running bake whose instance is absent.
    - expect: Only Running bakes strictly older than two hours are timed out by this job; exactly-at-boundary and terminal bakes are not changed.
    - expect: A matching builder is terminated; an absent instance is logged and the eligible Running bake still becomes TimedOut.
  2. Independently run DeleteLongRunningEC2Instances against tagged Packer instances exactly one hour old and just older, a non-Packer instance, and an old builder with missing/malformed BakeId.
    - expect: Only eligible old Packer instances are terminated; exactly one hour and non-Packer instances are preserved.
    - expect: Bake status changes only if still Running; a missing/malformed tag is logged without changing an unrelated bake.
    - expect: Extend the two existing timeout/EC2 specs; no real instance termination is permitted in this test.

### 9. Significant resilience and output safety

**Seed:** `seed.spec.ts`

#### 9.1. P0 X1 - Descriptions, role content, bake logs and package diffs do not execute injected markup

**File:** `tests/e2e/output-safety.spec.ts`

**Steps:**
  1. Start isolated fixtures with marker payloads in descriptions, custom role variables, log message text and package added/removed lines. Use a harmless payload that attempts only to set window.__amigoInjectionMarker, and capture unexpected outbound requests. Include a role README payload in a controlled role catalogue.
    - expect: No production content or service is used; intended data and the marker are known before rendering.
  2. Open affected base/recipe/role/bake views, including the package diff, and inspect the DOM and marker.
    - expect: Safety contract: text is readable without script execution, event-handler injection, active injected elements or unexpected network requests.
    - expect: CSP blocking alone is not sufficient evidence of correct escaping; inspect inserted markup too.
    - expect: Raw Html rendering in MessagePart/package diff and Markdown conversion are regression targets, not trusted input by assumption.

#### 9.2. P0 X2 - Dependency failure cannot look like successful mutation or authoritative zero usage

**File:** `test/controllers/DependencyFailureSpec.scala`

**Steps:**
  1. In separate fresh fixtures inject Dynamo write/read failure, unavailable package S3, Prism refresh failure with and without previous cache, and a Packer startup exception while operating representative create/edit/bake/delete workflows.
    - expect: Failures produce explicit non-success outcomes or clearly documented stale-data handling; no success flash appears for an uncommitted write.
    - expect: S3 package failure degrades to the documented diagnostic/404 response rather than breaking all bake details.
    - expect: A failed Prism read must not be treated as authoritative zero usage to permit destructive action; distinguish cache policy from a successful empty result.
  2. Inspect persisted state, scheduled jobs, running processes and external messages after each failure, then restore the dependency and retry once deliberately.
    - expect: No hidden partial writes, duplicate jobs, unintended cloud work or loss of the only diagnostic evidence goes unnoticed.
    - expect: The controlled retry has one attributable outcome; any non-atomic or stale-data safety gap is recorded as a defect, not hidden by browser retries.
