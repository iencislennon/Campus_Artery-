#!/usr/bin/env bash
# Runs the plain-print demo of Strategy, Adapter and Decorator. Needs the modules built (scripts/build.sh).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[ -d build/campus ] && [ -d build/fix ] || scripts/build.sh
mkdir -p build/tests
CP="build/common:build/campus:build/fix"
javac -cp "$CP" -d build/tests tests/java/PatternDemo.java
java -cp "$CP:build/tests" PatternDemo
