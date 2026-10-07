import { expect } from '@playwright/test';
import { test } from './fixtures';

const proxyURL = `http://127.0.0.1:${process.env.E2E_PROXY_PORT ?? '9101'}`;

test.describe('Unauthenticated protection', () => {
  test.use({ expectBrowserLogin: false, expectedAuthStages: [] });

  for (const path of ['/', '/base-images', '/roles', '/recipes', '/housekeeping']) {
    test(`${path} requires authentication`, async ({ request }) => {
      const response = await request.get(path, { maxRedirects: 0 });
      expect(response.status()).toBe(303);
      expect(response.headers().location).toBe('/login');
    });
  }
});

test('session survives reload without another login', async ({ page, request }) => {
  await page.goto('/base-images');
  await expect(page.getByRole('heading', { name: 'Base images', exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Base images', exact: true })).toBeVisible();
  const status = await request.get(`${proxyURL}/status`);
  await expect(status).toBeOK();
  const body: unknown = await status.json();
  expect(body).toMatchObject({
    discovery: expect.any(Number), serviceTokens: expect.any(Number), unexpected: [],
  });
  if (body === null || typeof body !== 'object'
    || !('discovery' in body) || typeof body.discovery !== 'number'
    || !('serviceTokens' in body) || typeof body.serviceTokens !== 'number') {
    throw new Error('Invalid authentication proxy status');
  }
  expect(body.discovery).toBeGreaterThan(0);
  expect(body.serviceTokens).toBeGreaterThan(0);
});

for (const group of [
  'e2e-department@guardian.co.uk',
  'e2e-data@guardian.co.uk',
  'e2e-multimedia@guardian.co.uk',
]) {
  test.describe(`Any-group authorisation: ${group}`, () => {
    test.use({ authScenario: { group }, expectBrowserLogin: false });

    test('membership of this group alone authorises the deep link', async ({ authMock, request }) => {
      const callback = await authMock.authenticateViaAPI();
      expect(callback.status()).toBe(303);
      expect(callback.headers().location).toBe('/base-images');
      const response = await request.get('/base-images', { maxRedirects: 0 });
      expect(response.status()).toBe(200);
    });
  });
}

for (const state of ['missing', 'invalid'] as const) {
  test.describe(`Callback state: ${state}`, () => {
    test.use({ expectBrowserLogin: false, expectedAuthStages: [] });

    test('rejects the callback before exchanging the code', async ({ authMock, request }) => {
      const callback = await authMock.authenticateViaAPI('/base-images', state);
      expect(callback.status()).toBe(303);
      expect(callback.headers().location).toBe('/login');
      expect(callback.headers()['set-cookie']).toMatch(/PLAY_FLASH=[^;\s]+/);
      const protectedPage = await request.get('/base-images', { maxRedirects: 0 });
      expect(protectedPage.status()).toBe(303);
      expect(protectedPage.headers().location).toBe('/login');
    });
  });
}

test.describe('Recovery after rejected state', () => {
  test.use({ expectBrowserLogin: false });

  test('a subsequent valid login succeeds without inheriting the failure', async ({ authMock, request }) => {
    const rejected = await authMock.authenticateViaAPI('/base-images', 'invalid');
    expect(rejected.headers().location).toBe('/login');
    const accepted = await authMock.authenticateViaAPI();
    expect(accepted.status()).toBe(303);
    expect(accepted.headers().location).toBe('/base-images');
    const protectedPage = await request.get('/base-images', { maxRedirects: 0 });
    expect(protectedPage.status()).toBe(200);
  });
});

const failures = [
  { outcome: 'no-groups', stages: ['token', 'userinfo', 'groups'] },
  { outcome: 'unrelated-group', stages: ['token', 'userinfo', 'groups'] },
  { outcome: 'wrong-domain', stages: ['token'] },
  { outcome: 'expired', stages: ['token', 'userinfo', 'groups'] },
  { outcome: 'token-error', stages: ['token'] },
  { outcome: 'userinfo-error', stages: ['token', 'userinfo'] },
  { outcome: 'groups-error', stages: ['token', 'userinfo', 'groups'] },
  { outcome: 'token-malformed', stages: ['token'] },
  { outcome: 'userinfo-malformed', stages: ['token', 'userinfo'] },
  { outcome: 'groups-malformed', stages: ['token', 'userinfo', 'groups'] },
] as const;

for (const { outcome, stages } of failures) {
  test.describe(`Authentication rejection: ${outcome}`, () => {
    test.use({
      authScenario: { outcome }, expectedAuthStages: [...stages], expectBrowserLogin: false,
    });

    test('does not grant access to a protected page', async ({ authMock, request }) => {
      const callback = await authMock.authenticateViaAPI();
      expect(callback.status()).toBe(303);
      expect(callback.headers().location).toBe(outcome === 'expired' ? '/base-images' : '/login');
      if (outcome !== 'expired') {
        expect(callback.headers()['set-cookie']).toMatch(/PLAY_FLASH=[^;\s]+/);
      }
      const protectedPage = await request.get('/base-images', { maxRedirects: 0 });
      expect(protectedPage.status()).toBe(303);
      expect(protectedPage.headers().location).toBe('/login');
    });
  });
}
