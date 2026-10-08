import { expect, test as base, type Page } from '@playwright/test';

export const test = base.extend({
  page: async ({ page }, use) => {
    await page.goto('/');
    await expect(page).toHaveURL(url =>
      url.hostname === 'localhost' && url.pathname === '/google/authorize');
    const authorization = new URL(page.url());
    expect(authorization.searchParams.get('client_id')).toBe('amigo-e2e');
    expect(authorization.searchParams.get('redirect_uri')).toBe('http://localhost:9000/oauth2callback');
    expect(authorization.searchParams.get('state')).toBeTruthy();
    await expect(page.getByRole('heading', { name: 'Mock OAuth2 Server Sign-in' })).toBeVisible();
    await page.getByPlaceholder('Enter any user/subject').fill('navigation');
    const callback = page.waitForRequest(request => new URL(request.url()).pathname === '/oauth2callback');
    await page.getByRole('button', { name: 'Sign-in', exact: true }).click();
    const callbackURL = new URL((await callback).url());
    expect(callbackURL.searchParams.get('code')).toBeTruthy();
    expect(callbackURL.searchParams.get('state')).toBe(authorization.searchParams.get('state'));
    await expectPage(page, '/', '¡Hola AMIgo!');
    await use(page);
  },
});

const navigationLinks = [
  ['AMIgo', '/'],
  ['Base images', '/base-images'],
  ['Roles', '/roles'],
  ['Recipes', '/recipes'],
  ['Housekeeping', '/housekeeping'],
] as const;

export async function expectPage(page: Page, path: string, heading: string) {
  await expect(page).toHaveURL(url => url.origin === 'http://localhost:9000' && url.pathname === path);
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
