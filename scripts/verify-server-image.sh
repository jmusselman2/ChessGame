#!/usr/bin/env bash
#
# M15.2 verification: prove the deployable image is the thing Render will run.
#
# Builds the Dockerfile Render builds, then runs it the way Render runs it -- a
# host-chosen PORT, a free instance's 512 MB, no secret in the image -- and checks
# what a deploy actually depends on:
#
#   1. the image builds from a clean context, without an Android SDK,
#   2. it starts as a non-root user and binds the port PORT names,
#   3. /health answers 200 and does not claim health-only when configured,
#   4. the JVM is sized for the container rather than a quarter of it,
#   5. the SQL migrations shipped inside the image,
#   6. an anonymous Supabase token is verified and a database-backed request served,
#   7. a WebSocket upgrades and delivers the connected greeting,
#   8. an unauthenticated WebSocket is refused.
#
# It runs against the disposable local PostgreSQL from compose.yaml, never the beta
# database: this checks the artifact, and M15.3's script checks the beta connection.
# Nothing here prints a credential.
#
# Usage:  bash scripts/verify-server-image.sh
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

: "${SUPABASE_URL:?SUPABASE_URL is not set in .env}"
: "${SUPABASE_ANON_KEY:?SUPABASE_ANON_KEY is not set in .env}"

IMAGE=chessgame-server:verify
CONTAINER=chessgame-server-verify
# Render's default, and deliberately not the 8080 the server falls back to: binding the
# wrong one is the failure this is here to catch.
PORT=10000
NETWORK=chessgame_default

log() { printf '\n== %s\n' "$1"; }
fail() { echo "FAILED: $1" >&2; exit 1; }

cleanup() { docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

log "0/8 The disposable local PostgreSQL is up"
docker compose up -d postgres
docker compose exec -T postgres pg_isready -U chessgame -d chessgame_dev

log "1/8 Building the image Render builds"
docker build -t "$IMAGE" .

log "2/8 Running it as Render does: PORT from the environment, 512 MB, no baked secret"
# The compose network so the container reaches the database by service name; on Render the
# database is reached over the public internet instead, which M15.3 verified separately.
docker run -d --name "$CONTAINER" \
  --network "$NETWORK" \
  --memory 512m \
  -e PORT="$PORT" \
  -e DATABASE_URL="postgresql://chessgame:chessgame@postgres:5432/chessgame_dev" \
  -e SUPABASE_URL="$SUPABASE_URL" \
  -p "$PORT:$PORT" \
  "$IMAGE" >/dev/null

for _ in $(seq 1 60); do
  curl -fsS -m 3 "http://localhost:$PORT/health" >/dev/null 2>&1 && break
  sleep 2
done

docker exec "$CONTAINER" id | grep -q 'uid=[0-9]*(chessgame)' || fail "the server is running as root"
echo "runs as: $(docker exec "$CONTAINER" id -un)"

log "3/8 /health on the port PORT named, and not in health-only mode"
HEALTH=$(curl -fsS -m 10 "http://localhost:$PORT/health") || fail "/health did not answer on $PORT"
echo "$HEALTH"
case "$HEALTH" in
  *health-only*) fail "the server came up without DATABASE_URL/SUPABASE_URL" ;;
esac

log "4/8 The JVM is sized for the container"
docker exec "$CONTAINER" cat /proc/1/cmdline | tr '\0' '\n' | grep -E 'MaxRAMPercentage|UseSerialGC' \
  || fail "JAVA_OPTS did not reach the JVM"

log "5/8 The SQL migrations are inside the image"
# A server whose image left them behind starts cleanly and then has no schema to write to.
docker exec "$CONTAINER" sh -c 'unzip -l /app/lib/server.jar' | grep 'db/migration/V' \
  || fail "the image carries no SQL migrations"

log "6/8 Signing in anonymously and calling /me through the container"
TOKEN=$(curl -fsS -X POST "$SUPABASE_URL/auth/v1/signup" \
  -H "apikey: $SUPABASE_ANON_KEY" -H "Content-Type: application/json" -d '{}' |
  python -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')
[ -n "$TOKEN" ] || fail "no access token returned"
echo "got a token (not printed)"
curl -fsS -m 30 "http://localhost:$PORT/me" -H "Authorization: Bearer $TOKEN" || fail "/me failed"
echo

log "7/8 A WebSocket upgrades and greets"
# curl has no way to close a WebSocket, so -m ending it is the expected end of this check
# and its "timed out" on stderr is not a failure. What matters is in what it read first.
WS=$(curl -s -N -i -m 8 \
  -H "Connection: Upgrade" -H "Upgrade: websocket" -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: $(head -c 16 /dev/urandom | base64)" \
  -H "Authorization: Bearer $TOKEN" \
  "http://localhost:$PORT/ws" 2>/dev/null || true)
echo "$WS" | grep -q '101 Switching Protocols' || fail "the WebSocket did not upgrade"
echo "$WS" | grep -q 'connected' || fail "no connected greeting on the WebSocket"
echo "101 Switching Protocols, and the connected greeting arrived"

log "8/8 An unauthenticated WebSocket is refused"
CODE=$(curl -sS -o /dev/null -w '%{http_code}' -m 8 \
  -H "Connection: Upgrade" -H "Upgrade: websocket" -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: $(head -c 16 /dev/urandom | base64)" \
  "http://localhost:$PORT/ws")
[ "$CODE" = "401" ] || fail "an unauthenticated WebSocket got $CODE, not 401"
echo "401, as it should be"

log "How long the process took to be ready, as a floor for a cold start"
# Only a floor. A Render cold start also schedules the instance and pulls the image, which
# is why M15.2 measures that against the deployed service rather than here.
docker logs "$CONTAINER" 2>&1 | grep 'Application started in' || true

log "M15.2 image verification complete"
