#!/usr/bin/env bash
# Smoke JVM del AutomationEngine (c.662).
#
# AutomationEngine NO cabe en run_domain_tests.sh: depende de los repositorios
# concretos (Repositories.kt → DAOs Room). Este smoke los sustituye por fakes
# concretos con los mismos nombres (tools/automation-engine-smoke/Fakes.kt) en
# una compilación aislada, lo que permite verificar el motor en JVM pura por
# primera vez (seam de `zone` en runTrigger/runRule → guard diario → planner).
# Los fakes viven en tools/ (fuera de app/src/test) para no colisionar con las
# clases reales bajo gradle.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/ordia-automation-engine-smoke"
rm -rf "$OUT"
mkdir -p "$OUT"

# Resuelve kotlinc 2.1.20: prefiere $KOTLINC, luego /tmp/kotlinc-home (la
# instalación reproducible) y al final `kotlinc` del PATH.
KOTLINC="${KOTLINC:-}"
if [ -z "$KOTLINC" ]; then
  if [ -x /tmp/kotlinc-home/kotlinc/bin/kotlinc ]; then
    KOTLINC=/tmp/kotlinc-home/kotlinc/bin/kotlinc
  else
    KOTLINC="$(command -v kotlinc)" || KOTLINC=/tmp/kotlinc-home/kotlinc/bin/kotlinc
  fi
fi

LIBS="${ORDIA_LIBS:-/tmp/libs}"
CP="$LIBS/json-20231013.jar:$LIBS/kotlin-stdlib-2.1.20.jar:$LIBS/kotlinx-coroutines-core-1.10.2.jar:$LIBS/kotlinx-coroutines-core-jvm-1.10.2.jar"

"$KOTLINC" -cp "$CP" \
  "$ROOT/tools/domain-smoke/RoomStubs.kt" \
  "$ROOT/tools/domain-smoke/PreferenceStubs.kt" \
  "$ROOT/tools/domain-smoke/AndroidLogStub.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/data/local/Entities.kt" \
  "$ROOT"/app/src/main/java/com/ordia/app/domain/*.kt \
  "$ROOT/app/src/main/java/com/ordia/app/automation/AutomationRules.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/automation/AutomationSchedulePolicy.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/automation/AutomationUndoRules.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/automation/AutomationActionPlanner.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/automation/AutomationEngine.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/backup/BackupManager.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/backup/BackupSecurityRules.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/backup/RestoreData.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/backup/BackupStore.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/backup/BackupPreferences.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/backup/ReminderSchedulerPort.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/context/ContextEvent.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/context/ContextualSuggestion.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/context/ContextCaptureSource.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/context/ContextPrivacyFilter.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/context/ContextRateLimiter.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/context/NotificationObservationPolicy.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/context/ContextIntent.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/context/ContextDeduplicator.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/context/ContextIntentEngine.kt" \
  "$ROOT"/app/src/main/java/com/ordia/app/conversations/*.kt \
  "$ROOT"/app/src/main/java/com/ordia/app/assistant/*.kt \
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/IntelligenceSchema.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/IntelligenceSafetyGate.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/GenerativeModelStatus.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/IntelligenceResponse.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/IntelligenceProvider.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/intelligence/IntelligenceRequest.kt" \
  "$ROOT/tools/automation-engine-smoke/Fakes.kt" \
  "$ROOT/tools/automation-engine-smoke/Sched.kt" \
  "$ROOT/tools/automation-engine-smoke/ProbeAutomationEngine.kt" \
  -d "$OUT/classes"

java -cp "$OUT/classes:$CP" ProbeAutomationEngineKt
