#!/usr/bin/env bash
# SaaS OS — Start all apps locally
# Prerequisites: docker-compose up -d (infrastructure must be running)
# Usage: ./start-apps.sh [app-number]
#   ./start-apps.sh          → start all apps
#   ./start-apps.sh 02       → start only App 02
#   ./start-apps.sh stop     → kill all running app processes

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$REPO_ROOT/.app-logs"
mkdir -p "$LOG_DIR"

# App definitions: "module-path:port:name"
declare -A APPS=(
  ["01"]="apps/01-client-portal-builder:8081:client-portal-builder"
  ["02"]="apps/02-team-feedback-roadmap:8082:team-feedback-roadmap"
  ["03"]="apps/03-ai-knowledge-base:8083:ai-knowledge-base"
  ["04"]="apps/04-invoice-payment-tracker:8084:invoice-payment-tracker"
  ["05"]="apps/05-document-approval-workflow:8085:document-approval-workflow"
  ["06"]="apps/06-employee-onboarding-orchestrator:8086:employee-onboarding-orchestrator"
  ["07"]="apps/07-lightweight-issue-tracker:8087:lightweight-issue-tracker"
  ["08"]="apps/08-api-key-management-portal:8088:api-key-management-portal"
  ["09"]="apps/09-changelog-platform:8089:changelog-platform"
  ["10"]="apps/10-okr-goal-tracker:8090:okr-goal-tracker"
)

COMMON_PROPS=(
  "DATABASE_URL=jdbc:postgresql://localhost:5433/changelog"
  "DATABASE_USERNAME=changelog"
  "DATABASE_PASSWORD=changelog"
  "KEYCLOAK_ISSUER_URI=http://localhost:8080/realms/changelog"
  "KEYCLOAK_JWK_URI=http://localhost:8080/realms/changelog/protocol/openid-connect/certs"
)

stop_all() {
  echo "Stopping all SaaS OS apps..."
  pkill -f "spring-boot:run" 2>/dev/null || true
  pkill -f "saas-os" 2>/dev/null || true
  echo "Done."
  exit 0
}

start_app() {
  local num="$1"
  local entry="${APPS[$num]}"
  local path="${entry%%:*}"
  local rest="${entry#*:}"
  local port="${rest%%:*}"
  local name="${rest##*:}"

  echo "▶ Starting App $num ($name) on port $port..."

  local env_overrides=""
  for prop in "${COMMON_PROPS[@]}"; do
    env_overrides="$env_overrides -D${prop}"
  done

  local log_file="$LOG_DIR/app-${num}-${name}.log"

  cd "$REPO_ROOT"
  SERVER_PORT=$port \
  DATABASE_URL=jdbc:postgresql://localhost:5433/changelog \
  DATABASE_USERNAME=changelog \
  DATABASE_PASSWORD=changelog \
  KEYCLOAK_ISSUER_URI=http://localhost:8080/realms/changelog \
  KEYCLOAK_JWK_URI=http://localhost:8080/realms/changelog/protocol/openid-connect/certs \
    mvn -pl "$path" spring-boot:run \
      -Dspring-boot.run.jvmArguments="-Xmx256m" \
      > "$log_file" 2>&1 &

  echo "  PID $! → log: $log_file"
}

# Handle arguments
if [[ "${1:-}" == "stop" ]]; then
  stop_all
fi

TARGET="${1:-all}"

if [[ "$TARGET" == "all" ]]; then
  echo "=== SaaS OS: Starting all 10 apps ==="
  echo "Infrastructure check..."
  if ! docker ps | grep -q saas-os-postgres; then
    echo "⚠  PostgreSQL not running. Start infrastructure first: docker-compose up -d"
    exit 1
  fi
  for num in $(echo "${!APPS[@]}" | tr ' ' '\n' | sort); do
    start_app "$num"
    sleep 2  # stagger starts to avoid DB connection pile-up
  done
  echo ""
  echo "All apps started. Logs in .app-logs/"
  echo "Health check in ~30s:"
  for num in $(echo "${!APPS[@]}" | tr ' ' '\n' | sort); do
    entry="${APPS[$num]}"
    port="${entry#*:}"; port="${port%%:*}"
    name="${entry##*:}"
    echo "  App $num ($name): http://localhost:$port/actuator/health"
  done
else
  # Start a specific app
  if [[ -z "${APPS[$TARGET]:-}" ]]; then
    echo "Unknown app: $TARGET. Valid: ${!APPS[*]}"
    exit 1
  fi
  start_app "$TARGET"
fi
