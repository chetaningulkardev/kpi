import { expect, test, type Page } from '@playwright/test';

const publicRoutes = ['/dashboard', '/profile', '/kpi-dashboard', '/kpi/my-kpis', '/kpi/my-performance', '/notifications'];
const adminRoutes = ['/users', '/roles', '/teams', '/tribes', '/portfolios', '/squads', '/assignments', '/organization', '/org-chart', '/admin/config',
  '/kpi/cycles', '/kpi/categories', '/kpi/frequencies', '/kpi/data-sources', '/kpi/masters', '/kpi/templates', '/kpi/generate'];
const reviewerRoutes = ['/kpi/review', '/kpi/team-flow'];
const allRoutes = ['/login', ...publicRoutes, ...adminRoutes, ...reviewerRoutes, '/kpi/scorecards'];
type Role = 'EMPLOYEE' | 'MANAGER' | 'SQUAD_LEAD' | 'ADMIN';
const cycle = { id: 1, cycleCode: 'AUDIT', cycleName: 'Audit review cycle', startDate: '2026-01-01', endDate: '2026-12-31', status: 'OPEN' };
const kpi = { id: 1, kpiName: 'Delivery quality', kpiCode: 'QUALITY', categoryName: 'Quality', weight: 10, target: '90', actualValue: 92, status: 'PENDING',
  user: { id: 1, firstName: 'Audit', lastName: 'Employee', email: 'audit@example.invalid' } };
const card = { id: 1, userId: 1, employeeName: 'Audit Employee', role: 'EMPLOYEE', employeeLevel: 'MID', overallScore: 4, maxScore: 5, scorePercentage: 80,
  categoryScores: { Quality: 4 }, status: 'PUBLISHED', cycleCode: 'AUDIT', cycleName: 'Audit review cycle', cycleStartDate: '2026-01-01', cycleEndDate: '2026-12-31' };

