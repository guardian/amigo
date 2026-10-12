import { expectRoles, test } from './support';

test.describe('Basic UI visibility and navigation', () => {
  test('Roles navigation', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('navigation').getByRole('link', { name: 'Roles', exact: true }).click();
    await expectRoles(page);
  });
});
