#!/usr/bin/env bash
set -euo pipefail

# Build jar (skip tests for speed)
./gradlew :stirling-pdf:bootJar -x test

# Copy jar to override mount location if exists
mkdir -p ./stirling/latest
cp -f app/core/build/libs/Stirling-PDF-*.jar ./stirling/latest/app.jar

# Up with override
docker compose -f docker-compose.postgres.yml -f docker-compose.override.yml up -d --build

echo "Waiting for health..."
for i in {1..60}; do
  if curl -fsS http://localhost:8080/api/v1/info/status >/dev/null; then
    echo "App is UP"; exit 0
  fi
  sleep 2
done
echo "Timed out waiting for app health" >&2
exit 1
