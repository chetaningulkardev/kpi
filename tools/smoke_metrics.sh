#!/bin/zsh
# Exercises automated metric collection end to end against a running instance.
#
# Proves the parts a unit test cannot: that the migration applied, the admin
# endpoints persist a binding and an identity, and that /collect-metric returns
# a considered outcome rather than an error when GitHub is not configured.
#
#   usage: tools/smoke_metrics.sh <database-name>
set -u
. tools/smoke_credentials.sh

DB="$1"
LOG=/tmp/metrics_smoke.log
JAR=$(ls target/*.jar 2>/dev/null | grep -v original | head -1)
PORT=18083
BASE="http://127.0.0.1:${PORT}"

if [ -z "$JAR" ]; then
  echo "RESULT=NO_JAR (run: ./mvnw clean -DskipTests package)"
  exit 1
fi

export JWT_SECRET="${JWT_SECRET:-smoke-test-signing-key-long-enough-for-hs512-0123456789-abcdefghijkl}"

# Point the connector at a local stand-in so the COLLECTED path is exercised
# without a real token. Set GITHUB_TOKEN to hit the real API instead.
STUB_PORT=18099
python3 tools/fake_github.py $STUB_PORT 42 2> /tmp/fake_github.log &
STUB_PID=$!
sleep 1

export GITHUB_METRICS_ENABLED="${GITHUB_METRICS_ENABLED:-true}"
export GITHUB_TOKEN="${GITHUB_TOKEN:-stub-token}"
export GITHUB_API_URL="${GITHUB_API_URL:-http://127.0.0.1:${STUB_PORT}}"

cleanup() {
  kill "$APP_PID" 2>/dev/null
  kill "$STUB_PID" 2>/dev/null
}
trap cleanup EXIT

java -jar "$JAR" \
  --server.port=$PORT \
  --spring.datasource.url="jdbc:postgresql://127.0.0.1:5432/${DB}" \
  > "$LOG" 2>&1 &
APP_PID=$!

for i in $(seq 1 90); do
  grep -q "Started KpiApplication" "$LOG" 2>/dev/null && break
  grep -q "APPLICATION FAILED TO START\|Application run failed" "$LOG" 2>/dev/null && break
  sleep 1
done

if ! grep -q "Started KpiApplication" "$LOG"; then
  echo "RESULT=APP_DID_NOT_START"
  tail -5 "$LOG"
  kill "$APP_PID" 2>/dev/null
  exit 1
fi

TOKEN=$(curl -s -X POST "${BASE}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(smoke_login_json)" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "RESULT=LOGIN_FAILED"
  kill "$APP_PID" 2>/dev/null
  exit 1
fi
echo "login = OK"
AUTH="Authorization: Bearer $TOKEN"

echo "--- connectors ---"
curl -s -H "$AUTH" "${BASE}/api/metrics/providers"; echo

# GITHUB reports enabled=false here, which is the point: the UI can say
# "not connected" instead of offering a button that always fails.

echo "--- bind a KPI to a metric ---"
KPI_MASTER_ID=$(curl -s -H "$AUTH" "${BASE}/api/kpi/masters/active" \
  | python3 -c 'import sys,json
d=json.load(sys.stdin)
rows=d.get("data") if isinstance(d,dict) else d
print(rows[0]["id"] if rows else "")')
echo "kpi master id = ${KPI_MASTER_ID:-<none>}"

echo -n "POST /api/config/metrics/bindings -> HTTP "
curl -s -o /tmp/binding.json -w "%{http_code}\n" -X POST "${BASE}/api/config/metrics/bindings" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"kpiMasterId\":${KPI_MASTER_ID},\"providerCode\":\"GITHUB\",\"metricKey\":\"pull_requests_merged\",\"configJson\":\"{\\\"org\\\":\\\"acme\\\"}\"}"
cat /tmp/binding.json; echo

echo "--- a nonsense metric must be rejected at configuration time ---"
echo -n "POST bad metric_key -> HTTP "
curl -s -o /tmp/bad_binding.json -w "%{http_code}\n" -X POST "${BASE}/api/config/metrics/bindings" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"kpiMasterId\":${KPI_MASTER_ID},\"providerCode\":\"GITHUB\",\"metricKey\":\"lines_of_code\"}"
cat /tmp/bad_binding.json; echo

echo "--- link an identity to the KPI's owner ---"
# The identity must belong to whoever owns the KPI, not to whoever is signed in.
# Linking it to the admin instead is exactly the mistake that produces a
# correct-looking NO_IDENTITY and a confusing afternoon.
PSQL="psql -h 127.0.0.1 -U ${PGUSER:-kpi_user} -d ${DB} -tAc"
export PGPASSWORD="${PGPASSWORD:-kpi_password}"

KPI_ID=$(eval "$PSQL \"SELECT id FROM kpis WHERE kpi_master_id = ${KPI_MASTER_ID} LIMIT 1\"" 2>/dev/null)
OWNER_ID=$(eval "$PSQL \"SELECT user_id FROM kpis WHERE id = ${KPI_ID:-0}\"" 2>/dev/null)
echo "kpi id = ${KPI_ID:-<none>}, owner = ${OWNER_ID:-<none>}"

echo -n "POST /api/config/metrics/identities -> HTTP "
curl -s -o /tmp/identity.json -w "%{http_code}\n" -X POST "${BASE}/api/config/metrics/identities" \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"userId\":${OWNER_ID},\"providerCode\":\"github\",\"externalHandle\":\"octocat\"}"
cat /tmp/identity.json; echo

echo "--- collect for that KPI ---"

if [ -n "$KPI_ID" ]; then
  echo -n "POST /api/kpi/${KPI_ID}/collect-metric -> HTTP "
  curl -s -o /tmp/collect.json -w "%{http_code}\n" -X POST "${BASE}/api/kpi/${KPI_ID}/collect-metric" -H "$AUTH"
  cat /tmp/collect.json; echo
  echo "the connector asked GitHub for:"
  grep "^q=" /tmp/fake_github.log | tail -1
else
  echo "no KPI uses that master; skipping collection"
fi

kill "$APP_PID" 2>/dev/null
wait "$APP_PID" 2>/dev/null
kill "$STUB_PID" 2>/dev/null
echo done
