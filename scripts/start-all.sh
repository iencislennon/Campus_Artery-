#!/usr/bin/env bash
# Builds and starts gateway :8080, CampusPlus :8081, FixMyCampus :8082, ProjectForge :8083, AI validation :8084, Mailer :8085 and the frontend :5173.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ ! -f .env ]; then
  cp .env.example .env
  secret="$(openssl rand -hex 32)"
  sed -i.bak "s/^CAMPUSOS_SECRET=.*/CAMPUSOS_SECRET=${secret}/" .env && rm -f .env.bak
  echo "created .env with a fresh CAMPUSOS_SECRET"
fi
set -a; . ./.env; set +a
export CAMPUSOS_DATA_DIR="$ROOT/${CAMPUSOS_DATA_DIR:-data}"

"$ROOT/scripts/stop-all.sh" >/dev/null 2>&1 || true
"$ROOT/scripts/build.sh"
mkdir -p build/logs build/pids
JAVA_FLAGS="--enable-native-access=ALL-UNNAMED"
LIBS="lib/*"

start() { # name, command...
  local name="$1"; shift
  "$@" > "build/logs/${name}.log" 2>&1 &
  echo $! > "build/pids/${name}.pid"
}

start campus-plus   java $JAVA_FLAGS -cp "build/common:build/campus:$LIBS" campusos.campusplus.presentation.CampusPlusMain
start fix-my-campus java $JAVA_FLAGS -cp "build/common:build/fix:$LIBS" campusos.fixmycampus.presentation.FixMyCampusMain
start gateway       java $JAVA_FLAGS -cp "build/common:build/gateway:$LIBS" campusos.gateway.presentation.GatewayMain
( cd services/project-forge-python && start_py() { python3 main.py > "$ROOT/build/logs/project-forge.log" 2>&1 & echo $! > "$ROOT/build/pids/project-forge.pid"; }; start_py )
( cd services/ai-validation-python && start_ai() { python3 main.py > "$ROOT/build/logs/ai-validation.log" 2>&1 & echo $! > "$ROOT/build/pids/ai-validation.pid"; }; start_ai )
( cd services/mailer-python && start_mail() { python3 main.py > "$ROOT/build/logs/mailer.log" 2>&1 & echo $! > "$ROOT/build/pids/mailer.pid"; }; start_mail )
( cd frontend && python3 -m http.server "${FRONTEND_PORT:-5173}" > "$ROOT/build/logs/frontend.log" 2>&1 & echo $! > "$ROOT/build/pids/frontend.pid" )

for port in "$GATEWAY_PORT" "$CAMPUSPLUS_PORT" "$FIXMYCAMPUS_PORT" "$PROJECTFORGE_PORT" "${AI_VALIDATION_PORT:-8084}" "${MAILER_PORT:-8085}"; do
  for _ in $(seq 1 50); do
    curl -sf "http://localhost:${port}/health" >/dev/null && break
    sleep 0.2
  done
done
echo "gateway     http://localhost:${GATEWAY_PORT}/api/v1/health"
echo "frontend    http://localhost:${FRONTEND_PORT:-5173}"
echo "logs        build/logs/*.log   (stop with scripts/stop-all.sh)"
