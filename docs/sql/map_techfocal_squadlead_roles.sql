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

INSERT INTO kpi_master_roles (kpi_master_id, role_id)
SELECT km.id, r.id
FROM kpi_masters km
JOIN roles r ON r.role_code = 'SQUAD_LEAD'
WHERE km.kpi_code LIKE 'SQUAD_LEAD%'
ON CONFLICT DO NOTHING;

SELECT r.role_code, COUNT(*) AS mapped_kpis
FROM kpi_master_roles kmr
JOIN roles r ON r.id = kmr.role_id
WHERE r.role_code IN ('TECH_FOCAL','SQUAD_LEAD')
GROUP BY r.role_code
ORDER BY r.role_code;

