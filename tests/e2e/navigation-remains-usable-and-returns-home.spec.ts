// spec: specs/mocked-google-auth-test-plan.md
import { expectPage, test } from './support';

test.describe('Basic UI visibility and navigation', () => {
  test('Navigation remains usable and returns home', async ({ page }) => {
    // 1. Open /base-images.
    await page.goto('/base-images');
    await expectPage(page, '/base-images', 'Base images');
    const navigation = page.getByRole('navigation');

    // 2. Click Roles in the primary navigation.
    await navigation.getByRole('link', { name: 'Roles', exact: true }).click();
    await expectPage(page, '/roles', 'Roles');

    // 3. Click the AMIgo brand link.
    await navigation.getByRole('link', { name: 'AMIgo', exact: true }).click();
    await expectPage(page, '/', '¡Hola AMIgo!');
  });
});
