#!/usr/bin/env bash
#
# Removes the temporary per-role logins created by seed_role_test_users.sh.
# Matched on the reserved .invalid TLD, so this cannot touch a real account.
set -euo pipefail

PSQL="psql postgresql://kpi_user:kpi_password@127.0.0.1:5432/kpi -v ON_ERROR_STOP=1 -q"

$PSQL <<'SQL'
DELETE FROM user_roles
WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'e2e-%@example.invalid');

DELETE FROM users WHERE email LIKE 'e2e-%@example.invalid';
SQL

$PSQL -c "SELECT count(*) AS remaining_test_users FROM users WHERE email LIKE 'e2e-%@example.invalid';"

