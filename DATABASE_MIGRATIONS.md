# Database Migrations

The schema is owned by Flyway. Hibernate no longer alters it.

## What changed

`spring.jpa.hibernate.ddl-auto` moved from `update` to `validate`. Hibernate
now only *checks* that the entity mappings match the schema and refuses to
start on drift, instead of quietly issuing DDL against the database at startup.

Migrations live in `src/main/resources/db/migration` and are applied
automatically at boot, before the `EntityManagerFactory` is built.

| Version | What it does |
| --- | --- |
| `V1__baseline_schema.sql` | Snapshot of the schema as it stood when Flyway was introduced. |
| `V2__add_kpi_optimistic_lock_version.sql` | `kpis.version` for the `@Version` field on `Kpi`. |
| `V3__add_performance_indexes.sql` | Indexes for the review queue and org-hierarchy expansion. |
| `V4__align_score_column_types.sql` | Score columns to `double precision` to match the `Double` fields. |
| `V5__retire_legacy_domain_model_tables.sql` | Moves the removed parallel model's tables to `legacy_archive`. |
| `V6__add_automated_metric_collection.sql` | `kpi_metric_bindings`, `user_external_identities`, and `kpis.suggested_actual_*`. |

## How the baseline works

In the base configuration, `spring.flyway.baseline-on-migrate` is `true` with
`baseline-version: 1`:

- **Existing database** — Flyway stamps it as already at V1 and *skips*
  `V1__baseline_schema.sql`, then applies V2 onwards.
- **New database** — Flyway runs everything from V1.

The `prod` profile disables automatic baselining. Existing nonempty databases
without Flyway history require a deliberate adoption procedure; consult the
[deployment guide](DEPLOYMENT_CONFIGURATION.md#profiles) before migrating one.

## Adding a migration

1. Create `src/main/resources/db/migration/V<n>__short_description.sql`, where
   `<n>` is one higher than the current maximum.
2. Make the corresponding entity change.
3. Run `./mvnw test`. `SchemaMigrationTest` and the `@SpringBootTest` context
   load will fail if the entity and the schema disagree.

Never edit a migration that has already been applied anywhere.
`validate-on-migrate` is on, so Flyway compares checksums and will refuse to
start rather than silently repair.

> `./mvnw package` alone does not remove a deleted migration from
> `target/classes`. Use `./mvnw clean package` after renaming or deleting one,
> or Flyway reports "Found more than one migration with version N".

## Regenerating the baseline

Only relevant if V1 has to be rebuilt from scratch:

```sh
PGPASSWORD=... pg_dump -h 127.0.0.1 -U kpi_user -d kpi \
  --schema-only --no-owner --no-privileges --no-comments -n public \
  > /tmp/baseline_raw.sql
python3 tools/mk_baseline.py
```

## Verifying against a scratch database

`tools/verify_migrations.sh` boots the packaged jar against a named database
and reports whether it started. Because `ddl-auto` is `validate`, a successful
start is itself proof that every entity matches the migrated schema:

```sh
./mvnw clean -DskipTests package
createdb -O kpi_user kpi_scratch
./tools/verify_migrations.sh kpi_scratch /tmp/scratch.log
```

`tools/smoke_lookups.sh <db>` goes one step further and logs in over HTTP, then
calls `/api/config/lookups/DEPARTMENT` — an end-to-end check of the one
endpoint whose entity changed packages.

Worth doing both against a *restored copy* of production before a release: one
run on an empty database and one on the copy, then `diff` the two `pg_dump
--schema-only` outputs. They should be identical.

## The retired parallel domain model

The codebase used to carry two entity packages. `com.example.kpi.model`
was an earlier iteration whose tables shadowed the live ones by a single
character — `kpi`/`kpis`, `kpi_template`/`kpi_templates`,
`kpi_template_item`/`kpi_template_items`, `review_cycle`/`review_cycles`,
`employee`/`users`. A query written against the singular name returned stale
rows and no error.

It has been removed. `com.example.kpi.entity` is now the only entity
package.

The one class worth keeping was `LookupValue`, which backs
`/api/config/lookups/{lookupType}` and *is* called by the UI
(`configuration.service.ts`). It moved to `entity` along with its
`AuditableEntity` base, keeping the `lookup_value` table and its JSON shape
unchanged.

`AuditableEntity` and `BaseEntity` now sit side by side and are **not**
interchangeable:

| | `BaseEntity` | `AuditableEntity` |
| --- | --- | --- |
| Identifier | none — subclass declares it | owns `id` |
| Timestamps | `LocalDateTime` `created_date` / `updated_date` | `Instant` `created_at` / `updated_at` |
| Actor columns | `created_by` / `updated_by` | none |
| Soft delete | no | `active` / `deleted` |
| Populated by | Spring Data `AuditingEntityListener` | JPA `@PrePersist` / `@PreUpdate` |

Extend `BaseEntity` for new entities unless you specifically need soft deletion.

V5 moves the eight orphaned tables to a `legacy_archive` schema rather than
dropping them — several still held seed rows, and `ALTER TABLE ... SET SCHEMA`
back is the whole undo. Nothing maps them, so Hibernate ignores them entirely.

Removed alongside them: `EmployeeRepository`,
`EmployeeKpiAssignmentRepository`, `WorkflowDefinitionRepository`, and
`EmployeeConfigurationController` — the latter exposed CRUD at
`/api/config/employees` over a table nothing else read, and no frontend code
called it.
