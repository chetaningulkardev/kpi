import { expect, test } from '@playwright/test';
import { adminCredentials } from './credentials';

test('org hierarchy pages render without auth errors', async ({ page }) => {
  // Login first
  await page.goto('/login');
  await expect(page).toHaveURL(/\/login/);

  const { email, password } = adminCredentials();
  await page.locator('input[formcontrolname="email"]').fill(email);
  await page.locator('input[formcontrolname="password"]').fill(password);
  await page.getByRole('button', { name: 'Sign In' }).click();
  await expect(page).toHaveURL(/\/dashboard/);

  // Navigate to Tribes
  await page.getByRole('button', { name: 'Organization ▼' }).click();
  await page.getByRole('link', { name: 'Tribes' }).click();
  await expect(page).toHaveURL(/\/tribes/);
  await expect(page.getByText('Tribe Management')).toBeVisible();
  await expect(page.getByRole('button', { name: /Add Tribe/i })).toBeVisible();

  // Navigate to Portfolios
  await page.getByRole('button', { name: 'Organization ▼' }).click();
  await page.getByRole('link', { name: 'Portfolios' }).click();
  await expect(page).toHaveURL(/\/portfolios/);
  await expect(page.getByText('Portfolio Management')).toBeVisible();
  await expect(page.getByRole('button', { name: /Add Portfolio/i })).toBeVisible();

  // Navigate to Squads
  await page.getByRole('button', { name: 'Organization ▼' }).click();
  await page.getByRole('link', { name: 'Squads' }).click();
  await expect(page).toHaveURL(/\/squads/);
  await expect(page.getByText('Squad Management')).toBeVisible();
  await expect(page.getByRole('button', { name: /Add Squad/i })).toBeVisible();

  // Navigate to Teams
  await page.getByRole('button', { name: 'Organization ▼' }).click();
  await page.getByRole('link', { name: 'Teams' }).click();
  await expect(page).toHaveURL(/\/teams/);
  await expect(page.getByText('Team Management')).toBeVisible();

  // Navigate to Assignments
  await page.getByRole('button', { name: 'Organization ▼' }).click();
  await page.getByRole('link', { name: 'Assignments' }).click();
  await expect(page).toHaveURL(/\/assignments/);
  await expect(page.getByText('Assignment Management')).toBeVisible();

  // Navigate to Organization Explorer
  await page.getByRole('button', { name: 'Organization ▼' }).click();
  await page.getByRole('link', { name: 'Org Explorer' }).click();
  await expect(page).toHaveURL(/\/organization/);
  await expect(page.getByRole('heading', { name: 'Organization Explorer' })).toBeVisible();

  // Navigate to Org Chart
  await page.getByRole('button', { name: 'Organization ▼' }).click();
  await page.getByRole('link', { name: 'Org Chart' }).click();
  await expect(page).toHaveURL(/\/org-chart/);
  await expect(page.getByRole('heading', { name: /Org Chart|Organization Chart/i })).toBeVisible();
});
