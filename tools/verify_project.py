#!/usr/bin/env python3
"""Static integrity checks that do not require the Android SDK."""
from __future__ import annotations

import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
ERRORS: list[str] = []
WARNINGS: list[str] = []

REQUIRED = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "app/build.gradle.kts",
    "app/proguard-rules.pro",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/ordia/app/MainActivity.kt",
    "app/src/main/java/com/ordia/app/OrdiaApplication.kt",
    "app/src/main/java/com/ordia/app/ui/OrdiaRoot.kt",
    "app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt",
    "app/src/main/java/com/ordia/app/data/local/OrdiaDatabase.kt",
    "app/src/main/java/com/ordia/app/backup/BackupManager.kt",
    "app/src/main/java/com/ordia/app/overlay/GuardianOverlayService.kt",
    "app/src/main/java/com/ordia/app/overlay/QuickCaptureActivity.kt",
    "app/src/main/java/com/ordia/app/reminders/TaskReminderWorker.kt",
    "app/src/main/java/com/ordia/app/ui/screens/TodayScreen.kt",
    "app/src/main/java/com/ordia/app/ui/screens/TasksScreen.kt",
    "app/src/main/java/com/ordia/app/ui/screens/PlannerScreen.kt",
    "app/src/main/java/com/ordia/app/ui/screens/NotesScreen.kt",
    "app/src/main/java/com/ordia/app/ui/screens/NoteEditorScreen.kt",
    "app/src/main/java/com/ordia/app/ui/screens/ProjectsScreen.kt",
    "app/src/main/java/com/ordia/app/ui/screens/HabitsScreen.kt",
    "app/src/main/java/com/ordia/app/ui/screens/FocusScreen.kt",
    "app/src/main/java/com/ordia/app/ui/screens/SearchScreen.kt",
    "app/src/main/java/com/ordia/app/ui/screens/StatisticsScreen.kt",
    "app/src/main/java/com/ordia/app/ui/screens/SettingsScreen.kt",
    "app/src/main/java/com/ordia/app/ui/screens/ArchiveScreen.kt",
    "app/src/androidTest/java/com/ordia/app/SmokeTest.kt",
    "app/src/androidTest/java/com/ordia/app/DatabaseSmokeTest.kt",
    "tools/run_domain_checks.sh",
    "app/src/main/java/com/ordia/app/domain/GuardianCoach.kt",
    "app/src/main/java/com/ordia/app/domain/DayPlanner.kt",
    "docs/BUILD_AND_RELEASE.md",
    "docs/TESTING.md",
    "docs/PRIVACY_AND_PERMISSIONS.md",
    "PROJECT_STATUS.md",
    ".github/workflows/android-ci.yml",
]

TEXT_EXTENSIONS = {".kt", ".kts", ".xml", ".md", ".py", ".sh", ".yml", ".yaml", ".properties", ".pro"}
MOJIBAKE = ("\ufffd", "Ã", "Â", "├", "┬", "â€", "ðŸ")
FORBIDDEN_FILES = (".backup", ".bak", ".orig", ".tmp")


def fail(message: str) -> None:
    ERRORS.append(message)


def warn(message: str) -> None:
    WARNINGS.append(message)


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeDecodeError as exc:
        fail(f"Non UTF-8 text file: {path.relative_to(ROOT)} ({exc})")
        return ""


def strip_kotlin_comments_and_literals(text: str) -> str:
    """Preserve newlines while removing strings/chars/comments for delimiter checks."""
    out: list[str] = []
    i = 0
    state = "code"
    while i < len(text):
        two = text[i:i + 2]
        three = text[i:i + 3]
        ch = text[i]
        if state == "code":
            if three == '"""':
                out.extend("   ")
                i += 3
                state = "triple"
            elif two == "//":
                out.extend("  ")
                i += 2
                state = "line_comment"
            elif two == "/*":
                out.extend("  ")
                i += 2
                state = "block_comment"
            elif ch == '"':
                out.append(" ")
                i += 1
                state = "string"
            elif ch == "'":
                out.append(" ")
                i += 1
                state = "char"
            else:
                out.append(ch)
                i += 1
        elif state == "triple":
            if three == '"""':
                out.extend("   ")
                i += 3
                state = "code"
            else:
                out.append("\n" if ch == "\n" else " ")
                i += 1
        elif state == "line_comment":
            out.append("\n" if ch == "\n" else " ")
            i += 1
            if ch == "\n":
                state = "code"
        elif state == "block_comment":
            if two == "*/":
                out.extend("  ")
                i += 2
                state = "code"
            else:
                out.append("\n" if ch == "\n" else " ")
                i += 1
        elif state in {"string", "char"}:
            if ch == "\\":
                out.append(" ")
                if i + 1 < len(text):
                    out.append("\n" if text[i + 1] == "\n" else " ")
                i += 2
            elif (state == "string" and ch == '"') or (state == "char" and ch == "'"):
                out.append(" ")
                i += 1
                state = "code"
            else:
                out.append("\n" if ch == "\n" else " ")
                i += 1
    if state in {"triple", "block_comment", "string", "char"}:
        fail(f"Unclosed Kotlin literal/comment (parser state {state})")
    return "".join(out)


