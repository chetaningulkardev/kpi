#!/bin/bash
# KPI Quick Start Script
# Usage: bash run.sh

echo "=================================="
echo "KPI Application Launcher"
echo "=================================="
echo ""

# Set Java 26
echo "Setting Java 26..."
export JAVA_HOME=$(/usr/libexec/java_home -v 26)
echo "Java: $(java -version 2>&1 | head -1)"
echo ""

# Check if database exists
echo "Checking PostgreSQL connection..."
if ! psql -U kpi_user -d kpi -c "SELECT 1" > /dev/null 2>&1; then
    echo "❌ ERROR: Cannot connect to kpi database"
    echo ""
    echo "To setup database, run:"
    echo "  createdb kpi"
    echo "  createuser kpi_user"
    echo "  psql -U postgres -d kpi -c \"ALTER USER kpi_user WITH PASSWORD 'kpi_password';\""
    echo "  psql -U postgres -d kpi -c \"GRANT ALL PRIVILEGES ON DATABASE kpi TO kpi_user;\""
    exit 1
fi
echo "✅ Database connection verified"
echo ""

# Check if JAR exists
if [ ! -f "target/KPI-0.0.1-SNAPSHOT.jar" ]; then
    echo "❌ ERROR: JAR file not found"
    echo "Building project first..."
    ./mvnw clean package -DskipTests
fi
echo "✅ JAR file found: $(ls -lh target/KPI-0.0.1-SNAPSHOT.jar | awk '{print $9, "(" $5 ")"}')"
echo ""

echo "Starting KPI Application..."
echo "=================================="
echo "Server will start on: http://localhost:8080"
echo "Default credentials:"
echo "  Email: john.doe@example.com"
echo "  Password: Welcome2026!"
echo ""
echo "Press Ctrl+C to stop the server"
echo "=================================="
echo ""

# Run the application
SPRING_PROFILES_ACTIVE=dev java -jar target/KPI-0.0.1-SNAPSHOT.jar
