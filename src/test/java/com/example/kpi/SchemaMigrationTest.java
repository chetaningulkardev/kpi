package com.example.kpi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Flyway setup.
 * <p>
 * The context loading at all already proves a lot: {@code ddl-auto} is
 * {@code validate}, so Hibernate refuses to start if any entity has drifted
 * from what the migrations produced. That check is what caught the
 * {@code Double}/{@code DECIMAL(5,2)} mismatch these migrations fix.
 * <p>
 * The assertions below cover what validation alone cannot see.
 */
@SpringBootTest
class SchemaMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway has taken ownership of the schema")
    void flywayHistoryIsPresentAndClean() throws Exception {
        Integer historyTable = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'
                """, Integer.class);
        assertThat(historyTable)
                .as("flyway_schema_history should exist once Flyway has run")
                .isEqualTo(1);

        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",
                String.class);

        // Checked for shape rather than against a hardcoded list, which would
        // need editing on every migration and would fail for the wrong reason.
        // What actually matters is that the chain starts at the baseline and
        // has no gaps or repeats: either would mean a migration was skipped or
        // applied twice.
        List<Integer> applied = versions.stream().map(Integer::parseInt).toList();
        assertThat(applied).as("the baseline must be the first entry").startsWith(1);
        assertThat(applied).doesNotHaveDuplicates();
        for (int i = 0; i < applied.size(); i++) {
            assertThat(applied.get(i))
                    .as("migration chain must be contiguous, but %s follows %s",
                            applied.get(i), i == 0 ? "the start" : applied.get(i - 1))
                    .isEqualTo(i + 1);
        }

        // The applied chain must match what is actually on the classpath,
        // otherwise a deleted or renamed migration goes unnoticed.
        assertThat(applied).hasSize(countMigrationScripts());

        Integer failures = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        assertThat(failures).as("no migration may be left in a failed state").isZero();
    }

    @Test
    @DisplayName("V2 gives kpis a non-null optimistic lock column")
    void kpisHasVersionColumn() {
        String nullable = jdbcTemplate.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'kpis' AND column_name = 'version'
                """, String.class);
        assertThat(nullable)
                .as("a NULL version makes Hibernate treat a loaded Kpi as transient")
                .isEqualTo("NO");
    }

    @Test
    @DisplayName("V3 indexes the columns the review queue filters on")
    void reviewQueryPathsAreIndexed() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);

        // Without these, findPendingForReviewerInScope and the org-hierarchy
        // expansion in AccessControlService both fall back to sequential scans.
        assertThat(indexes).contains(
                "idx_kpis_status",
                "idx_kpis_reviewer_status",
                "idx_kpis_status_submitted_at",
                "idx_squad_leads_user",
                "idx_portfolio_leads_user",
                "idx_tribe_leads_user",
                "idx_users_reporting_manager");
    }

    @Test
    @DisplayName("V4 stores scores as double precision, so nothing is rounded on save")
    void scoreColumnsAreDoublePrecision() {
        List<String> stillNumeric = jdbcTemplate.queryForList("""
                SELECT table_name || '.' || column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND data_type = 'numeric'
                  AND (table_name, column_name) IN (
                        ('kpis', 'actual_value'),
                        ('kpis', 'rating'),
                        ('kpi_scorecards', 'overall_score'),
                        ('kpi_scorecards', 'max_score'),
                        ('kpi_status_history', 'rating'))
                """, String.class);

        assertThat(stillNumeric)
                .as("a numeric(5,2) column silently truncates a computed score such as 4.3333")
                .isEmpty();
    }

    @Test
    @DisplayName("V5 leaves no shadow of the removed parallel domain model in public")
    void legacyDomainModelTablesAreRetired() {
        List<String> survivors = jdbcTemplate.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('employee', 'employee_kpi_assignment', 'kpi',
                                     'kpi_template', 'kpi_template_item',
                                     'review_cycle', 'workflow_definition', 'workflow_stage')
                ORDER BY table_name
                """, String.class);

        // Each of these shadows a live table by a single character -- kpi/kpis,
        // review_cycle/review_cycles, employee/users. Leaving one behind means
        // a query against the wrong name returns stale rows and no error.
        assertThat(survivors).isEmpty();
    }

    @Test
    @DisplayName("lookup_value survives the retirement: it still backs /api/config/lookups")
    void lookupValueRemainsLive() {
        Integer present = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'lookup_value'
                """, Integer.class);
        assertThat(present)
                .as("LookupValue moved to the entity package but kept its table")
                .isEqualTo(1);
    }

    /**
     * Counts the versioned migration scripts on the classpath.
     *
     * <p>Note that {@code target/classes} keeps a deleted migration until a
     * {@code clean}, which Flyway then reports as a duplicate version — so a
     * mismatch here may mean a stale build directory rather than a real fault.</p>
     */
    private int countMigrationScripts() throws Exception {
        var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        return resolver.getResources("classpath:db/migration/V*__*.sql").length;
    }
}

