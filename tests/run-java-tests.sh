#!/usr/bin/env bash
# Compiles and runs the Strategy/Adapter/Decorator unit tests. Needs the modules built (scripts/build.sh).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[ -d build/campus ] && [ -d build/fix ] || scripts/build.sh
mkdir -p build/tests
CP="build/common:build/campus:build/fix"
javac -cp "$CP" -d build/tests tests/java/PatternTests.java
java -cp "$CP:build/tests" PatternTests
