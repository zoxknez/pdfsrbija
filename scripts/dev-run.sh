#!/usr/bin/env bash
set -euo pipefail

export BROWSER_OPEN=${BROWSER_OPEN:-false}
export DISABLE_ADDITIONAL_FEATURES=${DISABLE_ADDITIONAL_FEATURES:-false}

./gradlew :stirling-pdf:bootRun
