// spec: specs/basic-ui-test-plan.md
// seed: tests/e2e/seed.spec.ts
import { expectRoles, test } from './support';

test.describe('Basic UI visibility and navigation', () => {
  test('Roles navigation', async ({ page }) => {
    // 1. Open /.
    await page.goto('/');

    // 2. Click Roles in the primary navigation.
    await page.getByRole('navigation').getByRole('link', { name: 'Roles', exact: true }).click();
    await expectRoles(page);
  });
});
