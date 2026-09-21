import { expect, test } from '@playwright/test';

test('authenticated home is ready for exploration', async ({ page }) => {
	await page.goto('/');
	await expect(
		page
			.getByRole('navigation')
			.getByRole('link', { name: 'Recipes', exact: true }),
	).toBeVisible();
	await expect(
		page.getByText('AMIgo is a self-serve AMI bakery.', { exact: true }),
	).toBeVisible();
});
