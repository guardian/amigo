import { expect, test as base, type Page } from '@playwright/test';

const test = base.extend({
  page: async ({ page }, use) => {
    await page.goto('/');
    await expectPage(page, '/', '¡Hola AMIgo!');
    await use(page);
  },
});

async function expectPage(page: Page, path: string, heading: string) {
  await expect(page).toHaveURL(path);
  await expect(page.getByRole('heading', { name: heading, exact: true, level: 1 })).toBeVisible();
}

const navLink = (page: Page, name: string) =>
  page.getByRole('navigation').getByRole('link', { name, exact: true });

test('Homepage is visible', async ({ page }) => {
  await expect(page.getByText('AMIgo is a self-serve AMI bakery.', { exact: true })).toBeVisible();
  expect((await page.request.get('/assets/stylesheets/main.css')).ok()).toBe(true);
});

test('Base images navigation', async ({ page }) => {
  await navLink(page, 'Base images').click();
  await expectPage(page, '/base-images', 'Base images');
  await expect(page.getByRole('table')).toBeVisible();
});

test('Roles navigation', async ({ page }) => {
  await navLink(page, 'Roles').click();
  await expectPage(page, '/roles', 'Roles');
  await expect(page.getByText('Choose a role from the list to see more details.', { exact: true })).toBeVisible();
});

test('Recipes navigation', async ({ page }) => {
  await navLink(page, 'Recipes').click();
  await expectPage(page, '/recipes', 'Recipes');
  await expect(page.getByRole('heading', { name: 'Recipes in use', exact: true })).toBeVisible();
});

test('Housekeeping navigation', async ({ page }) => {
  await navLink(page, 'Housekeeping').click();
  await expectPage(page, '/housekeeping', 'Housekeeping');
  await expect(page.getByText('Orphaned Bakes', { exact: true })).toBeVisible();
});

test('Navigation remains usable and returns home', async ({ page }) => {
  await page.goto('/base-images');
  await expectPage(page, '/base-images', 'Base images');
  await navLink(page, 'Roles').click();
  await expectPage(page, '/roles', 'Roles');
  await navLink(page, 'AMIgo').click();
  await expectPage(page, '/', '¡Hola AMIgo!');
});
