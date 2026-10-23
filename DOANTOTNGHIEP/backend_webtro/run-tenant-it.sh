#!/usr/bin/env bash
# =====================================================================
#  Chạy Integration Test luồng người thuê (src/test/java/com/webtro/it/tenant).
#  Yêu cầu: docker compose đang chạy MySQL (cổng 3307) + Redis (cổng 6380).
#  Lần đầu tạo DB riêng cho IT (không đụng DB 'webtro'):
#    docker exec webtro-mysql mysql -uroot -p<MYSQL_ROOT_PASSWORD> \
#      -e "CREATE DATABASE IF NOT EXISTS webtro_it CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
#          GRANT ALL PRIVILEGES ON webtro_it.* TO '<DB_USERNAME>'@'%'; FLUSH PRIVILEGES;"
#
#    bash run-tenant-it.sh                 # toàn bộ IT tenant
#    bash run-tenant-it.sh TenantRentalFlowIT   # một lớp
#  Biến tùy chọn: MVN (đường dẫn mvn), IT_DB_URL, IT_REDIS_DB...
# =====================================================================
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${HERE}/../.env"

read_env() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '\r'; }

export WEBTRO_IT=true
export IT_DB_USERNAME="${IT_DB_USERNAME:-$(read_env DB_USERNAME)}"
export IT_DB_PASSWORD="${IT_DB_PASSWORD:-$(read_env DB_PASSWORD)}"
export IT_REDIS_PASSWORD="${IT_REDIS_PASSWORD:-$(read_env REDIS_PASSWORD)}"

# E2E chatbot AI trên stack docker đang chạy: bash run-tenant-it.sh TenantChatbotE2EIT
export WEBTRO_E2E=true
export E2E_ADMIN_EMAIL="${E2E_ADMIN_EMAIL:-$(read_env ADMIN_EMAIL)}"
export E2E_ADMIN_PASSWORD="${E2E_ADMIN_PASSWORD:-$(read_env ADMIN_PASSWORD)}"

MVN="${MVN:-mvn}"
TESTS="${1:-Tenant*IT}"
EXTRA=()
# KNOWN_BUGS=1 → chạy cả các test @Disabled("BUG-xx") để xem bug còn hay đã được sửa.
if [ "${KNOWN_BUGS:-0}" = "1" ]; then
  EXTRA+=("-Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition")
fi
cd "$HERE"
"$MVN" -B -o test -Dtest="$TESTS" -Dsurefire.failIfNoSpecifiedTests=false "${EXTRA[@]}"
