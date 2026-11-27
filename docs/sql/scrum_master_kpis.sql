-- Scrum Master KPIs Insert Script
-- Adds KPI masters and maps them to SCRUM_MASTER role (idempotent)

INSERT INTO kpi_masters
(kpi_code, kpi_name, category_id, description, kpi_type, target_type, measurement_unit, frequency_id, frequency, data_source, default_weight, active, created_at, updated_at)
VALUES
('SCRUM_MASTER_SPRINT_COMMITMENT_RELIABILITY', 'Sprint commitment reliability', 27, '% of sprint goals/commitments met across the squad(s) they facilitate', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 15, 'BI_WEEKLY', 'Jira', 30.0, true, NOW(), NOW()),
('SCRUM_MASTER_CYCLE_TIME_FLOW_EFFICIENCY', 'Cycle time / flow efficiency', 27, 'Trend in time work spends in-progress vs. blocked/waiting across the board', 'NUMBER', 'TREND_TARGET', 'Days', 26, 'MONTHLY', 'Jira', 15.0, true, NOW(), NOW()),
('SCRUM_MASTER_BLOCKER_RESOLUTION_TIME', 'Blocker resolution time', 27, 'Median time from a blocker being raised to it being cleared', 'NUMBER', 'NUMBER_TARGET', 'Business Days', 26, 'MONTHLY', 'Jira', 10.0, true, NOW(), NOW()),
('SCRUM_MASTER_CEREMONY_EFFECTIVENESS', 'Ceremony effectiveness', 28, 'Attendance, timeliness, and usefulness of standups, planning, and retros (peer/team rated)', 'RATING', 'RATING_TARGET', 'Score', 17, 'QUARTERLY', 'Manager Review', 10.0, true, NOW(), NOW()),
('SCRUM_MASTER_RETRO_ACTION_FOLLOW_THROUGH', 'Retro action follow-through', 29, '% of retro action items actually implemented by the next retro', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 15, 'BI_WEEKLY', 'Retro Tracker', 15.0, true, NOW(), NOW()),
('SCRUM_MASTER_PROCESS_IMPROVEMENT_INITIATIVES', 'Process improvement initiatives', 29, 'Number/quality of process improvements introduced and adopted (not just proposed)', 'NUMBER', 'NUMBER_TARGET', 'Count', 17, 'QUARTERLY', 'Manager Review', 10.0, true, NOW(), NOW()),
('SCRUM_MASTER_PO_ALIGNMENT', 'PO Alignment', 30, 'PO satisfaction with predictability and communication of the squad''s plan', 'RATING', 'RATING_TARGET', 'Score', 17, 'QUARTERLY', 'Manager Review', 5.0, true, NOW(), NOW()),
('SCRUM_MASTER_AGILE_MATURITY_COACHING', 'Agile maturity coaching', 31, 'Growth in the team''s own agile practice maturity (self-sufficiency, not dependence on the ScM)', 'QUALITATIVE', 'QUALITATIVE_TARGET', 'Qualitative', 17, 'QUARTERLY', 'Manager Review', 5.0, true, NOW(), NOW())
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
JOIN roles r ON r.role_code = 'SCRUM_MASTER'
WHERE km.kpi_code LIKE 'SCRUM_MASTER%'
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
LEFT JOIN kpi_categories c ON m.category_id = c.id
LEFT JOIN kpi_frequencies f ON m.frequency_id = f.id
LEFT JOIN kpi_master_roles kmr ON kmr.kpi_master_id = m.id
LEFT JOIN roles r ON r.id = kmr.role_id
WHERE m.kpi_code LIKE 'SCRUM_MASTER%'
ORDER BY m.id;

