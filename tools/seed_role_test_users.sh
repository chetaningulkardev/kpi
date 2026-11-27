#!/usr/bin/env bash
#
# Creates one temporary login per role so the demo-readiness sweep can be run as
# somebody other than the admin.
#
# There is no create-user endpoint, and the existing accounts were made through
# the UI, so their passwords are not in the repository. These rows are inserted
# directly and reuse the admin's stored password hash, which makes the password
# the same as the admin's without anyone needing to know the hash. Emails use
# the reserved .invalid TLD so they can never collide with, or mail, a real
# person.
#
# Run tools/drop_role_test_users.sh afterwards to remove them.
set -euo pipefail
: "${E2E_ADMIN_EMAIL:?Set E2E_ADMIN_EMAIL to the test administrator email}"

PSQL="psql postgresql://kpi_user:kpi_password@127.0.0.1:5432/kpi -v ON_ERROR_STOP=1 -q"

$PSQL -v admin_email="$E2E_ADMIN_EMAIL" <<'SQL'
WITH admin_secret AS (
    SELECT password FROM users WHERE email = :'admin_email'
)
INSERT INTO users (employee_code, first_name, last_name, email, password, active, created_by, updated_by, created_date, updated_date)
SELECT
    'E2E' || r.id,
    'E2E',
    r.role_name,
    'e2e-' || lower(replace(r.role_name, ' ', '-')) || '@example.invalid',
    (SELECT password FROM admin_secret),
    true,
    'e2e-seed',
    'e2e-seed',
    now(),
    now()
FROM roles r
WHERE NOT EXISTS (
    SELECT 1 FROM users u
    WHERE u.email = 'e2e-' || lower(replace(r.role_name, ' ', '-')) || '@example.invalid'
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r
  ON u.email = 'e2e-' || lower(replace(r.role_name, ' ', '-')) || '@example.invalid'
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);
SQL

$PSQL -c "SELECT u.email, r.role_name FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id WHERE u.email LIKE 'e2e-%@example.invalid' ORDER BY r.role_name;"
