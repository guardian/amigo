import { expect, test as base, type APIResponse } from '@playwright/test';
import { readServerLogTail } from './server-log.mjs';

type Outcome = 'success' | 'no-groups' | 'unrelated-group' | 'wrong-domain'
  | 'expired' | 'token-error' | 'userinfo-error' | 'groups-error'
  | 'token-malformed' | 'userinfo-malformed' | 'groups-malformed';
type AuthScenario = { outcome?: Outcome; group?: string };
type Mock = {
  id: string;
  authenticateViaAPI: (path?: string, state?: 'missing' | 'invalid') => Promise<APIResponse>;
};
type Fixtures = {
  authScenario: AuthScenario;
  expectedAuthStages: string[];
  expectBrowserLogin: boolean;
  authMock: Mock;
};

const mockURL = `http://127.0.0.1:${process.env.E2E_PROXY_PORT ?? '9101'}`;

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function scenarioId(value: unknown): string {
  if (!isRecord(value) || typeof value.id !== 'string') {
    throw new Error('Authentication mock returned an invalid scenario');
  }
  return value.id;
}

function journal(value: unknown): { stages: string[]; unexpected: unknown[] } {
  if (!isRecord(value) || !Array.isArray(value.events) || !Array.isArray(value.unexpected)) {
    throw new Error('Authentication mock returned an invalid request journal');
  }
  return {
    stages: value.events.map((event: unknown) => {
      if (!isRecord(event) || typeof event.stage !== 'string') {
        throw new Error('Authentication mock returned an invalid event');
      }
      return event.stage;
    }),
    unexpected: value.unexpected,
  };
}

function callbackURL(authorization: URL, baseURL: string, id: string): URL {
  expect(authorization.origin).toBe('https://accounts.google.com');
  expect(authorization.pathname).toBe('/o/oauth2/v2/auth');
  const params = authorization.searchParams;
  expect(params.get('client_id')).toBe('e2e-client');
  expect(params.get('response_type')).toBe('code');
  expect(params.get('scope')).toBe('openid email profile');
  expect(params.get('hd')).toBe('guardian.co.uk');
  expect(params.get('max_auth_age')).toBe(String(90 * 24 * 60 * 60));
  expect(params.get('redirect_uri')).toBe(`${baseURL}/oauth2callback`);
  const state = params.get('state');
  if (!state) throw new Error('Google authorisation redirect is missing state');
  const callback = new URL('/oauth2callback', baseURL);
  callback.searchParams.set('code', id);
  callback.searchParams.set('state', state);
  return callback;
}

export const test = base.extend<Fixtures>({
  authScenario: [{ outcome: 'success' }, { option: true }],
  expectedAuthStages: [['token', 'userinfo', 'groups'], { option: true }],
  expectBrowserLogin: [true, { option: true }],
  authMock: [async ({
    context, request, baseURL, authScenario, expectedAuthStages, expectBrowserLogin,
  }, use, testInfo) => {
    if (!baseURL) throw new Error('The mocked-auth tests require baseURL');
    const registered = await request.post(`${mockURL}/scenarios`, { data: authScenario });
    await expect(registered).toBeOK();
    const id = scenarioId(await registered.json());
    const callbacks: number[] = [];
    let authorizations = 0;
    context.on('response', response => {
      if (new URL(response.url()).pathname === '/oauth2callback') {
        callbacks.push(response.status());
      }
    });
    // Fetch the initial redirect without following it: redirected URLs do not
    // reliably re-enter Playwright's URL-filtered routing.
    await context.route(`${baseURL}/**`, async route => {
      if (authorizations > 0 || !route.request().isNavigationRequest()) {
        await route.continue();
        return;
      }
      const protectedResponse = await route.fetch({ maxRedirects: 0 });
      expect(protectedResponse.status()).toBe(303);
      expect(protectedResponse.headers().location).toBe('/login');
      const login = await context.request.get(`${baseURL}/login`, { maxRedirects: 0 });
      expect(login.status()).toBe(303);
      const callback = callbackURL(new URL(login.headers().location), baseURL, id);
      authorizations++;
      await route.fulfill({
        response: login,
        headers: { ...login.headers(), location: callback.toString() },
      });
    });
    await context.route(
      /^https:\/\/(?:accounts\.google\.com|(?:[a-z]+\.)?googleapis\.com)\//,
      async route => {
        await route.abort('blockedbyclient');
        throw new Error('Unexpected browser request to the live Google provider');
      },
    );
    try {
      await use({
        id,
        async authenticateViaAPI(path = '/base-images', state) {
          const protectedResponse = await request.get(`${baseURL}${path}`, { maxRedirects: 0 });
          expect(protectedResponse.status()).toBe(303);
          expect(protectedResponse.headers().location).toBe('/login');
          const login = await request.get(`${baseURL}/login`, { maxRedirects: 0 });
          expect(login.status()).toBe(303);
          const callback = callbackURL(new URL(login.headers().location), baseURL, id);
          if (state === 'missing') callback.searchParams.delete('state');
          if (state === 'invalid') callback.searchParams.set('state', 'invalid-e2e-state');
          return request.get(callback.toString(), { maxRedirects: 0 });
        },
      });
      const response = await request.get(`${mockURL}/scenarios/${id}`);
      await expect(response).toBeOK();
      const events = journal(await response.json());
      await testInfo.attach('backend-authentication-requests', {
        body: JSON.stringify(events, null, 2), contentType: 'application/json',
      });
      expect(events.unexpected, 'Unexpected request reached the authentication proxy').toEqual([]);
      expect(events.stages).toEqual(expectedAuthStages);
      if (expectBrowserLogin) {
        expect(authorizations, 'Each UI test must initiate a real login').toBe(1);
        expect(callbacks, 'Each UI test must complete the real callback once').toEqual([303]);
      }
    } finally {
      if (testInfo.status !== testInfo.expectedStatus) {
        const log = readServerLogTail();
        if (log !== undefined) {
          await testInfo.attach('e2e-server-log', { body: log, contentType: 'text/plain' });
        }
      }
      const removed = await request.delete(`${mockURL}/scenarios/${id}`);
      await expect(removed).toBeOK();
    }
  }, { auto: true }],
});
