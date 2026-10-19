import { expectPage, test } from './support';

test.describe('Basic UI visibility and navigation', () => {
  test('Navigation remains usable and returns home', async ({ page }) => {
    await page.goto('/base-images');
    await expectPage(page, '/base-images', 'Base images');
    const navigation = page.getByRole('navigation');
    await navigation.getByRole('link', { name: 'Roles', exact: true }).click();
    await expectPage(page, '/roles', 'Roles');
    await navigation.getByRole('link', { name: 'AMIgo', exact: true }).click();
    await expectPage(page, '/', '¡Hola AMIgo!');
  });
});
