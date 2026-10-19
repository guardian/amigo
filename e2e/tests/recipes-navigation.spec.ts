import { expectRecipes, test } from './support';

test.describe('Basic UI visibility and navigation', () => {
  test('Recipes navigation', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('navigation').getByRole('link', { name: 'Recipes', exact: true }).click();
    await expectRecipes(page);
  });
});
