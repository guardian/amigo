# AMIgo basic UI end-to-end test plan

## Scope

Automated end-to-end tests verify that AMIgo is visible and its primary navigation opens live pages. Do not test functionality beyond page visibility and navigation. Visual regression testing is out of scope.

## Preconditions

- Playwright starts the isolated test application at `http://localhost:9000`; the full development app must not be running on that port.
- Use a desktop browser with the primary navigation visible.
- No data creation, modification or deletion is required.
- Start each scenario at `/` unless otherwise specified.

Run the automated tests from the repository root with `npm run test:e2e`. All runs record traces and retain generated test artefacts. Screenshots are captured on failure; videos are not recorded.

All Playwright runs start an isolated application on `http://localhost:9000` with development authentication, a local Prism stub and read-only synthetic data. The same scenarios run locally and in CI without a load balancer, real Prism access or AWS credentials. See `tests/e2e/README.md` for startup details.

## Shared expectations

For every destination:

- The URL matches the expected path on the application origin.
- The document title is `AMIgo`.
- The page-specific heading and content listed below are visible.
- The primary navigation contains AMIgo, Base images, Roles, Recipes and Housekeeping and remains visible and usable.
- The destination is not a blank page, browser error, 404 page or indefinitely loading page.
- Checks do not depend on particular records, record counts or backend warning counts.

## 1. Basic UI visibility and navigation

### 1.1 Homepage is visible

**Steps**

1. Open `http://localhost:9000/`.

**Expected results**

- The URL path is `/`.
- The heading `¡Hola AMIgo!` is visible.
- The introduction `AMIgo is a self-serve AMI bakery.` is visible.
- The primary navigation satisfies the shared expectations.

### 1.2 Base images navigation

**Steps**

1. Open `/`.
2. Click **Base images** in the primary navigation.

**Expected results**

- The URL path is `/base-images`.
- The level-one heading `Base images` is visible.
- The base-image table is visible; specific columns and records are not checked.
- The shared expectations hold.

### 1.3 Roles navigation

**Steps**

1. Open `/`.
2. Click **Roles** in the primary navigation.

**Expected results**

- The URL path is `/roles`.
- The level-one heading `Roles` is visible.
- The instruction `Choose a role from the list to see more details.` is visible; no particular role or non-empty role list is required.
- The shared expectations hold.
- Do not select a role or verify role details.

### 1.4 Recipes navigation

**Steps**

1. Open `/`.
2. Click **Recipes** in the primary navigation.

**Expected results**

- The URL path is `/recipes`.
- The level-one heading `Recipes` is visible.
- The section heading `Recipes in use` is visible.
- The shared expectations hold.
- Empty sections are acceptable; specific recipe records are not required.

### 1.5 Housekeeping navigation

**Steps**

1. Open `/`.
2. Click **Housekeeping** in the primary navigation.

**Expected results**

- The URL path is `/housekeeping`.
- The level-one heading `Housekeeping` and the `Orphaned Bakes` section are visible.
- The shared expectations hold.
- Do not click **Delete Orphaned Bakes** or any confirmation control.

### 1.6 Navigation remains usable and returns home

**Steps**

1. Open `/base-images` directly.
2. Click **Roles** in the primary navigation.
3. Click the **AMIgo** brand link.

**Expected results**

- The initial page has path `/base-images` and heading `Base images`.
- Clicking **Roles** reaches `/roles` and displays the heading `Roles`.
- The primary navigation remains visible and usable throughout.
- Clicking **AMIgo** returns to `/` and displays `¡Hola AMIgo!` again.
- This scenario checks one section-to-section transition and return home without repeating the individual destination content checks.

## Exploration observations

Explored at `http://localhost:9000` on 5 October 2026. The homepage, all four primary navigation destinations and the brand-link return path rendered successfully.

Housekeeping displayed: `Housekeeping encountered 2 errors when searching the database, continue anyway?`

This warning is an observed backend issue, not a broken navigation link. It does not fail these end-to-end tests if the page renders its expected heading and section. Do not assert the warning's presence or exact error count. Backend health remains outside this plan's scope.

## Out of scope

- Creation and editing forms, including opening new-item forms.
- Table-row detail links and role selection.
- Baking, deletion and other state-changing actions.
- Search, filtering and data correctness.
- Backend health, AWS operations and warning resolution.
- Visual regression testing and screenshot baselines.
- Mobile layouts, additional viewport/browser coverage, performance and comprehensive accessibility testing.
