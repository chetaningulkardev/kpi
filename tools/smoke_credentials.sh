#!/bin/sh
# Source from a smoke test against an isolated database.
: "${E2E_ADMIN_EMAIL:?Set E2E_ADMIN_EMAIL to the test administrator email}"
: "${E2E_ADMIN_PASSWORD:?Set E2E_ADMIN_PASSWORD to the test administrator password}"
export BOOTSTRAP_ADMIN_EMAIL="${BOOTSTRAP_ADMIN_EMAIL:-$E2E_ADMIN_EMAIL}"
export BOOTSTRAP_ADMIN_PASSWORD="${BOOTSTRAP_ADMIN_PASSWORD:-$E2E_ADMIN_PASSWORD}"
export E2E_ADMIN_EMAIL E2E_ADMIN_PASSWORD

smoke_login_json() {
  python3 -c 'import json, os; print(json.dumps({"email": os.environ["E2E_ADMIN_EMAIL"], "password": os.environ["E2E_ADMIN_PASSWORD"]}))'
}
