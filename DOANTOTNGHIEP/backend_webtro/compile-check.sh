#!/usr/bin/env bash
# =====================================================================
#  Biên dịch backend TRONG Docker (khong can JDK/Maven tren may host).
#  Dung de kiem tra nhanh sau moi dot sinh code.
#
#    bash compile-check.sh          # compile, bo qua test
#    bash compile-check.sh test     # compile + chay test
# =====================================================================
set -euo pipefail

GOAL="${1:-compile}"
HERE="$(cd "$(dirname "$0")" && pwd)"

if [ "$GOAL" = "test" ]; then
  MVN_ARGS="clean test"
else
  MVN_ARGS="clean compile -DskipTests"
fi

# Cache ~/.m2 qua named volume de lan sau khong tai lai dependency.
docker run --rm \
  -v "${HERE}:/app" \
  -v webtro_m2_cache:/root/.m2 \
  -w /app \
  maven:3.9-eclipse-temurin-21 \
  mvn -B -ntp ${MVN_ARGS}
