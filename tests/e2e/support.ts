import { expect, type Page } from '@playwright/test';

export { test } from './auth/fixtures';

const navigationLinks = [
  ['AMIgo', '/'],
  ['Base images', '/base-images'],
  ['Roles', '/roles'],
  ['Recipes', '/recipes'],
  ['Housekeeping', '/housekeeping'],
] as const;

export async function expectPage(page: Page, path: string, heading: string) {
  await expect(page).toHaveURL(url => url.pathname === path
    && url.origin === `http://127.0.0.1:${process.env.E2E_PORT ?? '9100'}`);
  await expect(page).toHaveTitle('AMIgo');
  await expect(page.getByRole('heading', { name: heading, exact: true, level: 1 })).toBeVisible();
  const navigation = page.getByRole('navigation');
  await expect(navigation).toBeVisible();
  for (const [name, href] of navigationLinks) {
    const link = navigation.getByRole('link', { name, exact: true });
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute('href', href);
  }
}

export async function expectBaseImages(page: Page) {
  await expectPage(page, '/base-images', 'Base images');
  await expect(page.getByRole('table')).toBeVisible();
}

export async function expectRoles(page: Page) {
  await expectPage(page, '/roles', 'Roles');
  await expect(page.getByText('Choose a role from the list to see more details.', { exact: true })).toBeVisible();
}

export async function expectRecipes(page: Page) {
  await expectPage(page, '/recipes', 'Recipes');
  await expect(page.getByRole('heading', { name: 'Recipes in use', exact: true })).toBeVisible();
}

export async function expectHousekeeping(page: Page) {
  await expectPage(page, '/housekeeping', 'Housekeeping');
  await expect(page.getByText('Orphaned Bakes', { exact: true })).toBeVisible();
}
