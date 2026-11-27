#!/bin/zsh
# Exercises the employee-facing performance history end to end.
#
# The claim worth proving is the publication gate: an employee must see a
# scorecard only once it has been released, never while it is still being
# calibrated. That is a rule about who learns what and when, so a unit test
# with a mocked repository is not on its own convincing — this drives the real
# query against real rows.
#
# Scorecards are seeded directly with SQL rather than generated through the
# scoring service. The read path is what this feature added; how a scorecard
# comes to exist is a separate concern with its own tests, and depending on it
# here would make this script fail for reasons unrelated to what it checks.
#
#   usage: tools/smoke_performance.sh <database-name>
set -u
. tools/smoke_credentials.sh

DB="$1"
LOG=/tmp/performance_smoke.log
JAR=$(ls target/*.jar 2>/dev/null | grep -v original | head -1)
PORT=18084
BASE="http://127.0.0.1:${PORT}"
# zsh does not word-split an unquoted parameter, so this has to be a function
db() { psql -h 127.0.0.1 -U kpi_user -d "${DB}" -tA -P pager=off "$@"; }

if [ -z "$JAR" ]; then
  echo "RESULT=NO_JAR (run: ./mvnw clean -DskipTests package)"
  exit 1
fi

export JWT_SECRET="${JWT_SECRET:-smoke-test-signing-key-long-enough-for-hs512-0123456789-abcdefghijkl}"
export PGPASSWORD="${PGPASSWORD:-kpi_pass}"

ADMIN_ID=$(db -v admin_email="$E2E_ADMIN_EMAIL" <<'SQL'
SELECT id FROM users WHERE email = :'admin_email';
SQL
)
if [ -z "$ADMIN_ID" ]; then
  echo "RESULT=NO_ADMIN_USER"; exit 1
fi

unseed() {
  db -c "DELETE FROM kpi_scorecards WHERE employee_name = 'SMOKE FIXTURE';" > /dev/null
}

# Three cycles' worth of history, so ordering and the percentage conversion
# both have something to chew on.
unseed
db -v admin_email="$E2E_ADMIN_EMAIL" > /dev/null <<'SQL'
INSERT INTO kpi_scorecards
  (user_id, review_cycle_id, employee_name, role, employee_level,
   overall_score, max_score, status, created_at, updated_at)
SELECT (SELECT id FROM users WHERE email = :'admin_email'),
       rc.id, 'SMOKE FIXTURE', 'Developer', 'L3',
       v.score, 5.0, 'PUBLISHED', NOW(), NOW()
FROM (VALUES ('KPIM1', 3.0), ('KPIM2', 4.0), ('Q3_2027', 3.5)) AS v(code, score)
JOIN review_cycles rc ON rc.cycle_code = v.code;
SQL

SEEDED=$(db -c "SELECT count(*) FROM kpi_scorecards WHERE employee_name='SMOKE FIXTURE';")
if [ "$SEEDED" != "3" ]; then
  echo "RESULT=SEED_FAILED (seeded ${SEEDED}; expected cycles KPIM1/KPIM2/Q3_2027)"; exit 1
fi
echo "seeded ${SEEDED} scorecards for user ${ADMIN_ID}"

java -jar "$JAR" \
  --server.port=$PORT \
  --spring.datasource.url="jdbc:postgresql://127.0.0.1:5432/${DB}" \
  > "$LOG" 2>&1 &
APP_PID=$!

cleanup() { kill $APP_PID 2>/dev/null; unseed; }
trap cleanup EXIT

echo "waiting for startup…"
for i in $(seq 1 60); do
  curl -sf "${BASE}/actuator/health" > /dev/null 2>&1 && break
  sleep 2
done

TOKEN=$(curl -s -X POST "${BASE}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(smoke_login_json)" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print((d.get("data") or d).get("access_token",""))')

if [ -z "$TOKEN" ]; then
  echo "RESULT=LOGIN_FAILED"; tail -30 "$LOG"; exit 1
fi
echo "login = OK"

history_json() {
  curl -s "${BASE}/api/kpi/scorecards/me/history" -H "Authorization: Bearer $TOKEN"
}
fixture_count() {
  history_json | python3 -c \
    'import sys,json; print(sum(1 for c in json.load(sys.stdin) if c["employeeName"]=="SMOKE FIXTURE"))'
}

echo "--- a scorecard still being calibrated must stay hidden ---"
for STATE in DRAFT UNDER_REVIEW CALIBRATION FINAL; do
  db -c "UPDATE kpi_scorecards SET status='${STATE}' WHERE employee_name='SMOKE FIXTURE';" > /dev/null
  COUNT=$(fixture_count)
  echo "  status=${STATE} -> ${COUNT} visible"
  if [ "$COUNT" != "0" ]; then
    echo "RESULT=LEAKED — ${STATE} was visible to the employee"; exit 1
  fi
done

echo "--- once published it appears, with dates and a percentage ---"
db -c "UPDATE kpi_scorecards SET status='PUBLISHED' WHERE employee_name='SMOKE FIXTURE';" > /dev/null
history_json | python3 -c '
import sys, json
cards = [c for c in json.load(sys.stdin) if c["employeeName"] == "SMOKE FIXTURE"]
for c in cards:
    print("  %-10s score=%s/%s pct=%s start=%s status=%s" % (
        c["cycleCode"], c["overallScore"], c["maxScore"],
        c["scorePercentage"], c["cycleStartDate"], c["status"]))
assert len(cards) == 3, "expected 3 published cards, saw %d" % len(cards)
assert all(c["status"] == "PUBLISHED" for c in cards), "a non-published card leaked"

# 3.0 of 5 is 60 percent; the client must not have to work that out.
by_code = {c["cycleCode"]: c for c in cards}
assert by_code["KPIM1"]["scorePercentage"] == 60.0, by_code["KPIM1"]
assert by_code["KPIM2"]["scorePercentage"] == 80.0, by_code["KPIM2"]
assert by_code["Q3_2027"]["scorePercentage"] == 70.0, by_code["Q3_2027"]

dates = [c["cycleStartDate"] for c in cards]
assert dates == sorted(dates), "history came back out of order: %s" % dates
print("  ordering and percentages OK")
' || { echo "RESULT=FAIL"; exit 1; }

echo "--- a mixed set returns only the published ones ---"
db -c "UPDATE kpi_scorecards SET status='CALIBRATION'
            WHERE employee_name='SMOKE FIXTURE'
              AND review_cycle_id = (SELECT id FROM review_cycles WHERE cycle_code='KPIM2');" > /dev/null
history_json | python3 -c '
import sys, json
codes = sorted(c["cycleCode"] for c in json.load(sys.stdin) if c["employeeName"] == "SMOKE FIXTURE")
print("  visible:", codes)
assert codes == ["KPIM1", "Q3_2027"], codes
' || { echo "RESULT=FAIL"; exit 1; }

echo "RESULT=OK"
