#!/bin/bash

# KPI Build Script
# This script builds the entire project (backend + frontend)

set -e

PROJECT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "🏗️  KPI Build Script"
echo "======================="
echo ""
echo "📍 Project Directory: $PROJECT_DIR"
echo ""

# Check for required tools
echo "✅ Checking for required tools..."

if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 21 or higher."
    exit 1
fi

if ! command -v mvn &> /dev/null && [ ! -f "$PROJECT_DIR/mvnw" ]; then
    echo "❌ Maven is not installed and mvnw not found."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | grep -oP 'version "\K[^"]*')
echo "  ✓ Java version: $JAVA_VERSION"

if [ -f "$PROJECT_DIR/mvnw" ]; then
    MAVEN_CMD="$PROJECT_DIR/mvnw"
else
    MAVEN_CMD="mvn"
fi
echo "  ✓ Maven ready"

if ! command -v node &> /dev/null; then
    echo "  ⚠️  Node.js not found in PATH (Maven will download it)"
fi

echo ""
echo "🔨 Starting build process..."
echo ""

cd "$PROJECT_DIR"

# Clean previous builds
echo "🧹 Cleaning previous builds..."
$MAVEN_CMD clean

echo ""
echo "📦 Building project (Frontend + Backend)..."
echo "This may take several minutes on first build..."
echo ""

# Run Maven build
$MAVEN_CMD package -DskipTests

echo ""
echo "✅ Build completed successfully!"
echo ""
echo "📊 Build Summary:"
echo "  - Backend: Spring Boot 4.1.0 with Java 21"
echo "  - Frontend: Angular 21 (compiled to static assets)"
echo "  - Output JAR: $PROJECT_DIR/target/KPI-0.0.1-SNAPSHOT.jar"
echo ""
echo "🚀 To run the application, execute:"
echo "   ./start.sh"
echo ""
echo "Or run manually:"
echo "   java -jar target/KPI-0.0.1-SNAPSHOT.jar"
echo ""

