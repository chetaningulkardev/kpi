# Documentation

| | |
| --- | --- |
| [`../README.md`](../README.md) | Start here. |
| [`../DATABASE_MIGRATIONS.md`](../DATABASE_MIGRATIONS.md) | Flyway, the baseline, and how to add a migration. |
| [`../DEPLOYMENT_CONFIGURATION.md`](../DEPLOYMENT_CONFIGURATION.md) | Environment variables, tunables, HTTP status contract. |
| [`../RBAC_PERMISSION_MATRIX.md`](../RBAC_PERMISSION_MATRIX.md) | Who can do what, and why. |
| [`DEMO_DATA.md`](DEMO_DATA.md) | Fictional development accounts, organization, and KPI approval flow. |
| [`ARCHITECTURE_DELIVERY.md`](ARCHITECTURE_DELIVERY.md) | Requirements, architecture, implemented choices, and proposed quality gates. |
| [`../frontend/README.md`](../frontend/README.md) | Frontend development and unit tests. |
| [`../frontend/e2e/README.md`](../frontend/e2e/README.md) | Playwright live tests and isolated UI audit. |
The root README and maintained configuration, migration, authorization, and
demo guides are the current record.

## `sql/`

One-off scripts used to seed or repair data during development — role mappings,
ladder KPI definitions, an RBAC audit query. These are **not** migrations; the
schema is owned by Flyway under `src/main/resources/db/migration`.

## `scripts/`

Legacy convenience wrappers (`build.sh`, `run.sh`, `start.sh`) that predate the
Dockerfile, plus an RBAC smoke test. `docker compose up` supersedes them.

The maintained scripts live in [`../tools/`](../tools).
