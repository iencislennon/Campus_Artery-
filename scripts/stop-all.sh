#!/usr/bin/env bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
for pidfile in "$ROOT"/build/pids/*.pid; do
  [ -f "$pidfile" ] || continue
  kill "$(cat "$pidfile")" 2>/dev/null || true
  rm -f "$pidfile"
done
echo "stopped"
