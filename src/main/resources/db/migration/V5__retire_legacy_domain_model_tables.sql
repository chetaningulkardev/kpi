--
-- V5 :: Retire the tables belonging to the removed parallel domain model.
--
-- The codebase carried two entity packages: com.example.kpi.entity (the
-- live model) and com.example.kpi.model (an earlier iteration). The second
-- one has been deleted, apart from LookupValue, which backs
-- /api/config/lookups and has moved into the entity package unchanged.
--
-- These eight tables are now mapped by nothing. Note how closely several of
-- them shadow a live table -- kpi/kpis, kpi_template/kpi_templates,
-- kpi_template_item/kpi_template_items, review_cycle/review_cycles,
-- employee/users -- which is exactly why leaving them in `public` is a hazard:
-- a query written against the singular form would return stale rows and no
-- error.
--
-- They are MOVED to a `legacy_archive` schema rather than dropped. Several
-- still hold seed rows (lookup-style data in workflow_stage, one row in
-- review_cycle and kpi_template, two in employee), and the change is undone
-- with a single SET SCHEMA back if anything turns out to have depended on them.
--
-- Every step is guarded, so this is a no-op on a database that never had them.
--

CREATE SCHEMA IF NOT EXISTS legacy_archive;

DO $$
DECLARE
    legacy_table text;
    -- Children first, so referential integrity holds at every intermediate
    -- step rather than only at the end.
    legacy_tables text[] := ARRAY[
        'employee_kpi_assignment',
        'workflow_stage',
        'workflow_definition',
        'kpi_template_item',
        'kpi_template',
        'kpi',
        'review_cycle',
        'employee'
    ];
BEGIN
    FOREACH legacy_table IN ARRAY legacy_tables LOOP
        IF to_regclass('public.' || quote_ident(legacy_table)) IS NOT NULL THEN
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA legacy_archive', legacy_table);
            RAISE NOTICE 'Archived public.% -> legacy_archive.%', legacy_table, legacy_table;
        END IF;
    END LOOP;
END
$$;

