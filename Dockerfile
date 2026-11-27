# Build and runtime image for KPI.
#
# Multi-stage: the JDK, Maven cache and node_modules stay in the build stages so
# the shipped image carries only a JRE and the jar.

# ---------------------------------------------------------------------------
# Stage 1 — Angular frontend
# ---------------------------------------------------------------------------
FROM node:22-alpine AS frontend

WORKDIR /build

# Copy the manifests first so `npm ci` is only re-run when dependencies change,
# not on every source edit.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build -- --configuration production

# ---------------------------------------------------------------------------
# Stage 2 — Spring Boot backend
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-26 AS backend

WORKDIR /build

# Same trick: resolve dependencies against the pom alone so the layer caches.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src/ ./src/
# The frontend build output is served as static content by the backend.
COPY --from=frontend /build/dist/frontend/ ./src/main/resources/static/

# Tests need a live PostgreSQL, which is not available during an image build.
RUN mvn -B -DskipTests package

# ---------------------------------------------------------------------------
# Stage 3 — runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:27-jre-alpine

# Run unprivileged: a compromise in the app should not be able to modify the
# image or reach anything owned by root.
RUN addgroup -S kpi && adduser -S kpi -G kpi

WORKDIR /app

COPY --from=backend /build/target/*.jar app.jar

# Evidence uploads are written here. Mount a volume over it — container
# filesystems do not survive a restart, and the files are referenced by rows in
# the database that do.
RUN mkdir -p /var/lib/kpi/evidence && chown -R kpi:kpi /var/lib/kpi /app

USER kpi

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx: the JVM then respects whatever
# memory limit the container is given instead of reading the host's total.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE=prod

# Database credentials and JWT_SECRET must be supplied at run time. A new
# non-dev database also requires BOOTSTRAP_ADMIN_EMAIL and
# BOOTSTRAP_ADMIN_PASSWORD; see DEPLOYMENT_CONFIGURATION.md.

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
