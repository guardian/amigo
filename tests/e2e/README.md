# AMIgo end-to-end tests

Implements `specs/basic-ui-test-plan.md` with six navigation and visibility tests. Each scenario has its own test file. Tests are read-only and never submit forms, select roles, bake images or delete records.

## Run

Start AMIgo at `http://localhost:9000`, then run from the repository root:

```bash
npm run test:e2e
```

The root `playwright.config.ts` discovers all Playwright end-to-end tests recursively under `tests/e2e`, including both `*.spec.*` and `*.test.*` files. Keep end-to-end tests in this directory. Shared browser settings are configured centrally, so new end-to-end tests should import `test` from `@playwright/test` without repeating those settings.

The generator seed is `tests/e2e/seed.spec.ts`, alongside the scenario files. It is excluded from the automated suite by `testIgnore` and is only used for Playwright test generation setup.

Set `AMIGO_BASE_URL` to use a different application origin. The suite does not start the application or change its data.

## Coverage and output

The suite checks homepage visibility, each primary navigation destination, one section-to-section transition and the brand-link return home. Each destination has a main heading and one content landmark check. The transition test checks the page shell only, without repeating destination content checks. Assertions cover URLs, the document title and usable navigation, without depending on particular records, counts or table columns.

Visual regression testing is out of scope. Traces, screenshots and videos are not recorded, and diagnostic test output is not retained, including on failure. Playwright's last-run metadata is sent to the operating system's null device, so no `.last-run.json` file is retained. Results are reported in the terminal.
