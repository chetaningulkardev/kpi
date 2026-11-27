-- Developer Ladder KPIs (Junior / Mid / Senior)
-- Strategy:
-- 1) Keep canonical KPI masters (no duplication by level)
-- 2) Map each KPI to roles where weight > 0
-- 3) Keep role-specific weight matrix for template creation (query at bottom)
-- Idempotent: safe to rerun

INSERT INTO kpi_masters
(kpi_code, kpi_name, category_id, description, kpi_type, target_type, measurement_unit, frequency_id, frequency, data_source, default_weight, active, created_at, updated_at)
VALUES
('DEV_LADDER_ON_TIME_DELIVERY', 'On-time delivery', 9, '% of committed tickets/features delivered within sprint or agreed timeline', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 26, 'MONTHLY', 'Sprint Tool', 20.0, true, NOW(), NOW()),
('DEV_LADDER_THROUGHPUT_TREND', 'Throughput trend', 9, 'Trend in tickets/points delivered, used as a directional signal - never a hard score', 'NUMBER', 'TREND_TARGET', 'Points', 26, 'MONTHLY', 'Sprint Tool', 11.67, true, NOW(), NOW()),
('DEV_LADDER_ESTIMATION_ACCURACY', 'Estimation accuracy', 9, 'Planned vs. actual effort variance on owned work', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 17, 'QUARTERLY', 'Sprint Tool', 8.33, true, NOW(), NOW()),
('DEV_LADDER_DEFECT_RATE', 'Defect rate', 10, 'Bugs found in production traced to their code', 'NUMBER', 'TREND_TARGET', 'Count', 17, 'QUARTERLY', 'Bug Tracker', 13.33, true, NOW(), NOW()),
('DEV_LADDER_TEST_COVERAGE', 'Test coverage on owned code', 10, 'Coverage for code they write, per team standard', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 26, 'MONTHLY', 'CI/Test Tooling', 10.0, true, NOW(), NOW()),
('DEV_LADDER_REWORK_RATE', 'Rework rate', 10, '% of their PRs needing significant rework after merge', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 26, 'MONTHLY', 'Git Provider', 11.67, true, NOW(), NOW()),
('DEV_LADDER_CODE_REVIEW_PARTICIPATION', 'Code review participation', 23, 'Quality and timeliness of reviews given and received', 'QUALITATIVE', 'QUALITATIVE_TARGET', 'Qualitative', 26, 'MONTHLY', 'Git Provider', 11.67, true, NOW(), NOW()),
('DEV_LADDER_AUTONOMY_TREND', 'Autonomy trend', 24, 'Movement from needing guidance to owning problems end to end', 'QUALITATIVE', 'TREND_TARGET', 'Qualitative', 17, 'QUARTERLY', 'Manager Judgment', 6.67, true, NOW(), NOW()),
('DEV_LADDER_REFACTORING_INITIATIVES', 'Refactoring initiatives', 25, 'Code improvement/Refactoring result in improved maintainability and efficiency', 'NUMBER', 'NUMBER_TARGET', 'Count', 26, 'MONTHLY', 'Manager Judgment', 5.0, true, NOW(), NOW()),
('DEV_LADDER_MENTORSHIP_DESIGN_INFLUENCE', 'Mentorship & design influence', 26, 'Coaching juniors/mids and contributing to design/architecture decisions', 'QUALITATIVE', 'QUALITATIVE_TARGET', 'Qualitative', 17, 'QUARTERLY', 'Manager + Peer Feedback', 5.0, true, NOW(), NOW())
ON CONFLICT (kpi_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS kpi_master_roles (
  kpi_master_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (kpi_master_id, role_id),
  CONSTRAINT fk_kpi_master_roles_kpi_master FOREIGN KEY (kpi_master_id) REFERENCES kpi_masters(id) ON DELETE CASCADE,
  CONSTRAINT fk_kpi_master_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- Map KPIs to roles based on non-zero weights in your matrix.
INSERT INTO kpi_master_roles (kpi_master_id, role_id)
SELECT km.id, r.id
FROM kpi_masters km
JOIN roles r ON r.role_code IN ('JUNIOR_DEVELOPER','MID_DEVELOPER','SENIOR_DEVELOPER')
WHERE km.kpi_code IN (
  'DEV_LADDER_ON_TIME_DELIVERY',
  'DEV_LADDER_THROUGHPUT_TREND',
  'DEV_LADDER_ESTIMATION_ACCURACY',
  'DEV_LADDER_DEFECT_RATE',
  'DEV_LADDER_TEST_COVERAGE',
  'DEV_LADDER_REWORK_RATE',
  'DEV_LADDER_CODE_REVIEW_PARTICIPATION'
)
ON CONFLICT DO NOTHING;

-- Mid + Senior only
INSERT INTO kpi_master_roles (kpi_master_id, role_id)
SELECT km.id, r.id
FROM kpi_masters km
JOIN roles r ON r.role_code IN ('MID_DEVELOPER','SENIOR_DEVELOPER')
WHERE km.kpi_code IN (
  'DEV_LADDER_AUTONOMY_TREND',
  'DEV_LADDER_REFACTORING_INITIATIVES'
)
ON CONFLICT DO NOTHING;

-- Senior only
INSERT INTO kpi_master_roles (kpi_master_id, role_id)
SELECT km.id, r.id
FROM kpi_masters km
JOIN roles r ON r.role_code = 'SENIOR_DEVELOPER'
WHERE km.kpi_code = 'DEV_LADDER_MENTORSHIP_DESIGN_INFLUENCE'
ON CONFLICT DO NOTHING;

-- Verification: role-wise mapped KPI count for this ladder set.
SELECT r.role_code, COUNT(*) AS mapped_kpis
FROM kpi_master_roles kmr
JOIN kpi_masters km ON km.id = kmr.kpi_master_id
JOIN roles r ON r.id = kmr.role_id
WHERE km.kpi_code LIKE 'DEV_LADDER%'
GROUP BY r.role_code
ORDER BY r.role_code;

-- Verification: full mapping rows.
SELECT km.kpi_code, km.kpi_name, r.role_code
FROM kpi_master_roles kmr
JOIN kpi_masters km ON km.id = kmr.kpi_master_id
JOIN roles r ON r.id = kmr.role_id
WHERE km.kpi_code LIKE 'DEV_LADDER%'
ORDER BY km.kpi_code, r.role_code;

-- Reference matrix for future template-item weights (per role).
-- This should be used while creating role-specific KPI templates.
WITH weight_matrix AS (
  SELECT 'DEV_LADDER_ON_TIME_DELIVERY'::text AS kpi_code, 25::numeric AS junior_w, 20::numeric AS mid_w, 15::numeric AS senior_w
  UNION ALL SELECT 'DEV_LADDER_THROUGHPUT_TREND', 20, 10, 5
  UNION ALL SELECT 'DEV_LADDER_ESTIMATION_ACCURACY', 10, 10, 5
  UNION ALL SELECT 'DEV_LADDER_DEFECT_RATE', 15, 15, 10
  UNION ALL SELECT 'DEV_LADDER_TEST_COVERAGE', 10, 10, 10
  UNION ALL SELECT 'DEV_LADDER_REWORK_RATE', 15, 10, 10
  UNION ALL SELECT 'DEV_LADDER_CODE_REVIEW_PARTICIPATION', 5, 15, 15
  UNION ALL SELECT 'DEV_LADDER_AUTONOMY_TREND', 0, 5, 15
  UNION ALL SELECT 'DEV_LADDER_REFACTORING_INITIATIVES', 0, 5, 10
  UNION ALL SELECT 'DEV_LADDER_MENTORSHIP_DESIGN_INFLUENCE', 0, 0, 5
)
SELECT 'JUNIOR_DEVELOPER' AS role_code, SUM(junior_w) AS total_weight FROM weight_matrix
UNION ALL
SELECT 'MID_DEVELOPER', SUM(mid_w) FROM weight_matrix
UNION ALL
SELECT 'SENIOR_DEVELOPER', SUM(senior_w) FROM weight_matrix;

