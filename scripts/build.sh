#!/usr/bin/env bash
# Compiles the three Java modules into build/. Needs a JDK (17+) and the jars in lib/.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
rm -rf build/common build/gateway build/campus build/fix
mkdir -p build/common build/gateway build/campus build/fix
javac -d build/common $(find services/common-java -name '*.java')
javac -cp build/common -d build/gateway $(find services/gateway-java -name '*.java')
javac -cp build/common -d build/campus $(find services/campus-plus-java -name '*.java')
javac -cp build/common -d build/fix $(find services/fix-my-campus-java -name '*.java')
echo "build ok"
