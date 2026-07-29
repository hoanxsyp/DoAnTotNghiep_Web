#!/usr/bin/env bash
# =====================================================================
#  Kiem tra NHANH toan bo migration chay duoc tren MySQL that, KHONG can
#  build lai backend image. Dung khi debug migration.
#
#  Yeu cau: mysql container `webtro-mysql` dang chay (docker compose up -d mysql).
#  Tao mot database tam `webtro_flywaytest`, chay het migration, roi bao ket qua.
# =====================================================================
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"

# Doc mat khau root tu .env
ROOT_PW="$(grep -E '^MYSQL_ROOT_PASSWORD=' "${HERE}/../.env" | cut -d= -f2)"
NET="doantotnghiep-webtro_webtro-net"

echo "== Tao lai database test =="
docker exec webtro-mysql sh -c "mysql -uroot -p'${ROOT_PW}' -e 'DROP DATABASE IF EXISTS webtro_flywaytest; CREATE DATABASE webtro_flywaytest CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'"

echo "== Chay Flyway migrate =="
docker run --rm \
  --network "${NET}" \
  -v "${HERE}/src/main/resources/db/migration:/flyway/sql" \
  flyway/flyway:10 \
  -url="jdbc:mysql://mysql:3306/webtro_flywaytest?allowPublicKeyRetrieval=true&useSSL=false" \
  -user=root -password="${ROOT_PW}" \
  -placeholders.adminEmail=admin@webtro.local \
  migrate

echo "== Dem bang =="
docker exec webtro-mysql sh -c "mysql -uroot -p'${ROOT_PW}' webtro_flywaytest -e 'SELECT COUNT(*) AS tables FROM information_schema.tables WHERE table_schema=\"webtro_flywaytest\";'"
