-- QA Lead KPIs Insert Script
-- Assumption: "QA lead" maps to role_code = QA_TECH_FOCAL
-- Idempotent: safe to rerun

INSERT INTO kpi_masters
(kpi_code, kpi_name, category_id, description, kpi_type, target_type, measurement_unit, frequency_id, frequency, data_source, default_weight, active, created_at, updated_at)
VALUES
('QA_LEAD_AUTOMATED_TEST_COVERAGE', 'Automated test coverage', 16, '% of critical paths/code covered by automated tests across owned scope', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 26, 'MONTHLY', 'CI/Test Tooling', 20.0, true, NOW(), NOW()),
('QA_LEAD_TEST_SUITE_RELIABILITY', 'Test suite reliability (flakiness)', 16, '% of automated test runs that are flaky (fail intermittently, not due to real bugs)', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 26, 'MONTHLY', 'CI Dashboard', 5.0, true, NOW(), NOW()),
('QA_LEAD_ESCAPED_DEFECT_RATE', 'Escaped defect rate', 32, 'Bugs found in production vs. caught in QA/pre-release', 'NUMBER', 'TREND_TARGET', 'Count', 17, 'QUARTERLY', 'Bug Tracker', 20.0, true, NOW(), NOW()),
('QA_LEAD_CRITICAL_DEFECT_LEAKAGE', 'Critical/high defect leakage', 32, 'Number of Sev1/Sev2 defects reaching production per release', 'NUMBER', 'NUMBER_TARGET', 'Count', 22, 'PER_RELEASE', 'Bug Tracker', 20.0, true, NOW(), NOW()),
('QA_LEAD_RELEASE_SIGNOFF_ACCURACY', 'Release sign-off accuracy', 33, '% of releases signed off by QA that do not require a hotfix within 48h', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 22, 'PER_RELEASE', 'Release Tracker', 15.0, true, NOW(), NOW()),
('QA_LEAD_MANUAL_TO_AUTOMATED_RATIO', 'Manual-to-automated test ratio', 34, 'Trend of manual test effort converted to automated coverage over time', 'PERCENTAGE', 'TREND_TARGET', '%', 17, 'QUARTERLY', 'Test Management Tool', 15.0, true, NOW(), NOW()),
('QA_LEAD_TEST_PROCESS_IMPROVEMENTS', 'Test process improvements', 34, 'Introduction of tooling/process improvements (e.g. shift-left testing, contract tests) that are adopted', 'NUMBER', 'NUMBER_TARGET', 'Count', 17, 'QUARTERLY', 'Manager Review', 5.0, true, NOW(), NOW())
ON CONFLICT (kpi_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS kpi_master_roles (
  kpi_master_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (kpi_master_id, role_id),
  CONSTRAINT fk_kpi_master_roles_kpi_master FOREIGN KEY (kpi_master_id) REFERENCES kpi_masters(id) ON DELETE CASCADE,
  CONSTRAINT fk_kpi_master_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

INSERT INTO kpi_master_roles (kpi_master_id, role_id)
SELECT km.id, r.id
FROM kpi_masters km
JOIN roles r ON r.role_code = 'QA_TECH_FOCAL'
WHERE km.kpi_code LIKE 'QA_LEAD%'
ON CONFLICT DO NOTHING;

SELECT
  m.id,
  m.kpi_code,
  m.kpi_name,
  c.category_name,
  f.frequency_name,
  m.data_source,
  m.default_weight,
  r.role_code
FROM kpi_masters m
LEFT JOIN kpi_categories c ON c.id = m.category_id
LEFT JOIN kpi_frequencies f ON f.id = m.frequency_id
LEFT JOIN kpi_master_roles kmr ON kmr.kpi_master_id = m.id
LEFT JOIN roles r ON r.id = kmr.role_id
WHERE m.kpi_code LIKE 'QA_LEAD%'
ORDER BY m.id;

