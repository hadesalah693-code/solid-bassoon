#!/bin/sh
set -e

# Render Postgres exposes postgresql://... ; Spring Boot needs jdbc:postgresql://...
if [ -z "${DB_URL}" ] && [ -n "${DATABASE_URL}" ]; then
  DB_URL=$(printf '%s' "$DATABASE_URL" | sed -E 's|^postgresql://[^@]+@|jdbc:postgresql://|')
  case "$DB_URL" in
    *\?*) DB_URL="${DB_URL}&sslmode=require" ;;
    *) DB_URL="${DB_URL}?sslmode=require" ;;
  esac
  export DB_URL
fi

echo "[entrypoint] SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-unset}"
echo "[entrypoint] PORT=${PORT:-8080}"
echo "[entrypoint] DB_URL host configured=$(printf '%s' "${DB_URL:-}" | sed -E 's|jdbc:postgresql://([^/:?]+).*|\1|')"

exec java $JAVA_OPTS -Dserver.port="${PORT:-8080}" -jar app.jar
