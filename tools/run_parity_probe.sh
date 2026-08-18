#!/usr/bin/env bash
# Compila el contexto + sonda de paridad y la ejecuta. No es parte del build.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/ordia-parity-probe"
rm -rf "$OUT"; mkdir -p "$OUT"

KOTLINC="${KOTLINC:-/tmp/kotlinc-home/kotlinc/bin/kotlinc}"
LIBS="${DOMAIN_TEST_LIBS:-/tmp/libs}"
CP="$LIBS/json-20231013.jar:$LIBS/junit-4.13.2.jar:$LIBS/hamcrest-core-1.3.jar:$LIBS/kotlin-stdlib-2.1.20.jar:$LIBS/kotlinx-coroutines-core-1.10.2.jar:$LIBS/kotlinx-coroutines-core-jvm-1.10.2.jar:$LIBS/kotlinx-coroutines-test-1.10.2.jar:$LIBS/kotlinx-coroutines-test-jvm-1.10.2.jar"

CONTEXT_MAIN="$ROOT/app/src/main/java/com/ordia/app/context"
DATA_LOCAL="$ROOT/app/src/main/java/com/ordia/app/data/local"
DOMAIN_MAIN="$ROOT/app/src/main/java/com/ordia/app/domain"

SOURCES=(
  "$ROOT/tools/domain-smoke/RoomStubs.kt"
  "$ROOT/tools/domain-smoke/PreferenceStubs.kt"
  "$ROOT/tools/domain-smoke/AndroidLogStub.kt"
  "$DATA_LOCAL/Entities.kt"
  "$DOMAIN_MAIN"/*.kt
  "$CONTEXT_MAIN/ContextEvent.kt"
  "$CONTEXT_MAIN/ContextualSuggestion.kt"
  "$CONTEXT_MAIN/ContextCaptureSource.kt"
  "$CONTEXT_MAIN/ContextPrivacyFilter.kt"
  "$CONTEXT_MAIN/ContextRateLimiter.kt"
  "$CONTEXT_MAIN/NotificationObservationPolicy.kt"
  "$CONTEXT_MAIN/ContextIntent.kt"
  "$CONTEXT_MAIN/ContextDeduplicator.kt"
  "$CONTEXT_MAIN/ContextIntentEngine.kt"
  "$ROOT/app/src/main/java/com/ordia/app/conversations"/*.kt
  "$ROOT/tools/parity-probe/ProbeParity.kt"
)

echo ">> Compilando ${#SOURCES[@]} fuentes..."
"$KOTLINC" -cp "$CP" "${SOURCES[@]}" -d "$OUT" 2>"$OUT/compile.err" || {
  echo "ERROR de compilación:"; tail -40 "$OUT/compile.err"; exit 1
}
java -cp "$OUT:$CP" ProbeParityKt