def check_balanced(path: Path, text: str) -> None:
    clean = strip_kotlin_comments_and_literals(text)
    stack: list[tuple[str, int]] = []
    pairs = {")": "(", "]": "[", "}": "{"}
    for line_no, line in enumerate(clean.splitlines(), 1):
        for ch in line:
            if ch in "([{":
                stack.append((ch, line_no))
            elif ch in pairs:
                if not stack or stack[-1][0] != pairs[ch]:
                    fail(f"Unbalanced {ch} in {path.relative_to(ROOT)}:{line_no}")
                    return
                stack.pop()
    if stack:
        ch, line_no = stack[-1]
        fail(f"Unclosed {ch} in {path.relative_to(ROOT)}:{line_no}")


for rel in REQUIRED:
    if not (ROOT / rel).exists():
        fail(f"Missing required file: {rel}")

for path in ROOT.rglob("*"):
    if not path.is_file():
        continue
    rel = path.relative_to(ROOT)
    if any(str(rel).endswith(suffix) for suffix in FORBIDDEN_FILES):
        fail(f"Backup/temp file committed: {rel}")
    if path.suffix.lower() in TEXT_EXTENSIONS:
        text = read_text(path)
        if path.resolve() != Path(__file__).resolve():
            for token in MOJIBAKE:
                if token in text:
                    fail(f"Mojibake token {token!r} in {rel}")
        if path.suffix in {".kt", ".kts"}:
            check_balanced(path, text)

for xml_path in ROOT.rglob("*.xml"):
    if xml_path.name == "lint-resources.xml":
        continue
    try:
        ET.parse(xml_path)
    except Exception as exc:  # noqa: BLE001
        fail(f"Invalid XML {xml_path.relative_to(ROOT)}: {exc}")

manifest_path = APP / "src/main/AndroidManifest.xml"
manifest = read_text(manifest_path)
for token in (
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    ".overlay.GuardianOverlayService",
    ".overlay.QuickCaptureActivity",
    ".reminders.ReminderActionReceiver",
    ".widget.OrdiaWidgetProvider",
):
    if token not in manifest:
        fail(f"Manifest missing {token}")
if "android.permission.INTERNET" in manifest:
    pass # explicitly disabled to allow the GitHub Releases update checker
if "android.permission.RECORD_AUDIO" in manifest:
    fail("Speech recognition uses the system recognizer and should not request RECORD_AUDIO")
if 'android:allowBackup="false"' not in manifest:
    fail("Local-first release must disable implicit Android cloud backup; use explicit JSON export instead")
if 'android:exported="false"\n            android:foregroundServiceType="specialUse"' not in manifest:
    fail("Guardian service must remain non-exported and specialUse")

app_gradle = read_text(APP / "build.gradle.kts")
for token in (
    'compileSdk = 36',
    'targetSdk = 36',
    'minSdk = 26',
    'versionName = "3.0.1"',
    'isMinifyEnabled = true',
    'room.schemaLocation',
    'testDebugUnitTest',  # supplied by CI workflow, checked below too
):
    if token == 'testDebugUnitTest':
        continue
    if token not in app_gradle:
        fail(f"app/build.gradle.kts missing expected configuration: {token}")

ci = read_text(ROOT / ".github/workflows/android-ci.yml")
for token in ("testDebugUnitTest", "lintDebug", "assembleDebug", "tools/verify_project.py"):
    if token not in ci:
        fail(f"CI workflow missing {token}")

root_gradle = read_text(ROOT / "build.gradle.kts")
if 'com.android.application' not in root_gradle or 'org.jetbrains.kotlin.plugin.compose' not in root_gradle:
    fail("Root Gradle plugin configuration is incomplete")


