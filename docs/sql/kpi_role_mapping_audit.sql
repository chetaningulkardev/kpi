-- KPI Role Mapping Audit
-- Safe read-only checks to validate mapping quality.

-- 1) KPI count by role
SELECT
  r.role_code,
  r.role_name,
  COUNT(kmr.kpi_master_id) AS mapped_kpis
FROM roles r
LEFT JOIN kpi_master_roles kmr ON kmr.role_id = r.id
GROUP BY r.id, r.role_code, r.role_name
ORDER BY r.role_code;

-- 2) Unmapped KPI masters (should be intentional only)
SELECT
  km.id,
  km.kpi_code,
  km.kpi_name,
  c.category_name,
  km.default_weight,
  km.active
FROM kpi_masters km
LEFT JOIN kpi_master_roles kmr ON kmr.kpi_master_id = km.id
LEFT JOIN kpi_categories c ON c.id = km.category_id
WHERE kmr.kpi_master_id IS NULL
ORDER BY km.kpi_code;

-- 3) KPI masters mapped to more than one role
SELECT
  km.kpi_code,
  km.kpi_name,
  COUNT(DISTINCT kmr.role_id) AS role_count,
  STRING_AGG(DISTINCT r.role_code, ', ' ORDER BY r.role_code) AS role_codes
FROM kpi_masters km
JOIN kpi_master_roles kmr ON kmr.kpi_master_id = km.id
JOIN roles r ON r.id = kmr.role_id
GROUP BY km.id, km.kpi_code, km.kpi_name
HAVING COUNT(DISTINCT kmr.role_id) > 1
ORDER BY km.kpi_code;

-- 4) Focus check: expected developer ladder role spread
-- junior: 7, mid: 9, senior: 10
SELECT
  r.role_code,
  COUNT(*) AS dev_ladder_kpis
FROM kpi_master_roles kmr
JOIN kpi_masters km ON km.id = kmr.kpi_master_id
JOIN roles r ON r.id = kmr.role_id
WHERE km.kpi_code LIKE 'DEV_LADDER%'
  AND r.role_code IN ('JUNIOR_DEVELOPER','MID_DEVELOPER','SENIOR_DEVELOPER')
GROUP BY r.role_code
ORDER BY r.role_code;

-- 5) Focus check: key role sets onboarded so far
SELECT
  r.role_code,
  COUNT(*) AS mapped_kpis
FROM kpi_master_roles kmr
JOIN kpi_masters km ON km.id = kmr.kpi_master_id
JOIN roles r ON r.id = kmr.role_id
WHERE r.role_code IN ('TECH_FOCAL','SQUAD_LEAD','SCRUM_MASTER','QA_TECH_FOCAL')
GROUP BY r.role_code
ORDER BY r.role_code;

