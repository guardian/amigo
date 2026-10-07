// spec: specs/basic-ui-test-plan.md
import { expectBaseImages, test } from './support';

test.describe('Basic UI visibility and navigation', () => {
  test('Base images navigation', async ({ page }) => {
    // 1. Open /.
    await page.goto('/');

    // 2. Click Base images in the primary navigation.
    await page.getByRole('navigation').getByRole('link', { name: 'Base images', exact: true }).click();
    await expectBaseImages(page);
  });
});
