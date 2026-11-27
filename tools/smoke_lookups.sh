#!/bin/zsh
# Smoke-tests the /api/config/lookups endpoint against a running instance.
# LookupValue moved packages, so this confirms the endpoint still serves data.
#   usage: tools/smoke_lookups.sh <database-name>
set -u
. tools/smoke_credentials.sh

DB="$1"
LOG=/tmp/smoke_boot.log
JAR=$(ls target/*.jar 2>/dev/null | grep -v original | head -1)
if [ -z "$JAR" ]; then
  # `mvn clean test` removes target/, so the jar has to be rebuilt first.
  echo "RESULT=NO_JAR - run: ./mvnw clean -DskipTests package"
  exit 1
fi
PORT=18080

# JwtTokenProvider refuses to start without a signing key of at least 64 bytes.
export JWT_SECRET="${JWT_SECRET:-smoke-test-signing-key-long-enough-for-hs512-0123456789-abcdefghijkl}"

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
  kill "$APP_PID" 2>/dev/null
  exit 1
fi

RAW=$(curl -s -X POST "http://127.0.0.1:${PORT}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(smoke_login_json)")

# The API serialises snake_case, so the field is access_token, not accessToken.
TOKEN=$(echo "$RAW" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

if [ -z "$TOKEN" ]; then
  echo "RESULT=LOGIN_FAILED raw=$(echo "$RAW" | head -c 300)"
else
  echo "login=OK"
  echo -n "GET /api/config/lookups/DEPARTMENT -> "
  curl -s -o /tmp/lookups.json -w "HTTP %{http_code} " \
    -H "Authorization: Bearer $TOKEN" \
    "http://127.0.0.1:${PORT}/api/config/lookups/DEPARTMENT"
  echo "rows=$(grep -o '"code"' /tmp/lookups.json | wc -l | tr -d ' ')"
  head -c 200 /tmp/lookups.json; echo
fi

kill "$APP_PID" 2>/dev/null
wait "$APP_PID" 2>/dev/null
echo "done"


