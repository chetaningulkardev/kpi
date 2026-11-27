--
-- V4 :: Align the score/value columns with the Java types that read them.
--
-- Five fields were declared as `Double` but pinned to a DECIMAL column via
-- @Column(columnDefinition = "DECIMAL(x,2)"). Two problems followed:
--
--   1. Hibernate maps Double to `double precision`, so `ddl-auto: validate`
--      rejected every one of these columns ("found [numeric], but expecting
--      [decimal(5,2) (Types#FLOAT)]"). The mismatch was invisible only because
--      ddl-auto was set to `update`.
--
--   2. The truncation was silent. KpiScoringService computes weighted scores
--      such as 4.3333; the object in memory kept that value while the database
--      stored 4.33, so a score changed the moment the entity was reloaded.
--
-- The rest of the codebase consistently treats these as `Double`, so the
-- columns are widened to match rather than the Java being moved to BigDecimal.
-- numeric -> double precision preserves every existing value.
--
-- Note this also removes the implicit range limit that precision 5 carried
-- (max 999.99). Ratings are bounded by ScoringConfiguration, not by the column.
--

ALTER TABLE public.kpis
    ALTER COLUMN actual_value TYPE double precision USING actual_value::double precision,
    ALTER COLUMN rating TYPE double precision USING rating::double precision;

ALTER TABLE public.kpi_scorecards
    ALTER COLUMN overall_score TYPE double precision USING overall_score::double precision,
    ALTER COLUMN max_score TYPE double precision USING max_score::double precision;

ALTER TABLE public.kpi_status_history
    ALTER COLUMN rating TYPE double precision USING rating::double precision;

