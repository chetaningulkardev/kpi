--
-- V2 :: Optimistic locking column for kpis.
--
-- The Kpi entity carries @Version so that two managers reviewing the same KPI
-- concurrently cannot silently overwrite each other's rating. Existing rows
-- need a concrete starting value: a NULL version makes Hibernate treat a loaded
-- row as transient and attempt an INSERT.
--

ALTER TABLE public.kpis
    ADD COLUMN IF NOT EXISTS version bigint;

UPDATE public.kpis
SET version = 0
WHERE version IS NULL;

ALTER TABLE public.kpis
    ALTER COLUMN version SET DEFAULT 0;

ALTER TABLE public.kpis
    ALTER COLUMN version SET NOT NULL;

