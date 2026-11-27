#!/bin/bash

# KPI Startup Script
# This script builds and runs the KPI Dashboard application

set -e

PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JAR_FILE="$PROJECT_DIR/target/KPI-0.0.1-SNAPSHOT.jar"

echo "🚀 KPI Dashboard Launcher"
echo "=================================="
echo ""

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "📦 JAR file not found. Building the project..."
    echo "This may take a few minutes on first build..."
    echo ""
    cd "$PROJECT_DIR"
    ./mvnw clean package -DskipTests
    echo ""
    echo "✅ Build completed successfully!"
    echo ""
else
    echo "✅ JAR file found. Skipping build."
    echo ""
fi

# Kill any existing Java processes on port 8080
echo "🔍 Checking for existing processes on port 8080..."
EXISTING_PID=$(lsof -ti:8080 || true)
if [ ! -z "$EXISTING_PID" ]; then
    echo "⚠️  Found existing process on port 8080 (PID: $EXISTING_PID)"
    read -p "Kill it and continue? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        kill -9 $EXISTING_PID 2>/dev/null || true
        sleep 2
        echo "✅ Process terminated."
    else
        echo "❌ Exiting..."
        exit 1
    fi
fi

echo ""
echo "🎯 Starting KPI application..."
echo "=================================="
echo ""
echo "📍 Application will be available at: http://localhost:8080"
echo "📍 API endpoint: http://localhost:8080/api/kpis"
echo "📍 Press Ctrl+C to stop the application"
echo ""
echo "⏳ Starting application (this may take a few seconds)..."
echo ""

cd "$PROJECT_DIR"
java -jar "$JAR_FILE"
