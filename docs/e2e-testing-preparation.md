# End-to-end UI testing preparation

## Agreed outcome

A Chromium-only Playwright suite will test representative read-only UI journeys using synthetic fixtures. It will run with the same command in the development container and GitHub Actions, without AWS credentials, external network access, or mutable production services.

## Preparation work list

- [x] **Create an isolated application entry point**
  - Add a dedicated E2E `ApplicationLoader` and component graph.
  - Do not subclass production components.
  - Bypass SSM configuration loading.
  - Never construct AWS, Prism, Google OAuth, or Quartz clients.
  - Require an explicit E2E marker and reject unknown fixture scenarios.

- [ ] **Introduce dependency boundaries**
  - Extract narrow interfaces from controllers that directly depend on DynamoDB, Prism, S3, and schedulers.
  - Cover only home, base images, roles, recipes, usage, and their detail pages.
  - Retain production adapters unchanged behind those interfaces.
  - Supply harmless fakes for destructive services while leaving their controls visible.

- [ ] **Build deterministic fixture scenarios**
  - Add immutable `empty` and `populated` scenarios.
  - Use synthetic records only.
  - Include one base image, several roles, one used recipe, and one unused recipe.
  - Use a fixed application clock.
  - Select the scenario once through `AMIGO_E2E_SCENARIO`.
  - Run each scenario in a separate server process.

- [ ] **Provide authenticated sessions**
  - Add a small JVM helper using the real rotating Play session-cookie baker.
  - Share a fixed, non-sensitive E2E signing key with the isolated application.
  - Have Playwright inject the generated cookie.
  - Preserve the real `AuthAction` and include one unauthenticated redirect check.

- [ ] **Remove browser network dependencies**
  - Serve Bootstrap, jQuery, and Showdown locally instead of using public CDNs.
  - Fail tests on any non-localhost request.
  - Fail on uncaught browser errors and unexpected failed local requests.

- [ ] **Make the UI semantically testable**
  - Verify headings, labels, links, tabs, tables, and controls have stable accessible names.
  - Improve semantic markup where necessary.
  - Do not add `data-testid` attributes.

- [ ] **Scaffold root-level Playwright tooling**
  - Create a separate root Node package and lockfile.
  - Install Playwright and Chromium.
  - Configure a fixed desktop viewport.
  - Use one worker per scenario and no initial retries.
  - Capture traces for every run and screenshots on failure.
  - Add commands for populated, empty, and complete runs.

- [ ] **Support process orchestration**
  - Let Playwright start the E2E application and wait for `/healthcheck`.
  - Ensure application and browser processes are cleaned up after success or failure.
  - Keep local and CI commands identical.
  - Document required Node, Java, and browser installation steps.

- [ ] **Add GitHub Actions support**
  - Start as a non-required job in `.github/workflows/ci.yaml`.
  - Provide no AWS credentials.
  - Install Chromium and its system dependencies.
  - Upload all traces and failed screenshots for 14 days.
  - Cache sbt, Node, and Playwright downloads where appropriate.

- [ ] **Document the initial route contract**
  - Include authentication, home, navigation, base images, roles, recipes, and recipe usage.
  - Defer bake detail.
  - Exclude housekeeping and every create, edit, clone, delete, or bake route.
  - Record that destructive controls may render but must never be activated.

## Ready-to-build gate

Begin writing journeys once the isolated application:

- starts in both the development container and GitHub Actions;
- serves both fixture scenarios;
- accepts generated authentication cookies;
- performs no external requests; and
- cannot construct production integrations.

## Required-check promotion

Make the E2E job a required pull-request check after:

- 20 consecutive clean CI runs;
- a median runtime below 10 minutes;
- confirmation that it needs no AWS credentials or external network access;
- reliable trace upload for every run, with screenshots on failure; and
- documented local reproduction using the same command as CI.