// spec: specs/basic-ui-test-plan.md
// seed: tests/e2e/seed.spec.ts
import { expect } from '@playwright/test';
import { expectPage, test } from './support';

test.describe('Basic UI visibility and navigation', () => {
  test('Homepage is visible', async ({ page }) => {
    // 1. Open http://localhost:9000/.
    await page.goto('/');
    const stylesheet = await page.request.get('/assets/stylesheets/main.css');
    expect(stylesheet.status()).toBe(200);
    expect(stylesheet.headers()['content-type']).toMatch(/^text\/css(?:;|$)/);
    await expectPage(page, '/', '¡Hola AMIgo!');
    await expect(page.getByText('AMIgo is a self-serve AMI bakery.', { exact: true })).toBeVisible();
  });
});