async function mockApp(page: Page, role: Role = 'ADMIN', empty = false, failure = '', delay = '') {
  const user = { id: 1, first_name: 'Audit', last_name: 'User', email: 'audit@example.invalid', active: true, roles: [{ id: 1, role_name: role }], teams: [] };
  const token = `e30.${Buffer.from(JSON.stringify({ exp: 4102444800 })).toString('base64url')}.audit`;
  let currentKpi = { ...kpi };
  let pending = true;
  const cycles = empty ? [] : [cycle];
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === delay) await new Promise(resolve => setTimeout(resolve, 800));
    if (path === failure) { await route.fulfill({ status: 503, json: { message: 'Audit unavailable' } }); return; }
    let body: any = [];
    if (path === '/api/auth/login') body = { data: { access_token: token, refresh_token: token, user } };
    else if (path === '/api/auth/validate') body = { data: true };
    else if (path === '/api/auth/me') body = { data: user };
    else if (path === '/api/me/permissions') body = { userId: 1, roles: [role], admin: role === 'ADMIN', canReviewKpis: role !== 'EMPLOYEE',
      canViewTeamFlow: role !== 'EMPLOYEE', canManageScorecards: ['ADMIN', 'MANAGER'].includes(role), reviewScope: role === 'EMPLOYEE' ? 'SELF' : 'SQUAD' };
    else if (path === '/api/auth/demo-credentials') body = { data: [] };
    else if (path === '/api/notifications/unread-count') body = { unreadCount: empty ? 0 : 1 };
    else if (path === '/api/notifications/types') body = ['KPI_APPROVED'];
    else if (path === '/api/notifications') body = { items: empty ? [] : [{ id: 1, title: 'Review update', message: 'Your KPI has been approved', type: 'KPI_APPROVED', read: false, createdAt: '2026-10-07T08:00:00Z' }],
      page: 0, size: 20, totalItems: empty ? 0 : 1, totalPages: empty ? 0 : 1 };
    else if (path === '/api/config/review-cycles' && route.request().method() === 'POST') {
      body = { id: 2, ...route.request().postDataJSON() };
      cycles.push(body);
    }
    else if (path.startsWith('/api/config/review-cycles')) body = cycles;
    else if (path === '/api/kpi/my-kpis') body = empty ? [] : [currentKpi];
    else if (path === '/api/kpi/review/pending-reviews') body = empty || !pending ? [] : [{ ...kpi, status: 'SUBMITTED' }];
    else if (path === '/api/kpi/review/team-assignment-flow') body = empty ? [] : [{
      transitionKey: 'EMPLOYEE_MANAGER', fromRole: { roleCode: 'EMPLOYEE', roleName: 'Employee' }, toRole: { roleCode: 'MANAGER', roleName: 'Manager' },
      pendingUsers: 1, pendingKpis: 1, requests: [{ userId: 1, employeeName: 'Audit Employee', email: 'audit@example.invalid', pendingKpis: 1, cycleCodes: 'AUDIT', statuses: 'SUBMITTED' }]
    }];
    else if (path.startsWith('/api/kpi/self-assess/')) body = currentKpi = { ...kpi, ...route.request().postDataJSON(), status: 'SUBMITTED' };
    else if (path.startsWith('/api/kpi/review/manager-review/')) { pending = false; body = { ...kpi, status: 'APPROVED' }; }
    else if (path === '/api/kpi/scorecards/summary') body = { totalEmployees: 1, averageScore: 4, minScore: 4, maxScore: 4, statusDistribution: { PUBLISHED: 1 }, ratingDistribution: { '4': 1 } };
    else if (path === '/api/kpi/scorecards' || path === '/api/kpi/scorecards/me/history') body = empty ? [] : [card];
    else if (path === '/api/kpi/scorecards/me') body = card;
    else if (path.endsWith('/calibrate')) body = { ...card, ...route.request().postDataJSON() };
    else if (path === '/api/assignments/stats') body = { totalActiveUsers: empty ? 0 : 1, rolesWithActiveTemplate: empty ? 0 : 1,
      roleStats: empty ? [] : [{ roleId: 1, roleCode: 'EMPLOYEE', roleName: 'Employee', userCount: 1, totalTemplates: 1, activeTemplates: 1, readyToGenerate: true, templateNames: ['Audit template'] }],
      cycleStats: empty ? [] : [{ cycleId: 1, cycleCode: 'AUDIT', cycleName: 'Audit review cycle', status: 'OPEN', kpisGenerated: false, kpiCount: 1, startDate: cycle.startDate, endDate: cycle.endDate }] };
    else if (path === '/api/assignments/readiness-by-role') body = { roleCode: 'EMPLOYEE', cycleCode: 'AUDIT', ready: true, cycleEligible: true, activeUsersInRole: 1, reasons: [], templates: [] };
    else if (path === '/api/config/kpi-templates') body = empty ? [] : [{ id: 1, templateCode: 'AUDIT', templateName: 'Audit template', role: { id: 1, roleCode: 'EMPLOYEE', roleName: 'Employee' }, active: true, status: 'ACTIVE', items: [] }];
    else if (path === '/api/kpi/categories') body = empty ? [] : [{ id: 1, categoryCode: 'QUALITY', categoryName: 'Quality', active: true }];
    else if (path === '/api/kpi/frequencies') body = empty ? [] : [{ id: 1, frequencyCode: 'MONTHLY', frequencyName: 'Monthly', active: true }];
    else if (path === '/api/kpi/data-sources') body = empty ? [] : [{ id: 1, sourceCode: 'MANUAL', sourceName: 'Manual', active: true }];
    else if (path === '/api/kpi/masters') body = empty ? [] : [{ id: 1, kpiName: 'Delivery quality', kpiCode: 'QUALITY', categoryId: 1, categoryName: 'Quality', active: true, defaultWeight: 10, kpiType: 'QUANTITATIVE', targetType: 'HIGHER_IS_BETTER', measurementUnit: 'PERCENTAGE', frequency: 'MONTHLY', dataSource: 'MANUAL', roleIds: [1], roleNames: ['Employee'] }];
    else if (path === '/api/users') body = { data: empty ? [] : [user] };
    else if (path === '/api/roles' || path === '/api/roles/hierarchy') body = { data: empty ? [] : [{ id: 1, role_name: 'EMPLOYEE', role_code: 'EMPLOYEE', active: true, children: [] }] };
    else if (/^\/api\/(teams|tribes|portfolios|squads)$/.test(path)) {
      const singular = path.slice(5, -1);
      body = { data: empty ? [] : [{ id: 1, name: 'Audit organization', [`${singular}_name`]: 'Audit organization', [`${singular}_code`]: 'AUDIT', active: true, members: [] }] };
    } else if (path === '/api/assignments' || path.startsWith('/api/assignments/user/')) body = { data: empty ? [] : [{ id: 1, user_id: 1, user_name: 'Audit Employee', unit_type: 'SQUAD', unit_id: 1, unit_name: 'Audit squad', active: true, primary_assignment: true, start_date: '2026-01-01' }] };
    else if (path === '/api/hierarchy/tree') body = { data: empty ? [] : [{ id: 2, full_name: 'Audit Manager', email: 'manager@example.invalid', role_name: 'Manager', children: [{ id: 1, full_name: 'Audit Employee', email: 'audit@example.invalid', role_name: 'Employee', children: [] }] }] };
    else if (path === '/api/organization/tree') body = { data: empty ? [] : [{ id: 1, type: 'TRIBE', name: 'Audit tribe', code: 'TRIBE', children: [{ id: 1, type: 'PORTFOLIO', name: 'Audit portfolio', children: [{ id: 1, type: 'SQUAD', name: 'Audit squad', children: [] }] }] }] };
    await route.fulfill({ json: body });
  });
}

