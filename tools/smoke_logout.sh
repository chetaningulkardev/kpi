#!/bin/zsh
# Proves that logout revokes a token server-side, and that a correlation id is
# returned. Both are behaviours a unit test cannot demonstrate end to end.
#   usage: tools/smoke_logout.sh <database-name>
set -u
. tools/smoke_credentials.sh

DB="$1"
LOG=/tmp/logout_smoke.log
JAR=$(ls target/*.jar 2>/dev/null | grep -v original | head -1)
if [ -z "$JAR" ]; then
  # `mvn clean test` removes target/, so the jar has to be rebuilt first.
  echo "RESULT=NO_JAR - run: ./mvnw clean -DskipTests package"
  exit 1
fi
PORT=18082
BASE="http://127.0.0.1:${PORT}"

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

echo -n "before logout: GET /api/auth/me -> HTTP "
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN" "${BASE}/api/auth/me"

echo -n "logout                          -> HTTP "
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Authorization: Bearer $TOKEN" "${BASE}/api/auth/logout"

# The token is unexpired and its signature is still valid, so only the denylist
# can reject it here.
echo -n "after logout:  GET /api/auth/me -> HTTP "
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN" "${BASE}/api/auth/me"

echo "--- correlation id ---"
echo -n "generated when absent: "
curl -s -D - -o /dev/null "${BASE}/actuator/health" | grep -i "^X-Correlation-Id" | tr -d '\r'

echo -n "echoed when supplied:  "
curl -s -D - -o /dev/null -H "X-Correlation-Id: my-trace-42" "${BASE}/actuator/health" \
  | grep -i "^X-Correlation-Id" | tr -d '\r'

echo "log line carrying the id:"
grep -o "\[my-trace-42\].*" "$LOG" | head -1

kill "$APP_PID" 2>/dev/null
wait "$APP_PID" 2>/dev/null
echo done
