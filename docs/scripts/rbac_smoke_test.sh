#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"
USER_TOKEN="${USER_TOKEN:-}"
MANAGER_TOKEN="${MANAGER_TOKEN:-}"

if [[ -z "$ADMIN_TOKEN" || -z "$USER_TOKEN" ]]; then
  echo "Usage: ADMIN_TOKEN=... USER_TOKEN=... [MANAGER_TOKEN=...] BASE_URL=http://localhost:8080 ./rbac_smoke_test.sh"
  exit 1
fi

hit() {
  local label="$1"
  local token="$2"
  local method="$3"
  local url="$4"
  local data="${5:-}"

  local code
  if [[ -n "$data" ]]; then
    code=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url" \
      -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "$data")
  else
    code=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url" \
      -H "Authorization: Bearer $token")
  fi
  printf "%-45s %s\n" "$label" "$code"
}

echo "== Admin expected allowed (2xx or 4xx business validation) =="
hit "ADMIN GET categories" "$ADMIN_TOKEN" GET "$BASE_URL/api/kpi/categories"
hit "ADMIN GET review cycles" "$ADMIN_TOKEN" GET "$BASE_URL/api/config/review-cycles"
hit "ADMIN POST generate-by-role" "$ADMIN_TOKEN" POST "$BASE_URL/api/assignments/generate-by-role?roleCode=EMPLOYEE&cycleCode=Q1_2027" "{}"

echo
echo "== User expected forbidden (403) for admin config =="
hit "USER GET categories" "$USER_TOKEN" GET "$BASE_URL/api/kpi/categories"
hit "USER GET review cycles" "$USER_TOKEN" GET "$BASE_URL/api/config/review-cycles"
hit "USER POST generate-by-role" "$USER_TOKEN" POST "$BASE_URL/api/assignments/generate-by-role?roleCode=EMPLOYEE&cycleCode=Q1_2027" "{}"

echo
echo "== User expected allowed for own workflow =="
hit "USER GET my-kpis" "$USER_TOKEN" GET "$BASE_URL/api/kpi/review/my-kpis?cycleCode=Q1_2027"

if [[ -n "$MANAGER_TOKEN" ]]; then
  echo
  echo "== Manager expected allowed for review ops =="
  hit "MANAGER GET pending-reviews" "$MANAGER_TOKEN" GET "$BASE_URL/api/kpi/review/pending-reviews"
  hit "MANAGER GET employee-kpis" "$MANAGER_TOKEN" GET "$BASE_URL/api/kpi/review/employee-kpis?cycleCode=Q1_2027&userId=1"
fi

echo
echo "RBAC smoke test complete."

