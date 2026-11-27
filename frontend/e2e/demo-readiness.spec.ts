import { expect, test, type Page } from '@playwright/test';
import { adminCredentials } from './credentials';

/**
 * Demo readiness sweep, run once per role.
 *
 * Walks every route each account can reach and fails on anything the audience
 * would notice: a console error, a server error, or a screen that renders
 * nothing.
 *
 * Being redirected is not a failure. A non-admin opening an admin screen is
 * supposed to be bounced, so the assertion is about *how* the app responds — it
 * must stay inside the application and keep rendering, rather than falling
 * through to a raw API error page the way /kpi/* used to.
 *
 * The non-admin logins come from tools/seed_role_test_users.sh, which creates
 * one throwaway account per role on the reserved .invalid domain. Run
 * tools/drop_role_test_users.sh to remove them.
 */
const ROUTES = [
  '/dashboard',
  '/profile',
  '/kpi-dashboard',
  '/users',
  '/roles',
  '/teams',
  '/tribes',
  '/portfolios',
  '/squads',
  '/assignments',
  '/organization',
  '/org-chart',
  '/admin/config',
  '/kpi/cycles',
  '/kpi/categories',
  '/kpi/frequencies',
  '/kpi/data-sources',
  '/kpi/masters',
  '/kpi/templates',
  '/kpi/generate',
  '/kpi/my-kpis',
  '/kpi/my-performance',
  '/kpi/review',
  '/kpi/team-flow',
  '/kpi/scorecards',
  '/notifications',
];

const ACCOUNTS: ReadonlyArray<{ role: string; email: string }> = [
  { role: 'EMPLOYEE', email: 'e2e-employee@example.invalid' },
  { role: 'MANAGER', email: 'e2e-manager@example.invalid' },
  { role: 'TRIBE LEAD', email: 'e2e-tribe-lead@example.invalid' },
  { role: 'PORTFOLIO LEAD', email: 'e2e-portfolio-lead@example.invalid' },
  { role: 'SQUAD LEAD', email: 'e2e-squad-lead@example.invalid' },
  { role: 'TECH FOCAL', email: 'e2e-tech-focal@example.invalid' },
  { role: 'QA TECH FOCAL', email: 'e2e-qa-tech-focal@example.invalid' },
  { role: 'SCRUM MASTER TECH FOCAL', email: 'e2e-scrum-master-tech-focal@example.invalid' },
  { role: 'SCRUM MASTER', email: 'e2e-scrum-master@example.invalid' },
  { role: 'SENIOR DEVELOPER', email: 'e2e-senior-developer@example.invalid' },
  { role: 'MID DEVELOPER', email: 'e2e-mid-developer@example.invalid' },
  { role: 'JUNIOR DEVELOPER', email: 'e2e-junior-developer@example.invalid' },
  { role: 'QA ENGINEER', email: 'e2e-qa-engineer@example.invalid' },
];

async function signIn(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.locator('input[formcontrolname="email"]').fill(email);
  await page.locator('input[formcontrolname="password"]').fill(password);
  await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15_000 });
}

for (const { role, email } of [{ role: 'ADMIN', email: '' }, ...ACCOUNTS]) {
  test(`every screen behaves for ${role}`, async ({ page }) => {
    test.setTimeout(180_000);

    // The per-role accounts are throwaway fixtures, and they are deliberately
    // not left in the database — they would otherwise show up in the Users
    // screen during a demo. Skip rather than fail when they are absent, so the
    // suite stays honest without them.
    const admin = adminCredentials();
    const accountEmail = role === 'ADMIN' ? admin.email : email;
    const credentials = { email: accountEmail, password: admin.password };
    const probe = await page.request.post('/api/auth/login', { data: credentials, failOnStatusCode: false });
    if (role === 'ADMIN') {
      expect(probe.status(), 'administrator credentials must work').toBe(200);
    }
    test.skip(
      probe.status() !== 200,
      `no login for ${role} — run tools/seed_role_test_users.sh to include this role`
    );

    const problems: string[] = [];
    let current = '';

    // The browser logs its own "Failed to load resource" line for every 401/403.
    // Those mirror responses this sweep deliberately allows — a screen may ask
    // about data the role cannot see — so counting them here would contradict
    // the response rule below. Genuine script errors still come through.
    const isDeniedResourceNoise = (text: string) =>
        /Failed to load resource/.test(text) && /\b(401|403)\b/.test(text);

    page.on('console', (message) => {
      if (message.type() === 'error' && !isDeniedResourceNoise(message.text())) {
        problems.push(`${current} — console: ${message.text()}`);
      }
    });
    page.on('pageerror', (error) => {
      problems.push(`${current} — uncaught: ${error.message}`);
    });
    page.on('response', (response) => {
      // 401/403 mean the app asked about something this role cannot see, which
      // it is entitled to do. A 400 or a 5xx is the server failing to answer a
      // question it should have understood — that is the bug class here.
      const status = response.status();
      const url = response.url();
      if (url.includes('/api/') && (status >= 500 || status === 400)) {
        problems.push(`${current} — ${status} ${new URL(url).pathname}`);
      }
    });

    current = '/login';
    await signIn(page, accountEmail, admin.password);

    for (const route of ROUTES) {
      current = route;
      await page.goto(route);

      // Either the route was allowed, or a guard moved the user elsewhere in
      // the app. What must never happen is landing outside the app.
      await expect(page.locator('app-root'), `${route} stayed inside the app`).toBeAttached();

      const main = page.locator('main');
      await expect
        .poll(async () => (await main.innerText()).trim().length, {
          message: `${route} rendered content`,
          timeout: 10_000,
        })
        .toBeGreaterThan(0);

      // Rendering *something* is not enough: a placeholder like "Loading
      // roles..." is text too, which is how a frozen screen slipped past here.
      //

      // Nothing is clicked or hovered anywhere in this loop, and that is the
      // point. Change detection has to run off the HTTP response alone; when it
      // does not, the data is there but the view still says "Loading..." until
      // some unrelated interaction happens to schedule a render. Waiting for the
      // spinner to clear on its own is what distinguishes the two.
      await expect
        .poll(async () => await main.getByText(/^\s*Loading\b/i).count(), {
          message: `${route} finished loading without needing a click`,
          timeout: 15_000,
        })
        .toBe(0);

      // Whatever the app does about a permission it lacks, it must not shout
      // about it on screen — an error toast mid-demo is the visible failure
      // that all of the above is really standing in for.
      const alerts = page.locator('app-toast [role="alert"]');
      if (await alerts.count()) {
        problems.push(`${route} — visible alert: ${(await alerts.first().innerText()).trim()}`);
      }
    }

    expect(problems, `\n${problems.join('\n')}\n`).toEqual([]);
  });
}
