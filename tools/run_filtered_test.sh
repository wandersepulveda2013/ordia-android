#!/usr/bin/env bash
# Ejecuta SOLO las clases de test de dominio cuyo nombre casa un filtro,
# sobre la compilación JVM del dominio (sin Android SDK).
# Uso: run_filtered_test.sh "<regex de nombre de clase>"
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILTER="${1:?uso: run_filtered_test.sh <class-name-regex>}"
KOTLINC=$([ -x /tmp/kotlinc-home/bin/kotlinc ] && echo /tmp/kotlinc-home/bin/kotlinc || ([ -x /tmp/kotlinc-home/kotlinc/bin/kotlinc ] && echo /tmp/kotlinc-home/kotlinc/bin/kotlinc || command -v kotlinc))
LIBS=/tmp/libs
# Heap determinista igual que `run_domain_tests.sh` (OMS/OOM evitado con
# las ~533 fuentes compiladas); JAVA_OPTS externo prevalece.
export JAVA_OPTS="${JAVA_OPTS:--Xmx4g}"
CP="$LIBS/json-20231013.jar:$LIBS/junit-4.13.2.jar:$LIBS/hamcrest-core-1.3.jar:$LIBS/kotlin-stdlib-2.1.20.jar:$LIBS/kotlinx-coroutines-core-1.10.2.jar:$LIBS/kotlinx-coroutines-core-jvm-1.10.2.jar:$LIBS/kotlinx-coroutines-test-1.10.2.jar:$LIBS/kotlinx-coroutines-test-jvm-1.10.2.jar"
DOMAIN_MAIN="$ROOT/app/src/main/java/com/ordia/app/domain"
DATA_LOCAL="$ROOT/app/src/main/java/com/ordia/app/data/local"
AUTOMATION_MAIN="$ROOT/app/src/main/java/com/ordia/app/automation"
BACKUP_MAIN="$ROOT/app/src/main/java/com/ordia/app/backup"
CONTEXT_MAIN="$ROOT/app/src/main/java/com/ordia/app/context"
CONVERSATIONS_MAIN="$ROOT/app/src/main/java/com/ordia/app/conversations"
OUT=/tmp/filtered_tests
rm -rf "$OUT"; mkdir -p "$OUT"
SOURCES=(
  "$ROOT/tools/domain-smoke/RoomStubs.kt"
  "$ROOT/tools/domain-smoke/PreferenceStubs.kt"
  "$ROOT/tools/domain-smoke/AndroidLogStub.kt"
  "$DATA_LOCAL/Entities.kt"
  "$DOMAIN_MAIN"/*.kt
  "$AUTOMATION_MAIN/AutomationRules.kt" "$AUTOMATION_MAIN/AutomationSchedulePolicy.kt"
  "$AUTOMATION_MAIN/AutomationUndoRules.kt" "$AUTOMATION_MAIN/AutomationActionPlanner.kt"
  "$BACKUP_MAIN/BackupManager.kt" "$BACKUP_MAIN/BackupSecurityRules.kt" "$BACKUP_MAIN/RestoreData.kt"
  "$BACKUP_MAIN/BackupStore.kt" "$BACKUP_MAIN/BackupPreferences.kt" "$BACKUP_MAIN/ReminderSchedulerPort.kt"
  "$CONTEXT_MAIN/ContextEvent.kt" "$CONTEXT_MAIN/ContextualSuggestion.kt" "$CONTEXT_MAIN/ContextCaptureSource.kt"
  "$CONTEXT_MAIN/ContextPrivacyFilter.kt" "$CONTEXT_MAIN/ContextRateLimiter.kt"
  "$CONTEXT_MAIN/NotificationObservationPolicy.kt" "$CONTEXT_MAIN/ContextIntent.kt" "$CONTEXT_MAIN/ContextDeduplicator.kt"
  "$CONTEXT_MAIN/ContextIntentEngine.kt"
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/IntelligenceSchema.kt"
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/IntelligenceSafetyGate.kt"
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/GenerativeModelStatus.kt"
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/IntelligenceResponse.kt"
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/IntelligenceProvider.kt"
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/IntelligenceRequest.kt"
  "$CONVERSATIONS_MAIN"/*.kt
  "$ROOT/app/src/main/java/com/ordia/app/assistant"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/domain"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/automation"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/assistant"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/intelligence"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/backup"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/context"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/conversations"/*.kt
)
echo ">> Compilando ${#SOURCES[@]} fuentes..."
"$KOTLINC" -cp "$CP" "${SOURCES[@]}" -d "$OUT" 2>"$OUT/compile.err" || {
  echo "ERROR de compilación:"; tail -40 "$OUT/compile.err"; exit 1
}
TEST_CLASSES=$(find "$OUT" -name '*Test.class' ! -name '*\$*' | sed "s|$OUT/||;s|/|.|g;s|\.class$||" | sort -u | grep -E "$FILTER")
echo ">> Test classes filtradas: $(echo "$TEST_CLASSES" | wc -l)"
java -cp "$OUT:$CP" org.junit.runner.JUnitCore $TEST_CLASSES