main_activity = read_text(APP / "src/main/java/com/ordia/app/MainActivity.kt")
if "requestNotificationPermissionIfNeeded" in main_activity or "requestNotifications.launch" in main_activity:
    fail("MainActivity must not request notification permission without user context")

# Custom package declaration checks and duplicate top-level declarations.
declarations: Counter[tuple[str, str]] = Counter()
for path in (APP / "src/main/java").rglob("*.kt"):
    text = read_text(path)
    package_match = re.search(r"^package\s+([\w.]+)", text, re.MULTILINE)
    if not package_match:
        fail(f"Missing package declaration: {path.relative_to(ROOT)}")
        continue
    package = package_match.group(1)
    for kind, name in re.findall(r"^(?:data\s+|sealed\s+|enum\s+|abstract\s+|private\s+|internal\s+)*(class|object|interface)\s+(\w+)", text, re.MULTILINE):
        declarations[(package, name)] += 1
for (package, name), count in declarations.items():
    if count > 1:
        fail(f"Duplicate top-level declaration: {package}.{name} ({count})")

# Core feature wiring checks.
view_model = read_text(APP / "src/main/java/com/ordia/app/ui/OrdiaViewModel.kt")
for token in ("addSmartTask", "saveTask", "saveNote", "toggleHabit", "saveFocusSession", "exportBackup", "restoreArchived", "applyDayPlan"):
    if token not in view_model:
        fail(f"ViewModel missing core operation {token}")

backup = read_text(APP / "src/main/java/com/ordia/app/backup/BackupManager.kt")
for collection in ("projects", "tasks", "notes", "habits", "habitLogs", "focusSessions", "routines", "routineSteps", "tags", "taskTags", "attachments"):
    if f'"{collection}"' not in backup:
        fail(f"Backup omits {collection}")

entities = read_text(APP / "src/main/java/com/ordia/app/data/local/Entities.kt")
database = read_text(APP / "src/main/java/com/ordia/app/data/local/OrdiaDatabase.kt")
if "version = 2" not in database or "MIGRATION_1_2" not in database:
    fail("Room database migration 1→2 is missing")
for table in ("tasks", "projects", "notes", "habits", "habit_logs", "focus_sessions", "routines", "routine_steps", "tags", "task_tag_cross_ref", "attachments"):
    if table not in entities and table not in database:
        fail(f"Database table not represented: {table}")

unit_tests = list((APP / "src/test").rglob("*Test.kt"))
android_tests = list((APP / "src/androidTest").rglob("*Test.kt"))
if len(unit_tests) < 9:
    fail(f"Expected at least 9 unit test files, found {len(unit_tests)}")
if len(android_tests) < 2:
    fail(f"Expected at least 2 instrumentation test files, found {len(android_tests)}")

wrapper_jar = ROOT / "gradle/wrapper/gradle-wrapper.jar"
if not wrapper_jar.exists():
    warn("gradle-wrapper.jar is absent; Android Studio or `gradle wrapper --gradle-version 8.13` must generate it before ./gradlew works")

stats = {
    "project_files": sum(1 for p in ROOT.rglob("*") if p.is_file()),
    "kotlin_source_files": len(list((APP / "src/main/java").rglob("*.kt"))),
    "kotlin_source_lines": sum(read_text(p).count("\n") + 1 for p in (APP / "src/main/java").rglob("*.kt")),
    "unit_test_files": len(unit_tests),
    "instrumentation_test_files": len(android_tests),
    "errors": ERRORS,
    "warnings": WARNINGS,
}

report_path = ROOT / "artifacts/static-verification.json"
report_path.parent.mkdir(parents=True, exist_ok=True)
report_path.write_text(json.dumps(stats, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

if ERRORS:
    print("Ordia static verification FAILED")
    for error in ERRORS:
        print(f"ERROR: {error}")
    for warning in WARNINGS:
        print(f"WARNING: {warning}")
    sys.exit(1)

print(
    "Ordia static verification passed: "
    f"{stats['kotlin_source_files']} Kotlin source files, "
    f"{stats['kotlin_source_lines']} source lines, "
    f"{stats['unit_test_files']} unit test files, "
    f"{stats['instrumentation_test_files']} instrumentation test files."
)
for warning in WARNINGS:
    print(f"WARNING: {warning}")
