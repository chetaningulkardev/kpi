-- Squad Lead KPIs Insert Script
-- Carefully adding 12 KPIs for Squad Lead role

INSERT INTO kpi_masters
(kpi_code, kpi_name, category_id, description, kpi_type, target_type, measurement_unit, frequency_id, frequency, data_source, default_weight, active, created_at, updated_at)
VALUES
-- Delivery & Execution (3 KPIs)
('SQUAD_LEAD_ON_TIME_DELIVERY_RATE', 'On-time delivery rate', 12, '% of committed sprint/roadmap items delivered within the committed window', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 26, 'MONTHLY', 'Sprint/Roadmap Tool', 15.0, true, NOW(), NOW()),
('SQUAD_LEAD_CYCLE_TIME', 'Cycle time', 12, 'Median time from work started to shipped to production for the squad''s work', 'NUMBER', 'TREND_TARGET', 'Days', 26, 'MONTHLY', 'CI/CD + Ticketing', 10.0, true, NOW(), NOW()),
('SQUAD_LEAD_ESTIMATE_ACCURACY', 'Estimate accuracy', 12, 'Planned vs. actual effort/time variance across the quarter', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 17, 'QUARTERLY', 'Sprint Tool', 5.0, true, NOW(), NOW()),

-- Quality & Technical Health (3 KPIs)
('SQUAD_LEAD_CHANGE_FAILURE_RATE', 'Change failure rate', 14, '% of deployments causing a production incident or rollback', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 26, 'MONTHLY', 'Incident Tracker', 10.0, true, NOW(), NOW()),
('SQUAD_LEAD_ESCAPED_DEFECT_RATE', 'Escaped defect rate', 14, 'Bugs found in production vs. caught pre-release', 'NUMBER', 'TREND_TARGET', 'Count', 17, 'QUARTERLY', 'Bug Tracker', 10.0, true, NOW(), NOW()),
('SQUAD_LEAD_TECH_DEBT_INVESTMENT', 'Tech debt investment honored', 14, '% of committed tech-debt/paydown time actually delivered (not deprioritized)', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 17, 'QUARTERLY', 'Roadmap Tool', 5.0, true, NOW(), NOW()),

-- Innovation (1 KPI)
('SQUAD_LEAD_INNOVATION', 'POCs/Tech improvements', 11, 'POCs/Tech improvements', 'NUMBER', 'NUMBER_TARGET', 'Count', 17, 'QUARTERLY', 'Manager Review', 20.0, true, NOW(), NOW()),

-- People & Team Health (1 KPI)
('SQUAD_LEAD_GROWTH_PLAN_CONSISTENCY', '1:1 & growth plan consistency', 21, '% of reports with a current development plan and regular 1:1s held', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 26, 'MONTHLY', 'Manager Self-Report', 5.0, true, NOW(), NOW()),

-- Technical Leadership (2 KPIs)
('SQUAD_LEAD_DESIGN_REVIEW_PARTICIPATION', 'Design/architecture review participation', 18, 'Active, quality participation in design reviews affecting the squad', 'QUALITATIVE', 'QUALITATIVE_TARGET', 'Qualitative', 17, 'QUARTERLY', 'Peer Feedback', 5.0, true, NOW(), NOW()),
('SQUAD_LEAD_BLOCKER_RESOLUTION', 'Cross-team blocker resolution', 18, 'Dependencies/blockers raised to other teams that get resolved, not just raised, on-time', 'QUALITATIVE', 'QUALITATIVE_TARGET', 'Qualitative', 26, 'MONTHLY', 'Manager Judgment', 5.0, true, NOW(), NOW()),

-- Stakeholder Management (2 KPIs)
('SQUAD_LEAD_STAKEHOLDER_SATISFACTION', 'Stakeholder satisfaction', 20, 'Feedback from product/PM partners on communication and delivery reliability', 'RATING', 'RATING_TARGET', 'Score', 17, 'QUARTERLY', 'Stakeholder Survey', 5.0, true, NOW(), NOW()),
('SQUAD_LEAD_RISK_BLOCKER_TRANSPARENCY', 'Risk/blocker transparency', 20, 'Risks and blockers surfaced early vs. discovered late by stakeholders', 'QUALITATIVE', 'QUALITATIVE_TARGET', 'Qualitative', 17, 'QUARTERLY', 'Manager + Stakeholder Input', 5.0, true, NOW(), NOW())
ON CONFLICT (kpi_code) DO NOTHING;

-- Ensure KPI-to-role mapping table exists and map these KPI masters to SQUAD_LEAD role
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
JOIN roles r ON r.role_code = 'SQUAD_LEAD'
WHERE km.kpi_code LIKE 'SQUAD_LEAD%'
ON CONFLICT DO NOTHING;

-- View the inserted KPIs
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
WHERE m.kpi_code LIKE 'SQUAD_LEAD%'
ORDER BY m.id;

