#!/usr/bin/env bash
# Compila TODO el dominio (igual que run_domain_tests.sh) a /tmp/probe_classes,
# compila el probe $1 (archivo .kt) y lo ejecuta. Uso: run_probe.sh <probe.kt>
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROBE="${1:?uso: run_probe.sh <probe.kt>}"
KOTLINC=$([ -x /tmp/kotlinc-home/bin/kotlinc ] && echo /tmp/kotlinc-home/bin/kotlinc || ([ -x /tmp/kotlinc-home/kotlinc/bin/kotlinc ] && echo /tmp/kotlinc-home/kotlinc/bin/kotlinc || command -v kotlinc))
LIBS=/tmp/libs
CP="$LIBS/json-20231013.jar:$LIBS/junit-4.13.2.jar:$LIBS/hamcrest-core-1.3.jar:$LIBS/kotlin-stdlib-2.1.20.jar:$LIBS/kotlinx-coroutines-core-1.10.2.jar:$LIBS/kotlinx-coroutines-core-jvm-1.10.2.jar:$LIBS/kotlinx-coroutines-test-1.10.2.jar:$LIBS/kotlinx-coroutines-test-jvm-1.10.2.jar"
DOMAIN_MAIN="$ROOT/app/src/main/java/com/ordia/app/domain"
DATA_LOCAL="$ROOT/app/src/main/java/com/ordia/app/data/local"
AUTOMATION_MAIN="$ROOT/app/src/main/java/com/ordia/app/automation"
BACKUP_MAIN="$ROOT/app/src/main/java/com/ordia/app/backup"
CONTEXT_MAIN="$ROOT/app/src/main/java/com/ordia/app/context"
CONVERSATIONS_MAIN="$ROOT/app/src/main/java/com/ordia/app/conversations"
OUT=/tmp/probe_classes
rm -rf "$OUT"; mkdir -p "$OUT"
SOURCES=(
  "$ROOT/tools/domain-smoke/RoomStubs.kt"
  "$ROOT/tools/domain-smoke/PreferenceStubs.kt"
  "$DATA_LOCAL/Entities.kt"
  "$DOMAIN_MAIN"/*.kt
  "$AUTOMATION_MAIN/AutomationRules.kt" "$AUTOMATION_MAIN/AutomationSchedulePolicy.kt"
  "$AUTOMATION_MAIN/AutomationUndoRules.kt" "$AUTOMATION_MAIN/AutomationActionPlanner.kt"
  "$BACKUP_MAIN/BackupManager.kt" "$BACKUP_MAIN/BackupSecurityRules.kt" "$BACKUP_MAIN/RestoreData.kt"
  "$BACKUP_MAIN/BackupStore.kt" "$BACKUP_MAIN/BackupPreferences.kt" "$BACKUP_MAIN/ReminderSchedulerPort.kt"
  "$CONTEXT_MAIN/ContextEvent.kt" "$CONTEXT_MAIN/ContextualSuggestion.kt" "$CONTEXT_MAIN/ContextCaptureSource.kt"
  "$CONTEXT_MAIN/ContextPrivacyFilter.kt" "$CONTEXT_MAIN/ContextRateLimiter.kt"
  "$CONTEXT_MAIN/NotificationObservationPolicy.kt" "$CONTEXT_MAIN/ContextIntent.kt" "$CONTEXT_MAIN/ContextDeduplicator.kt"
  "$CONVERSATIONS_MAIN"/*.kt
  "$ROOT/app/src/main/java/com/ordia/app/assistant"/*.kt
)
echo ">> Compilando dominio (${#SOURCES[@]} fuentes)..."
"$KOTLINC" -cp "$CP" "${SOURCES[@]}" -d "$OUT" 2>"$OUT/compile.err" || {
  echo "ERROR de compilación:"; tail -40 "$OUT/compile.err"; exit 1
}
echo ">> Compilando probe $PROBE..."
"$KOTLINC" -cp "$OUT:$CP" "$PROBE" -d "$OUT" 2>"$OUT/probe.err" || {
  echo "ERROR probe:"; tail -40 "$OUT/probe.err"; exit 1
}
echo ">> Ejecutando..."
PROBE_CLASS=$(basename "$PROBE" .kt | sed 's/[^A-Za-z0-9]/_/g')Kt
java -cp "$OUT:$CP" "$PROBE_CLASS"
