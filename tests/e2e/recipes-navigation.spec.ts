// spec: specs/basic-ui-test-plan.md
import { expectRecipes, test } from './support';

test.describe('Basic UI visibility and navigation', () => {
  test('Recipes navigation', async ({ page }) => {
    // 1. Open /.
    await page.goto('/');

    // 2. Click Recipes in the primary navigation.
    await page.getByRole('navigation').getByRole('link', { name: 'Recipes', exact: true }).click();
    await expectRecipes(page);
  });
});