async function signIn(page: Page) {
  await page.goto('/login');
  await page.locator('input[formcontrolname="email"]').fill('audit@example.invalid');
  await page.locator('input[formcontrolname="password"]').fill('Mock-only-password');
  await page.getByRole('button', { name: 'Sign In Securely', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

for (const route of allRoutes) {
  test(`visual layout ${route}`, async ({ page }, info) => {
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await mockApp(page);
    if (route !== '/login') await signIn(page);
    await page.goto(route);
    await expect(page).toHaveURL(new RegExp(`${route}$`));
    await expect(route === '/login' ? page.getByRole('heading', { name: 'Welcome back' }) : page.locator('main').getByRole('heading').first()).toBeVisible();
    await expect(page.locator('main').getByText(/^\s*Loading\b/).first()).not.toBeVisible();
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    if (route !== '/login') {
      expect(await page.locator('app-root > div > header').evaluate(el => getComputedStyle(el).backgroundImage)).not.toBe('none');
    }
    const clippedTables = await page.locator('main table').evaluateAll(tables => tables.filter(table => {
      if (table.getBoundingClientRect().right <= innerWidth + 1) return false;
      for (let parent = table.parentElement; parent && parent.tagName !== 'MAIN'; parent = parent.parentElement) {
        if (['auto', 'scroll'].includes(getComputedStyle(parent).overflowX) && parent.scrollWidth > parent.clientWidth) return false;
      }
      return true;
    }).map(table => table.textContent?.trim().slice(0, 60)));
    expect(clippedTables).toEqual([]);
    expect(errors).toEqual([]);
    await page.screenshot({ path: info.outputPath('screen.png'), fullPage: true });
    const contrast = await textContrast(page);
    await info.attach('solid-background-text-contrast', { body: JSON.stringify(contrast, null, 2), contentType: 'application/json' });
    expect(contrast).toEqual([]);
  });
}

async function textContrast(page: Page) {
  return page.evaluate(() => {
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = 1;
    const context = canvas.getContext('2d')!;
    const rgb = (color: string) => {
      context.clearRect(0, 0, 1, 1);
      context.fillStyle = color;
      context.fillRect(0, 0, 1, 1);
      const [r, g, b, a] = context.getImageData(0, 0, 1, 1).data;
      return [r, g, b, a / 255];
    };
    const luminance = (color: number[]) => color.slice(0, 3).map(c => c / 255).map(c => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4)
      .reduce((sum, c, i) => sum + c * [.2126, .7152, .0722][i], 0);
    const failures: Array<{ text: string; ratio: number; color: string; background: number[] }> = [];
    for (const el of document.querySelectorAll<HTMLElement>('main *, header *')) {
      const text = [...el.childNodes].filter(n => n.nodeType === Node.TEXT_NODE).map(n => n.textContent).join('').trim();
      if (!text || !el.getClientRects().length || el.closest('button:disabled, .login-aside')) continue;
      const style = getComputedStyle(el);
      if (style.visibility === 'hidden' || Number(style.opacity) < 1) continue;
      const layers: number[][] = [];
      let skip = false;
      for (let parent: HTMLElement | null = el; parent; parent = parent.parentElement) {
        const s = getComputedStyle(parent);
        if (s.backgroundImage !== 'none' || Number(s.opacity) < 1) { skip = true; break; }
        const color = rgb(s.backgroundColor);
        layers.push(color);
        if ((color[3] ?? 1) === 1) break;
      }
      if (skip) continue; // Gradients/images require visual inspection, not an invented solid background.
      let background = [255, 255, 255];
      for (const layer of layers.reverse()) {
        const alpha = layer[3] ?? 1;
        background = background.map((c, i) => layer[i] * alpha + c * (1 - alpha));
      }
      const foreground = rgb(style.color);
      const alpha = foreground[3] ?? 1;
      const color = background.map((c, i) => foreground[i] * alpha + c * (1 - alpha));
      const a = luminance(color), b = luminance(background);
      const ratio = (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
      const large = parseFloat(style.fontSize) >= 24 || (parseFloat(style.fontSize) >= 18.66 && Number(style.fontWeight) >= 700);
      if (ratio < (large ? 3 : 4.5) - .03) failures.push({ text: text.slice(0, 90), ratio: Math.round(ratio * 100) / 100, color: style.color, background });
    }
    return failures;
  });
}

for (const role of ['EMPLOYEE', 'MANAGER', 'SQUAD_LEAD', 'ADMIN'] as Role[]) {
  test(`role navigation and direct links ${role}`, async ({ page }) => {
    test.setTimeout(180_000);
    await mockApp(page, role);
    await signIn(page);
    for (const route of allRoutes.filter(route => route !== '/login')) {
      const allowed = publicRoutes.includes(route) || (adminRoutes.includes(route) && role === 'ADMIN') ||
        (reviewerRoutes.includes(route) && role !== 'EMPLOYEE') || (route === '/kpi/scorecards' && ['ADMIN', 'MANAGER'].includes(role));
      await page.goto(route);
      await expect(page).toHaveURL(new RegExp(`${allowed ? route : '/kpi/my-kpis'}$`));
      await expect(page.locator('main').getByRole('heading').first()).toBeVisible();
    }
    const mobile = (page.viewportSize()?.width ?? 1440) < 768;
    if (mobile) {
      await page.getByRole('button', { name: /menu/i }).first().click();
    } else {
      await page.getByRole('button', { name: /Performance/ }).click();
    }
    await expect(page.getByRole('link', { name: 'My KPIs', exact: true })).toBeVisible();
    await expect(page.locator('a[href="/kpi/review"]:visible')).toHaveCount(role === 'EMPLOYEE' ? 0 : 1);
    await page.getByRole('link', { name: 'My KPIs', exact: true }).click();
    await expect(page).toHaveURL(/\/kpi\/my-kpis$/);
  });
}

test('logged-out direct links are denied', async ({ page }) => {
  await mockApp(page);
  for (const route of allRoutes.filter(route => route !== '/login')) {
    await page.goto(route);
    await expect(page).toHaveURL(/\/login/);
  }
});

test('secondary scorecard failure retains table and retries', async ({ page }) => {
  await mockApp(page, 'MANAGER', false, '/api/kpi/scorecards/summary');
  await signIn(page);
  await page.goto('/kpi/scorecards');
  await expect(page.getByRole('alert')).toContainText('summary could not be loaded');
  await expect(page.getByRole('cell', { name: 'Audit Employee', exact: true })).toBeVisible();
  await page.route('**/api/kpi/scorecards/summary?*', route => route.fulfill({ json: { totalEmployees: 1, averageScore: 4, statusDistribution: {}, ratingDistribution: {} } }));
  await page.getByRole('button', { name: 'Retry summary' }).click();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(page.getByText('Average Score', { exact: true })).toBeVisible();
});

test('secondary KPI evidence failure is not empty evidence', async ({ page }) => {
  await mockApp(page, 'EMPLOYEE', false, '/api/kpi/review/evidence/1');
  await signIn(page);
  await page.goto('/kpi/my-kpis');
  await expect(page.getByRole('alert')).toContainText('Evidence unavailable');
});

test('loading secondary summary is visible', async ({ page }) => {
  await mockApp(page, 'MANAGER', false, '', '/api/kpi/scorecards/summary');
  await signIn(page);
  await page.goto('/kpi/scorecards');
  await expect(page.getByRole('status')).toContainText('Loading scorecard summary');
  await expect(page.getByRole('status')).toHaveCount(0);
});

test('no-cycle and assignment empty states offer next actions', async ({ page }) => {
  await mockApp(page, 'ADMIN', true);
  await signIn(page);
  await page.goto('/kpi/my-kpis');
  await expect(page.getByRole('button', { name: 'refresh cycles', exact: true })).toBeVisible();
  await page.goto('/kpi/scorecards');
  await expect(page.getByRole('button', { name: 'refresh cycles', exact: true })).toBeVisible();
  await expect(page.getByText('No scorecards for this cycle yet.', { exact: true })).toHaveCount(0);
  await page.goto('/assignments');
  await page.getByRole('button', { name: 'Create the first assignment' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('scorecard modal keyboard focus and responsive table', async ({ page }, info) => {
  await mockApp(page);
  await signIn(page);
  await page.goto('/kpi/scorecards');
  const trigger = page.getByRole('button', { name: /Calibrate/ });
  await trigger.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  expect(await dialog.evaluate(el => el.contains(document.activeElement))).toBe(true);
  for (let i = 0; i < 10; i++) {
    await page.keyboard.press('Tab');
    expect(await dialog.evaluate(el => el.contains(document.activeElement))).toBe(true);
  }
  await page.screenshot({ path: info.outputPath('modal.png'), fullPage: true });
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

for (const route of ['/users', '/roles', '/teams', '/tribes', '/portfolios', '/squads', '/assignments', '/kpi/cycles', '/kpi/categories', '/kpi/frequencies', '/kpi/data-sources', '/kpi/masters', '/kpi/templates']) {
  test(`create dialog layout and keyboard ${route}`, async ({ page }, info) => {
    await mockApp(page);
    await signIn(page);
    await page.goto(route);
    const trigger = page.getByRole('button', { name: /^\+|^Add |^New |^Create / }).first();
    await trigger.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    expect(await dialog.evaluate(el => el.contains(document.activeElement))).toBe(true);
    await expect.poll(() => dialog.evaluate(el => {
      const bounds = el.getBoundingClientRect();
      return bounds.left >= 0 && bounds.right <= innerWidth + 1 && bounds.top >= 0 && bounds.bottom <= innerHeight + 1;
    })).toBe(true);
    await page.keyboard.press('Shift+Tab');
    expect(await dialog.evaluate(el => el.contains(document.activeElement))).toBe(true);
    await page.screenshot({ path: info.outputPath('create-dialog.png'), fullPage: true });
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
    await expect(trigger).toBeFocused();
  });
}

test('employee submits self-assessment with confirmation', async ({ page }) => {
  await mockApp(page, 'EMPLOYEE');
  await signIn(page);
  await page.goto('/kpi/my-kpis');
  await page.getByRole('button', { name: 'Submit Assessment', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.locator('input[type="number"]').fill('95');
  await dialog.locator('textarea').first().fill('Quality goals met with verified results.');
  await dialog.getByRole('button', { name: /Submit to Manager/ }).click();
  await page.getByRole('dialog', { name: /Submit/ }).getByRole('button', { name: 'Submit', exact: true }).click();
  await expect(page.getByText('Self-assessment submitted successfully.', { exact: false })).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.reload();
  await expect(page.locator('article').getByText('SUBMITTED', { exact: true })).toBeVisible();
});

for (const role of ['MANAGER', 'SQUAD_LEAD'] as Role[]) {
  test(`review workflow and secondary failure ${role}`, async ({ page }) => {
    await mockApp(page, role, false, '/api/kpi/review/history/1');
    await signIn(page);
    await page.goto('/kpi/review');
    await page.getByRole('button', { name: '🔍 Review', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Failed to load status history.', { exact: true })).toBeVisible();
    await expect(dialog.getByText('No recorded transitions yet.', { exact: true })).toHaveCount(0);
    await dialog.getByRole('button', { name: /Approve & Rate/ }).click();
    await page.getByRole('dialog').last().getByRole('button', { name: 'Submit Review', exact: true }).click();
    await expect(page.getByText(/Review submitted for Audit Employee/)).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });
}

test('primary KPI failures never appear as no assignments', async ({ page }) => {
  await mockApp(page, 'EMPLOYEE', false, '/api/kpi/my-kpis');
  await signIn(page);
  await page.goto('/kpi/my-kpis');
  await expect(page.getByRole('alert')).toContainText('Failed to load your KPIs');
  await expect(page.getByText('No KPIs assigned to you for this cycle.', { exact: true })).toHaveCount(0);
});

test('empty KPIs, scorecards and published history provide actions', async ({ page }) => {
  await mockApp(page);
  await page.route('**/api/kpi/my-kpis?*', route => route.fulfill({ json: [] }));
  await page.route('**/api/kpi/scorecards?*', route => route.fulfill({ json: [] }));
  await page.route('**/api/kpi/scorecards/me/history', route => route.fulfill({ json: [] }));
  await signIn(page);
  await page.goto('/kpi/my-kpis');
  await expect(page.getByRole('button', { name: 'Refresh KPIs', exact: true })).toBeVisible();
  await page.goto('/kpi/scorecards');
  await expect(page.getByRole('button', { name: 'Generate scorecards from approved KPIs' })).toBeVisible();
  await page.goto('/kpi/my-performance');
  await expect(page.getByText('No published scorecards yet', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Go to my KPIs' })).toBeVisible();
});

for (const [route, endpoint] of [
  ['/kpi/cycles', '/api/config/review-cycles'], ['/kpi/categories', '/api/kpi/categories'],
  ['/kpi/frequencies', '/api/kpi/frequencies'], ['/kpi/data-sources', '/api/kpi/data-sources'],
  ['/kpi/masters', '/api/kpi/masters'], ['/kpi/templates', '/api/config/kpi-templates']
]) {
  test(`configuration failure is not empty data ${route}`, async ({ page }) => {
    await mockApp(page, 'ADMIN', false, endpoint);
    await signIn(page);
    await page.goto(route);
    await expect(page.getByRole('alert').first()).toContainText('Failed to load');
    await expect(page.getByText(/^No .*yet\.|^No .*match/)).toHaveCount(0);
    await expect(page.getByRole('button', { name: /^Retry/ }).first()).toBeVisible();
  });
}

test('admin creates cycle and verifies refreshed list', async ({ page }) => {
  await mockApp(page);
  await signIn(page);
  await page.goto('/kpi/cycles');
  await page.getByRole('button', { name: /Add Review Cycle/ }).click();
  const dialog = page.getByRole('dialog');
  await dialog.locator('input').nth(0).fill('Created audit cycle');
  await dialog.locator('input').nth(1).fill('CREATED');
  await dialog.locator('input[type="date"]').nth(0).fill('2027-01-01');
  await dialog.locator('input[type="date"]').nth(1).fill('2027-03-31');
  await dialog.getByRole('button', { name: /Save|Create/ }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('cell', { name: 'Created audit cycle', exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('cell', { name: 'Created audit cycle', exact: true })).toBeVisible();
});

test('navigation panels paint inside the viewport', async ({ page }, info) => {
  await mockApp(page);
  await signIn(page);
  if ((page.viewportSize()?.width ?? 1440) < 1024) {
    await page.getByRole('button', { name: 'Toggle menu', exact: true }).click();
    for (const name of ['Organization', 'Administration', 'Account']) {
      await page.getByRole('button', { name: new RegExp(`^${name}`) }).click();
    }
    await expect(page.getByRole('link', { name: 'Tribes', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Users', exact: true })).toBeVisible();
    await page.screenshot({ path: info.outputPath('navigation-mobile.png'), fullPage: true });
    await page.getByRole('link', { name: 'My Profile', exact: true }).click();
    await expect(page).toHaveURL(/\/profile$/);
    await expect(page.getByRole('link', { name: 'Tribes', exact: true })).toHaveCount(0);
  } else {
    for (const [menu, link] of [['Organization', 'Tribes'], ['Administration', 'Users'], ['Performance', 'My KPIs'], ['Account', 'My Profile']]) {
      await page.getByRole('button', { name: new RegExp(`^${menu}`) }).click();
      const item = page.getByRole('link', { name: link, exact: true });
      await expect(item).toBeVisible();
      const bounds = await item.boundingBox();
      expect(bounds!.x).toBeGreaterThanOrEqual(0);
      expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
      await page.screenshot({ path: info.outputPath(`navigation-${menu}.png`), fullPage: true });
      await page.keyboard.press('Escape');
      await expect(item).toHaveCount(0);
    }
    await page.getByRole('button', { name: 'Notifications', exact: true }).click();
    await expect(page.getByText('Your KPI has been approved', { exact: true })).toBeVisible();
    await page.screenshot({ path: info.outputPath('notifications-panel.png'), fullPage: true });
  }
});
