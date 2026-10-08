import { expectHousekeeping, test } from './support';

test.describe('Basic UI visibility and navigation', () => {
  test('Housekeeping navigation', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('navigation').getByRole('link', { name: 'Housekeeping', exact: true }).click();
    await expectHousekeeping(page);
  });
});
