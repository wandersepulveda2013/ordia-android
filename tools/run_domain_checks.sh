#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/ordia-domain-smoke"
rm -rf "$OUT"
mkdir -p "$OUT"

# Resuelve kotlinc 2.1.20: prefiere $KOTLINC, luego /tmp/kotlinc-home (la
# instalación reproducible) y al final `kotlinc` del PATH. Algunas imágenes
# traen un kotlinc 1.3 vía apt que no compila la stdlib 2.1.20.
KOTLINC="${KOTLINC:-}"
if [ -z "$KOTLINC" ]; then
  if [ -x /tmp/kotlinc-home/kotlinc/bin/kotlinc ]; then
    KOTLINC=/tmp/kotlinc-home/kotlinc/bin/kotlinc
  else
    KOTLINC="$(command -v kotlinc)" || KOTLINC=/tmp/kotlinc-home/kotlinc/bin/kotlinc
  fi
fi

"$KOTLINC" \
  "$ROOT/tools/domain-smoke/RoomStubs.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/data/local/Entities.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/DateRules.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/DayPlanner.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/FocusClock.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/GuardianCoach.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/CommitmentRules.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/HabitRules.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/NaturalTaskParser.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/QuietHours.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/RecurrenceEngine.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/ReminderRules.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/SearchEngine.kt" \
  "$ROOT/app/src/main/java/com/ordia/app/domain/TaskRules.kt" \
  "$ROOT/tools/domain-smoke/DomainSmoke.kt" \
  -include-runtime -d "$OUT/ordia-domain-smoke.jar"

java -jar "$OUT/ordia-domain-smoke.jar"
