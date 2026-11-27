-- Tech Focal KPIs Insert Script
-- Carefully adding 7 KPIs for Tech Focal role

INSERT INTO kpi_masters
(kpi_code, kpi_name, category_id, description, kpi_type, target_type, measurement_unit, frequency_id, frequency, data_source, default_weight, active, created_at, updated_at)
VALUES
('TECH_FOCAL_ON_TIME_DELIVERY', 'On-time delivery of owned work', 13, '% of committed personal deliverables shipped on time', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 26, 'MONTHLY', 'Sprint Tool', 20.0, true, NOW(), NOW()),
('TECH_FOCAL_DEFECT_RATE', 'Defect rate in owned code', 17, 'Bugs found in production traced to their code. Zero Critical defects', 'NUMBER', 'TREND_TARGET', 'Count', 22, 'PER_RELEASE', 'Bug Tracker', 20.0, true, NOW(), NOW()),
('TECH_FOCAL_TEST_COVERAGE', 'Test coverage & rework rate', 15, 'Coverage on owned code and % of MRs needing significant post-merge rework. Pipeline failure rate', 'PERCENTAGE', 'PERCENTAGE_TARGET', '%', 26, 'MONTHLY', 'CI + Git Provider', 15.0, true, NOW(), NOW()),
('TECH_FOCAL_STANDARDS', 'Standards & best-practice influence', 19, 'Contribution to engineering standards, tooling, or reusable patterns adopted beyond that user squad', 'QUALITATIVE', 'QUALITATIVE_TARGET', 'Qualitative', 17, 'QUARTERLY', 'Manager Judgment', 10.0, true, NOW(), NOW()),
('TECH_FOCAL_TECH_DEBT', 'Refactoring initiatives', 25, 'Code improvement/Refactoring result in improved maintainability and efficiency', 'NUMBER', 'NUMBER_TARGET', 'Count', 26, 'MONTHLY', 'Manager Judgment', 10.0, true, NOW(), NOW()),
('TECH_FOCAL_KNOWLEDGE_SHARING', 'Knowledge sharing', 22, 'Docs, tech talks, RFCs, or onboarding material produced and used by others', 'NUMBER', 'NUMBER_TARGET', 'Count', 17, 'QUARTERLY', 'Manager Review', 5.0, true, NOW(), NOW()),
('TECH_FOCAL_INNOVATION', 'POCs/Tech improvements', 11, 'POCs/Tech improvements', 'NUMBER', 'NUMBER_TARGET', 'Count', 17, 'QUARTERLY', 'Manager Review', 20.0, true, NOW(), NOW())
ON CONFLICT (kpi_code) DO NOTHING;

-- Ensure KPI-to-role mapping table exists and map these KPI masters to TECH_FOCAL role
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
JOIN roles r ON r.role_code = 'TECH_FOCAL'
WHERE km.kpi_code LIKE 'TECH_FOCAL%'
ON CONFLICT DO NOTHING;

-- View the inserted KPIs
SELECT m.id, m.kpi_code, m.kpi_name, c.category_name, f.frequency_name, m.data_source, m.default_weight, r.role_code
FROM kpi_masters m
LEFT JOIN kpi_categories c ON m.category_id = c.id
LEFT JOIN kpi_frequencies f ON m.frequency_id = f.id
LEFT JOIN kpi_master_roles kmr ON kmr.kpi_master_id = m.id
LEFT JOIN roles r ON r.id = kmr.role_id
WHERE m.kpi_code LIKE 'TECH_FOCAL%'
ORDER BY m.id;

