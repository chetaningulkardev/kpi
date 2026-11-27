#!/bin/zsh
# End-to-end check of the login rate limiter and the JWT secret requirement.
#   usage: tools/smoke_auth.sh <database-name>
set -u
. tools/smoke_credentials.sh

DB="$1"
LOG=/tmp/auth_smoke.log
JAR=$(ls target/*.jar 2>/dev/null | grep -v original | head -1)
if [ -z "$JAR" ]; then
  # `mvn clean test` removes target/, so the jar has to be rebuilt first.
  echo "RESULT=NO_JAR - run: ./mvnw clean -DskipTests package"
  exit 1
fi
PORT=18081

export JWT_SECRET="smoke-test-signing-key-long-enough-for-hs512-0123456789-abcdefghijkl"

java -jar "$JAR" \
  --server.port=$PORT \
  --app.security.login.max-attempts=3 \
  --app.security.login.window-seconds=60 \
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
echo "boot with JWT_SECRET = OK"
grep -o "JWT signing key accepted.*" "$LOG" | head -1

login() {
  curl -s -o /dev/null -w "%{http_code}" \
    -X POST "http://127.0.0.1:${PORT}/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}"
}

echo "--- 5 bad passwords, limit is 3 ---"
for i in 1 2 3 4 5; do
  echo "  attempt $i -> HTTP $(login "$E2E_ADMIN_EMAIL" 'WrongPassword1!')"
done

echo "--- a different account from the same address is unaffected ---"
echo "  other account -> HTTP $(login 'someone.else@example.invalid' 'WrongPassword1!')"

echo "--- correct password for the rate-limited account is still blocked ---"
echo "  admin correct -> HTTP $(curl -s -o /dev/null -w '%{http_code}' -X POST "http://127.0.0.1:${PORT}/api/auth/login" -H 'Content-Type: application/json' -d "$(smoke_login_json)")"

kill "$APP_PID" 2>/dev/null
wait "$APP_PID" 2>/dev/null
echo done
