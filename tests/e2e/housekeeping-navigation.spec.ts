// spec: specs/basic-ui-test-plan.md
import { expectHousekeeping, test } from './support';

test.describe('Basic UI visibility and navigation', () => {
  test('Housekeeping navigation', async ({ page }) => {
    // 1. Open /.
    await page.goto('/');

    // 2. Click Housekeeping in the primary navigation.
    await page.getByRole('navigation').getByRole('link', { name: 'Housekeeping', exact: true }).click();
    await expectHousekeeping(page);
  });
});
