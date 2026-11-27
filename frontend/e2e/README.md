# End-to-end tests

The Playwright tests cover browser navigation against a running application and
an isolated UI audit with mocked API responses. Run the commands below from
the repository root unless noted otherwise.

## Setup

Install Node.js 22 and the frontend dependencies, then install Chromium:

```sh
cd frontend
npm ci
npx playwright install chromium
```

The remaining commands are run from `frontend/`.

## Live application tests

The live configuration expects a running application at
`http://localhost:8080` by default. Use a **disposable, isolated database and
application**; never run live tests against shared or production data. See the
[root README](../../README.md) for startup instructions.

Set credentials for an administrator in the isolated application. For example,
the fictional `dev` dataset includes the account documented in the
[demo guide](../../docs/DEMO_DATA.md):

```sh
export E2E_ADMIN_EMAIL=john.doe@example.com
export E2E_ADMIN_PASSWORD='Welcome2026!'
```

For a non-`dev` application use the credentials configured during bootstrap.

Run individual suites:

```sh
npx playwright test -c playwright.config.ts e2e/smoke.spec.ts --project=chromium
npx playwright test -c playwright.config.ts e2e/org-crud.spec.ts --project=chromium
```

`smoke.spec.ts` checks login and core navigation. Despite its filename,
`org-crud.spec.ts` currently checks that organization screens render; it does
not create or delete records.

Override the base URL or add `--headed` to show the browser:

```sh
E2E_BASE_URL=http://localhost:4200 npx playwright test -c playwright.config.ts e2e/smoke.spec.ts --project=chromium --headed
```

`demo-readiness.spec.ts` visits routes for multiple roles. It skips accounts
that cannot sign in, so skipped cases do not establish coverage. Its optional
role fixtures use the scripts in `tools/`; inspect and adapt their database
connection settings before use, and use only a disposable database.

`npm run e2e:smoke` runs only the smoke suite; `npm run e2e:all` runs the live
suites, including the role sweep. The fixture-backed UI audit uses a separate
configuration.

## Isolated UI audit

```sh
npm run e2e:audit
```

This builds the production browser bundle and serves it on
`127.0.0.1:4300`. The audit intercepts API requests with fixtures; it does not
need a backend or database. It covers desktop and mobile layouts, navigation,
dialogs, workflow states, and selected accessibility checks. Failure
screenshots and traces are written to `frontend/audit-results/`.

The audit does not verify live backend authorization, database persistence, or
third-party integrations. Some visual checks, including gradients and images,
still require manual inspection.
