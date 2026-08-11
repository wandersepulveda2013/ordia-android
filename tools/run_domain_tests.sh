#!/usr/bin/env bash
# Compila y ejecuta los tests unitarios del dominio (app/src/test/.../domain)
# sin Android SDK, usando kotlinc + JUnit4 + stubs de Room/Preferences.
#
# Reutilizable entre sesiones autónomas. No forma parte del build de Gradle.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/ordia-domain-tests"
rm -rf "$OUT"
mkdir -p "$OUT"

KOTLINC="$(command -v kotlinc || echo /tmp/kotlinc-home/kotlinc/bin/kotlinc)"
LIBS="${DOMAIN_TEST_LIBS:-/tmp/libs}"
CP="$LIBS/json-20231013.jar:$LIBS/junit-4.13.2.jar:$LIBS/hamcrest-core-1.3.jar:$LIBS/kotlin-stdlib-2.1.20.jar:$LIBS/kotlinx-coroutines-core-1.10.2.jar:$LIBS/kotlinx-coroutines-core-jvm-1.10.2.jar:$LIBS/kotlinx-coroutines-test-1.10.2.jar:$LIBS/kotlinx-coroutines-test-jvm-1.10.2.jar"

DOMAIN_MAIN="$ROOT/app/src/main/java/com/ordia/app/domain"
DATA_LOCAL="$ROOT/app/src/main/java/com/ordia/app/data/local"

SOURCES=(
  "$ROOT/tools/domain-smoke/RoomStubs.kt"
  "$ROOT/tools/domain-smoke/PreferenceStubs.kt"
  "$DATA_LOCAL/Entities.kt"
  "$DOMAIN_MAIN"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/domain"/*.kt
)

echo ">> Compilando ${#SOURCES[@]} fuentes (main+stubs+tests)..."
"$KOTLINC" -cp "$CP" "${SOURCES[@]}" -d "$OUT" 2>"$OUT/compile.err" || {
  echo "ERROR de compilación:"; tail -40 "$OUT/compile.err"; exit 1
}

# Descubrir clases de test (las que contienen @Test) y pasarlas a JUnitCore.
TEST_CLASSES=$(find "$OUT" -name '*Test.class' ! -name '*\$*' | sed "s|$OUT/||;s|/|.|g;s|\.class$||" | sort -u)
echo ">> Test classes: $(echo "$TEST_CLASSES" | wc -l)"

java -cp "$OUT:$CP" org.junit.runner.JUnitCore $TEST_CLASSES
