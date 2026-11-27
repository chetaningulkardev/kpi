#!/bin/zsh
# Verifies the Flyway migration chain against a scratch database.
#   usage: tools/verify_migrations.sh <database-name> <logfile>
set -u

DB="$1"
LOG="$2"
JAR=$(ls target/*.jar 2>/dev/null | grep -v original | head -1)
if [ -z "$JAR" ]; then
  # `mvn clean test` removes target/, so the jar has to be rebuilt first.
  echo "RESULT=NO_JAR - run: ./mvnw clean -DskipTests package"
  exit 1
fi

# JwtTokenProvider refuses to start without a signing key of at least 64 bytes,
# so the migration check needs one even though it never authenticates.
export JWT_SECRET="${JWT_SECRET:-smoke-test-signing-key-long-enough-for-hs512-0123456789-abcdefghijkl}"

echo "jar=$JAR db=$DB"

java -jar "$JAR" \
  --server.port=0 \
  --spring.datasource.url="jdbc:postgresql://127.0.0.1:5432/${DB}" \
  > "$LOG" 2>&1 &
APP_PID=$!

# Poll rather than sleep blindly so a fast failure is reported immediately.
for i in $(seq 1 90); do
  if grep -q "Started KpiApplication" "$LOG" 2>/dev/null; then
    echo "RESULT=STARTED after ${i}s"
    break
  fi
  if grep -q "APPLICATION FAILED TO START\|Application run failed" "$LOG" 2>/dev/null; then
    echo "RESULT=FAILED after ${i}s"
    break
  fi
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "RESULT=EXITED after ${i}s"
    break
  fi
  sleep 1
done

kill "$APP_PID" 2>/dev/null
wait "$APP_PID" 2>/dev/null
echo "done"

