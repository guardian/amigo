# AMIgo basic UI smoke-test plan

## Scope

Verify that AMIgo is visible, its primary navigation opens live pages, and the appearance of those pages matches approved visual baselines. Do not test functionality beyond page visibility, navigation and visual regression. This plan does not require automated test implementation.

## Preconditions

- AMIgo is running at `http://localhost:9000`.
- Use a desktop browser with the primary navigation visible.
- No data creation, modification or deletion is required.
- Start each scenario at `/` unless otherwise specified.
- Visual regression scenarios additionally require the controlled environment and approved baselines described in section 2.

## Shared expectations

For every destination:

- The URL matches the expected path on the application origin.
- The document title is `AMIgo`.
- The page-specific heading and content listed below are visible.
- The primary navigation contains AMIgo, Base images, Roles, Recipes and Housekeeping and remains visible and usable.
- The destination is not a blank page, browser error, 404 page or indefinitely loading page.
- Navigation checks do not depend on particular records, record counts or backend warning counts.

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
- A table with the column headings `Name`, `Description`, `Usages` and `Status` is visible.
- The shared expectations hold.

### 1.3 Roles navigation

**Steps**

1. Open `/`.
2. Click **Roles** in the primary navigation.

**Expected results**

- The URL path is `/roles`.
- The level-one heading `Roles` is visible.
- The role-link list and `Choose a role from the list to see more details.` are visible.
- The shared expectations hold.
- Do not select a role or verify role details.

### 1.4 Recipes navigation

**Steps**

1. Open `/`.
2. Click **Recipes** in the primary navigation.

**Expected results**

- The URL path is `/recipes`.
- The level-one heading `Recipes` is visible.
- The section headings `Recipes in use` and `Unused recipes` are visible.
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

1. Open `/`.
2. Click **Base images**, then **Roles**, then **Recipes**, then **Housekeeping**, using the primary navigation on each destination.
3. Click the **AMIgo** brand link.

**Expected results**

- Each navigation click reaches the path and visible content specified in scenarios 1.2 to 1.5.
- The primary navigation remains visible and usable throughout.
- Clicking **AMIgo** returns to `/` and displays `¡Hola AMIgo!` again.

## 2. Desktop visual regression

### Controlled capture environment

- Use Chromium in a fixed Linux environment, with pinned Playwright/browser versions and the same installed fonts for baseline creation and comparison.
- Use a `1440 x 900` CSS-pixel viewport, device scale factor `1`, light colour scheme and a fixed locale and timezone.
- Use a pre-provisioned, read-only test dataset or deterministic read-only response fixtures. Keep record ordering and page content stable between runs. Do not modify live application data to stabilise screenshots.
- Start each capture with the page scrolled to the top and the pointer away from navigation links and other interactive controls. No menus or transient hover states should be open.
- Wait for the expected page heading, relevant content and fonts to be ready. Disable animations and hide text carets during capture; do not use arbitrary sleeps.
- Capture the viewport, including the primary navigation and the initial page content. Full-page, mobile and additional viewport baselines are outside this initial scope.
- Prefer stable fixtures over masking. If a genuinely volatile value cannot be stabilised, mask only that value using the same locator in both baseline and comparison captures. Do not mask navigation, headings, table headers, entire content areas or warnings merely to make a comparison pass.

### 2.1 Primary-page visual baselines

Run the following steps independently for each row in the capture matrix.

**Steps**

1. Open `/` in the controlled capture environment.
2. For a navigation destination, click its primary navigation link; for the homepage, remain on `/`.
3. Verify the corresponding expectations in section 1 and wait for the controlled capture conditions above.
4. Capture a viewport screenshot with the specified baseline name.
5. Compare the screenshot with its approved baseline.

| Page | Navigation action | Expected path | Baseline name | Visible appearance covered |
|---|---|---|---|---|
| Homepage | Open `/` | `/` | `home-desktop.png` | Brand, primary navigation, welcome heading, introductory text and links |
| Base images | Click **Base images** | `/base-images` | `base-images-desktop.png` | Navigation, page heading, action area, table headers and initial visible rows |
| Roles | Click **Roles** | `/roles` | `roles-desktop.png` | Navigation, page heading, initial visible role links and instructional content |
| Recipes | Click **Recipes** | `/recipes` | `recipes-desktop.png` | Navigation, page heading, action area, recipe section headings and initial visible listing or empty state |
| Housekeeping | Click **Housekeeping** | `/housekeeping` | `housekeeping-desktop.png` | Navigation, page heading, Orphaned Bakes section and the controlled fixture's warning/action state |

**Expected results**

- Each screenshot matches its approved baseline without unexpected changes to layout, spacing, typography, colours, navigation, content visibility or control appearance.
- Missing, clipped, overlapping or displaced elements fail the comparison.
- Start with no allowed differing pixels in the controlled environment. If reproducible rendering noise requires a tolerance, document and approve a small measured tolerance rather than increasing it to hide a real change.
- A comparison failure retains the expected, actual and difference images for review.
- Screenshot capture must not submit forms or trigger baking, creation, editing or deletion.

### Baseline review and updates

- The initial run creates candidate screenshots for human review; it is not evidence of a passing visual comparison until the baselines are approved.
- Review candidate images to ensure the application is fully rendered and not showing an unexpected error or loading state before accepting them.
- Store approved baselines alongside the eventual visual tests and review them with code changes.
- Update baselines only for intentional, reviewed appearance changes. Do not automatically accept new images after a failure.
- Keep separate baselines if additional browser or operating-system configurations are introduced later.
- A changed dataset or warning state is an environment/fixture issue to investigate, not a reason to silently replace the baseline.

## Exploration observations

Explored at `http://localhost:9000` on 5 October 2026. The homepage, all four primary navigation destinations and the brand-link return path rendered successfully. Visual baselines have not yet been captured or approved.

Housekeeping displayed: `Housekeeping encountered 2 errors when searching the database, continue anyway?`

This warning is an observed backend issue, not a broken navigation link. It does not fail the navigation-only scenarios if the page renders its expected heading and section. Do not assert the warning's presence or exact error count in those scenarios. For visual comparison, use a stable, explicitly reviewed fixture state; do not silently adopt the observed warning as the desired default baseline. Backend health remains outside this plan's scope.

## Out of scope

- Creation and editing forms, including opening new-item forms.
- Table-row detail links and role selection.
- Baking, deletion and other state-changing actions.
- Search, filtering and data correctness.
- Backend health, AWS operations and warning resolution.
- Mobile layouts, full-page screenshots, additional viewport/browser coverage, performance and comprehensive accessibility testing.
