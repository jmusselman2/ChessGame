#!/usr/bin/env bash
#
# M15.3 verification: prove the beta Supabase environment works end to end.
#
# Reads BETA_DATABASE_URL, SUPABASE_URL, and SUPABASE_ANON_KEY from the
# git-ignored .env. Nothing here prints or stores a credential.
#
#   1. connects to the beta database through the Supavisor session pooler,
#      confirming the connection is encrypted,
#   2. starts the server against it, which applies the Flyway migrations,
#   3. signs in anonymously against the Supabase project,
#   4. calls /me with that token, so the whole chain -- Supabase issues, JWKS
#      verifies, the server writes to the beta database -- is exercised,
#   5. reports the migrated schema.
#
# Usage:  bash scripts/verify-beta-database.sh
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "No .env. Copy .env.example and fill it in (see docs/DEVELOPMENT.md)." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
. ./.env
set +a

: "${BETA_DATABASE_URL:?BETA_DATABASE_URL is not set in .env -- see .env.example}"
: "${SUPABASE_URL:?SUPABASE_URL is not set in .env}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY is not set in .env}"

log() { printf '\n== %s\n' "$1"; }

log "1/5 Reaching the beta database through the session pooler"
docker compose exec -T postgres psql "$BETA_DATABASE_URL" -A -t \
  -c "select 'server=' || version();" \
  -c "select 'encrypted=' || (select ssl from pg_stat_ssl where pid = pg_backend_pid());"

log "2/5 Starting the server against the beta database (it migrates on startup)"
DATABASE_URL="$BETA_DATABASE_URL" ./gradlew.bat :server:run --console=plain > /tmp/beta-server.log 2>&1 &
SERVER_PID=$!
trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT

for _ in $(seq 1 90); do
  if curl -fsS -m 3 http://localhost:8080/health >/dev/null 2>&1; then break; fi
  sleep 2
done
curl -fsS -m 10 http://localhost:8080/health && echo

log "3/5 Signing in anonymously against the Supabase project"
TOKEN=$(curl -fsS -X POST "$SUPABASE_URL/auth/v1/signup" \
  -H "apikey: $SUPABASE_ANON_KEY" -H "Content-Type: application/json" -d '{}' |
  python -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || { echo "no access token returned" >&2; exit 1; }
echo "got a token (not printed)"

log "4/5 Calling /me with it, which writes the identity to the beta database"
curl -fsS -m 30 http://localhost:8080/me -H "Authorization: Bearer $TOKEN"
echo

log "5/5 Schema in the beta database"
docker compose exec -T postgres psql "$BETA_DATABASE_URL" -A -t \
  -c "select table_name from information_schema.tables
        where table_schema='public' order by table_name;" \
  -c "select 'flyway=' || version from flyway_schema_history order by installed_rank;"

log "M15.3 verification complete"
