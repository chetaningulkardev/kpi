import { expect, test } from '@playwright/test';
import { adminCredentials } from './credentials';

test('login and navigate core modules', async ({ page }) => {
  await page.goto('/login');
  await expect(page).toHaveURL(/\/login/);

  const { email, password } = adminCredentials();
  await page.locator('input[formcontrolname="email"]').fill(email);
  await page.locator('input[formcontrolname="password"]').fill(password);
  await page.getByRole('button', { name: 'Sign In' }).click();

  await expect(page).toHaveURL(/\/dashboard/);
  // The hero greeting is split: "Welcome back" is a label above the heading,
  // which carries the user's name on its own.
  await expect(page.getByText('Welcome back')).toBeVisible();
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

  // Each of these also proves the dropdown panel actually paints — an
  // `overflow` on the nav once clipped them away while still reporting open.
  await page.getByRole('button', { name: 'Organization ▼' }).click();
  await expect(page.getByRole('link', { name: 'Tribes' })).toBeVisible();
  await page.getByRole('link', { name: 'Tribes' }).click();
  await expect(page).toHaveURL(/\/tribes/);

  await page.getByRole('button', { name: 'Administration ▼' }).click();
  await expect(page.getByRole('link', { name: 'Users' })).toBeVisible();
  await page.getByRole('link', { name: 'Users' }).click();
  await expect(page).toHaveURL(/\/users/);

  await page.getByRole('button', { name: 'Performance ▼' }).click();
  await expect(page.getByRole('link', { name: 'KPI Dashboard' })).toBeVisible();
  await page.getByRole('link', { name: 'KPI Dashboard' }).click();
  await expect(page).toHaveURL(/\/kpi-dashboard/);
});
