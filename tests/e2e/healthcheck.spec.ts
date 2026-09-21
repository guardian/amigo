import { expect, test } from '@playwright/test';

test.use({ storageState: { cookies: [], origins: [] } });

test('healthcheck exposes build metadata without authentication', async ({
	request,
}) => {
	const response = await request.get('/healthcheck', { maxRedirects: 0 });

	expect(response.status()).toBe(200);
	expect(response.headers()['content-type']).toContain('application/json');
	const metadata: unknown = await response.json();
	expect(metadata).toEqual(
		expect.objectContaining({ buildTime: expect.any(String) }),
	);
	expect(metadata).toHaveProperty('buildNumber');
	expect(metadata).toHaveProperty('gitCommitId');
});
