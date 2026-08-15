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

# Resuelve el compilador: prefiere $KOTLINC, luego la instalación 2.1.20 de
# /tmp/kotlinc-home (permanente entre runs del supervisor) y solo al final
# `kotlinc` del PATH. Es vital porque algunas imágenes traen un kotlinc 1.3
# vía apt que no compila la stdlib 2.1.20: si `command -v` lo encontraba
# primero, el fallback nunca se activaba y la compilación fallaba. La
# instalación 2.1.20 es la versión reproducible que exige este script.
KOTLINC="${KOTLINC:-}"
if [ -z "$KOTLINC" ]; then
  if [ -x /tmp/kotlinc-home/kotlinc/bin/kotlinc ]; then
    KOTLINC=/tmp/kotlinc-home/kotlinc/bin/kotlinc
  else
    KOTLINC="$(command -v kotlinc)" || KOTLINC=/tmp/kotlinc-home/kotlinc/bin/kotlinc
  fi
fi
LIBS="${DOMAIN_TEST_LIBS:-/tmp/libs}"
CP="$LIBS/json-20231013.jar:$LIBS/junit-4.13.2.jar:$LIBS/hamcrest-core-1.3.jar:$LIBS/kotlin-stdlib-2.1.20.jar:$LIBS/kotlinx-coroutines-core-1.10.2.jar:$LIBS/kotlinx-coroutines-core-jvm-1.10.2.jar:$LIBS/kotlinx-coroutines-test-1.10.2.jar:$LIBS/kotlinx-coroutines-test-jvm-1.10.2.jar"

DOMAIN_MAIN="$ROOT/app/src/main/java/com/ordia/app/domain"
DATA_LOCAL="$ROOT/app/src/main/java/com/ordia/app/data/local"
AUTOMATION_MAIN="$ROOT/app/src/main/java/com/ordia/app/automation"

# Archivos de automatización que son puros (sin dependencias Android reales):
# AutomationRules, AutomationSchedulePolicy, AutomationUndoRules, AutomationActionPlanner.
# AutomationEngine depende de repositorios concretos (Repositories.kt) que a su vez usan
# DAOs de Room (Android), por lo que NO se incluye aquí. AutomationWorker usa WorkManager.
AUTOMATION_PURE_SOURCES=(
  "$AUTOMATION_MAIN/AutomationRules.kt"
  "$AUTOMATION_MAIN/AutomationSchedulePolicy.kt"
  "$AUTOMATION_MAIN/AutomationUndoRules.kt"
  "$AUTOMATION_MAIN/AutomationActionPlanner.kt"
)

# Archivos del paquete backup que son puros (sin dependencias Android reales):
# BackupManager, BackupSecurityRules, RestoreData, BackupStore (contrato),
# BackupPreferences, ReminderSchedulerPort. Se excluyen RoomBackupStore.kt (usa
# androidx.room.withTransaction + OrdiaDatabase) y BackupManager.kt NO usa Android.
# Esto hace verificables en JVM los tests de la ruta de datos más crítica (backup/restore).
BACKUP_MAIN="$ROOT/app/src/main/java/com/ordia/app/backup"
BACKUP_PURE_SOURCES=(
  "$BACKUP_MAIN/BackupManager.kt"
  "$BACKUP_MAIN/BackupSecurityRules.kt"
  "$BACKUP_MAIN/RestoreData.kt"
  "$BACKUP_MAIN/BackupStore.kt"
  "$BACKUP_MAIN/BackupPreferences.kt"
  "$BACKUP_MAIN/ReminderSchedulerPort.kt"
)

# Paquete context: la mayoría de fuentes usan Android (ContextEngine, ContextAuditLog,
# ContextualSettingsStore, OrdiaNotificationListenerService, etc.). Se enumeran aquí
# solo las fuentes JVM-puras que tienen tests unitarios verificables sin Android SDK:
# filtros de privacidad, rate-limiter, analyzer contextual y la política de observación
# de notificaciones (área de privacidad/contexto). NotificationObservationPolicy depende
# de ConversationPrivacyPolicy (definido en conversations/CommitmentEngine.kt).
# El subpaquete context/external NO se incluye: sus tests referencian
# ExternalConfirmationController.SECURE_PACKAGES, fuertemente acoplado a Android
# (NotificationManager/PackageManager/Context) → queda NO VERIFICADO en JVM pura.
CONTEXT_MAIN="$ROOT/app/src/main/java/com/ordia/app/context"
CONTEXT_PURE_SOURCES=(
  "$CONTEXT_MAIN/ContextEvent.kt"
  "$CONTEXT_MAIN/ContextualSuggestion.kt"
  "$CONTEXT_MAIN/ContextCaptureSource.kt"
  "$CONTEXT_MAIN/ContextualAnalyzer.kt"
  "$CONTEXT_MAIN/ContextPrivacyFilter.kt"
  "$CONTEXT_MAIN/ContextRateLimiter.kt"
  "$CONTEXT_MAIN/NotificationObservationPolicy.kt"
  "$CONTEXT_MAIN/ContextIntent.kt"
  "$CONTEXT_MAIN/ContextDeduplicator.kt"
)

# Paquete conversations: 2 archivos, ambos JVM-puros (CommitmentEngine define
# ConversationPrivacyPolicy + NotificationObservationPolicy lo consume; ChatImportParser
# es parser de chat sin Android). Se incluye entero con wildcard.
CONVERSATIONS_MAIN="$ROOT/app/src/main/java/com/ordia/app/conversations"

SOURCES=(
  "$ROOT/tools/domain-smoke/RoomStubs.kt"
  "$ROOT/tools/domain-smoke/PreferenceStubs.kt"
  "$DATA_LOCAL/Entities.kt"
  "$DOMAIN_MAIN"/*.kt
  "${AUTOMATION_PURE_SOURCES[@]}"
  "${BACKUP_PURE_SOURCES[@]}"
  "${CONTEXT_PURE_SOURCES[@]}"
  "$CONVERSATIONS_MAIN"/*.kt
  "$ROOT/app/src/main/java/com/ordia/app/assistant"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/domain"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/automation"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/assistant"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/backup"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/context"/*.kt
  "$ROOT/app/src/test/java/com/ordia/app/conversations"/*.kt
)

echo ">> Compilando ${#SOURCES[@]} fuentes (main+stubs+tests)..."
"$KOTLINC" -cp "$CP" "${SOURCES[@]}" -d "$OUT" 2>"$OUT/compile.err" || {
  echo "ERROR de compilación:"; tail -40 "$OUT/compile.err"; exit 1
}

# Descubrir clases de test (las que contienen @Test) y pasarlas a JUnitCore.
TEST_CLASSES=$(find "$OUT" -name '*Test.class' ! -name '*\$*' | sed "s|$OUT/||;s|/|.|g;s|\.class$||" | sort -u)
echo ">> Test classes: $(echo "$TEST_CLASSES" | wc -l)"

java -cp "$OUT:$CP" org.junit.runner.JUnitCore $TEST_CLASSES
