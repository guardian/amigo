import { expectBaseImages, test } from './support';

test.describe('Basic UI visibility and navigation', () => {
  test('Base images navigation', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('navigation').getByRole('link', { name: 'Base images', exact: true }).click();
    await expectBaseImages(page);
  });
});
