#!/bin/sh
# GitNode container entrypoint.
#
# Zero-config mode (default): when no external database / Redis is configured, this script
# runs PostgreSQL and Redis inside the container, keeps their data and the generated secrets
# under $GITNODE_DATA_DIR (default /data) and then starts GitNode. Mount a volume on /data.
#
# External mode: set SPRING_DATASOURCE_URL and/or SPRING_DATA_REDIS_HOST and the matching
# service is NOT started; with both set this script only starts the application.
set -eu

DATA="${GITNODE_DATA_DIR:-/data}"
SECRETS="$DATA/secrets"
PGDATA="$DATA/postgres"
DB_NAME=gitnode
DB_USER=gitnode

log() { echo "[gitnode] $*"; }

EMBEDDED_DB=true
[ -n "${SPRING_DATASOURCE_URL:-}" ] && EMBEDDED_DB=false
EMBEDDED_REDIS=true
[ -n "${SPRING_DATA_REDIS_HOST:-}" ] && EMBEDDED_REDIS=false

PG_PID_FILE="$PGDATA/postmaster.pid"
REDIS_PID=""
APP_PID=""

random_hex() { head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n'; }
random_b64() { head -c 32 /dev/urandom | base64 | tr -d '\n'; }
random_pass() { head -c 64 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20; }
# letters, digits and a symbol-free suffix so it passes typical password rules
random_admin_pass() { printf '%sAa1' "$(random_pass)"; }

# secret <file> <generator>: prints the stored value, creating it on first use.
secret() {
  if [ ! -s "$SECRETS/$1" ]; then
    (umask 077; "$2" > "$SECRETS/$1")
    echo created > "$SECRETS/.$1.new"
  fi
  cat "$SECRETS/$1"
}

stop_all() {
  [ -n "$APP_PID" ] && kill -TERM "$APP_PID" 2>/dev/null && wait "$APP_PID" 2>/dev/null || true
  if [ -n "$REDIS_PID" ]; then kill -TERM "$REDIS_PID" 2>/dev/null || true; wait "$REDIS_PID" 2>/dev/null || true; fi
  if [ "$EMBEDDED_DB" = true ] && [ -f "$PG_PID_FILE" ]; then
    pg_ctl -D "$PGDATA" -m fast -w stop >/dev/null 2>&1 || true
  fi
}

if [ "$EMBEDDED_DB" = true ] || [ "$EMBEDDED_REDIS" = true ]; then
  mkdir -p "$SECRETS"
  chmod 700 "$SECRETS"
  [ -n "${GITNODE_JWT_SECRET:-}" ] || GITNODE_JWT_SECRET=$(secret jwt-secret random_hex)
  [ -n "${ACTIONS_ENCRYPTION_KEY:-}" ] || ACTIONS_ENCRYPTION_KEY=$(secret actions-encryption-key random_b64)
  [ -n "${GITNODE_AI_ENCRYPTION_KEY:-}" ] || GITNODE_AI_ENCRYPTION_KEY=$(secret ai-encryption-key random_b64)
  export GITNODE_JWT_SECRET ACTIONS_ENCRYPTION_KEY GITNODE_AI_ENCRYPTION_KEY
fi

if [ "$EMBEDDED_DB" = true ]; then
  DB_PASSWORD=$(secret db-password random_pass)
  if [ -z "${GITNODE_BOOTSTRAP_ADMIN_PASSWORD:-}" ]; then
    GITNODE_BOOTSTRAP_ADMIN_PASSWORD=$(secret admin-password random_admin_pass)
    export GITNODE_BOOTSTRAP_ADMIN_PASSWORD
  fi

  if [ ! -s "$PGDATA/PG_VERSION" ]; then
    log "initialising PostgreSQL in $PGDATA"
    mkdir -p "$PGDATA"
    chmod 700 "$PGDATA"
    PWFILE=$(mktemp)
    printf '%s' "$DB_PASSWORD" > "$PWFILE"
    initdb -D "$PGDATA" -U "$DB_USER" --pwfile="$PWFILE" --auth=scram-sha-256 -E UTF8 >/dev/null
    rm -f "$PWFILE"
  fi
  log "starting PostgreSQL"
  pg_ctl -D "$PGDATA" -w -t 120 \
    -o "-c listen_addresses=127.0.0.1 -c port=5432 -c unix_socket_directories=/tmp" \
    -l "$DATA/postgres.log" start >/dev/null
  export PGPASSWORD="$DB_PASSWORD"
  if ! psql -h 127.0.0.1 -U "$DB_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1; then
    createdb -h 127.0.0.1 -U "$DB_USER" "$DB_NAME"
  fi
  unset PGPASSWORD
  export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/$DB_NAME"
  export SPRING_DATASOURCE_USERNAME="$DB_USER"
  export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
fi

if [ "$EMBEDDED_REDIS" = true ]; then
  log "starting Redis"
  mkdir -p "$DATA/redis"
  redis-server --bind 127.0.0.1 --port 6379 --dir "$DATA/redis" --save "" --appendonly no \
    --maxmemory 256mb --maxmemory-policy volatile-lru >/dev/null &
  REDIS_PID=$!
  export SPRING_DATA_REDIS_HOST=127.0.0.1
  export SPRING_DATA_REDIS_PORT=6379
fi

# The page is served by the app itself, so links and CORS point at it.
if [ "$EMBEDDED_DB" = true ] || [ "$EMBEDDED_REDIS" = true ]; then
  export GITNODE_GIT_REPO__ROOT="${GITNODE_GIT_REPO__ROOT:-$DATA/repos}"
  mkdir -p "$GITNODE_GIT_REPO__ROOT"
fi
if [ "$EMBEDDED_DB" = true ]; then
  export GITNODE_FRONTEND_BASE_URL="${GITNODE_FRONTEND_BASE_URL:-http://localhost:8080}"
  export GITNODE_CORS_ALLOWED_ORIGINS="${GITNODE_CORS_ALLOWED_ORIGINS:-http://localhost:8080}"
fi

if [ "$EMBEDDED_DB" = true ] || [ "$EMBEDDED_REDIS" = true ]; then
  RAM_PERCENT=60  # leave room for PostgreSQL / Redis next to the JVM
else
  RAM_PERCENT=75
fi

if [ "$EMBEDDED_DB" = true ] && [ -e "$SECRETS/.admin-password.new" ]; then
  rm -f "$SECRETS/.admin-password.new"
  log "=============================================================="
  log " First start. Sign in as  admin  with password:"
  log "   $GITNODE_BOOTSTRAP_ADMIN_PASSWORD"
  log " (stored in $SECRETS/admin-password; change it after sign in)"
  log "=============================================================="
fi
rm -f "$SECRETS"/.*.new 2>/dev/null || true

trap 'stop_all; exit 0' TERM INT

java \
  -Djava.security.egd=file:/dev/./urandom \
  -XX:+UseZGC -XX:+ZGenerational \
  -XX:MaxRAMPercentage="$RAM_PERCENT" \
  -XX:+OptimizeStringConcat \
  -XX:+UseStringDeduplication -XX:StringDeduplicationAgeThreshold=1 \
  -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:CompressedClassSpaceSize=64m \
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/gitnode \
  -XX:+ExitOnOutOfMemoryError \
  -Xlog:gc*:file=/var/log/gitnode/gc.log:time,uptime:filecount=5,filesize=10m \
  -jar /app/app.jar &
APP_PID=$!
wait "$APP_PID" || STATUS=$?
APP_PID=""
stop_all
exit "${STATUS:-0}"
