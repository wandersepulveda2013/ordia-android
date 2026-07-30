from __future__ import annotations

import hashlib
import json
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
import xml.etree.ElementTree as ET
from pathlib import Path


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def is_packaged_file(root: Path, path: Path) -> bool:
    if not path.is_file() or path.name == "PACKAGE_SHA256.json":
        return False
    relative = path.relative_to(root)
    return "__pycache__" not in relative.parts and path.suffix.lower() not in {".pyc", ".pyo"} and path.name != ".DS_Store"


def require_tokens(path: Path, tokens: tuple[str, ...]) -> None:
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            fail(f"Falta el control esperado {token!r} en {path.relative_to(path.parents[5] if len(path.parents) > 5 else path.parent)}")


def forbid_tokens(path: Path, tokens: tuple[str, ...]) -> None:
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token in text:
            fail(f"Permanece el patrón prohibido {token!r} en {path}")


def run_kotlin(name: str, sources: list[Path], checks: str) -> None:
    kotlinc = shutil.which("kotlinc")
    java = shutil.which("java")
    if not kotlinc or not java:
        print(f"Aviso: Kotlin/Java no está disponible; se omite {name}.")
        return
    with tempfile.TemporaryDirectory(prefix=f"ordia-{name.lower()}-") as temp_dir:
        temp = Path(temp_dir)
        check_file = temp / "Checks.kt"
        check_file.write_text(textwrap.dedent(checks).strip() + "\n", encoding="utf-8")
        jar = temp / "checks.jar"
        result = subprocess.run(
            [kotlinc, *map(str, sources), str(check_file), "-include-runtime", "-d", str(jar)],
            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90,
        )
        if result.returncode != 0:
            fail(f"{name} no compila de forma aislada:\n{result.stderr[-5000:]}")
        runtime = subprocess.run(
            [java, "-jar", str(jar)], text=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30,
        )
        if runtime.returncode != 0:
            fail(f"Las pruebas ejecutables de {name} fallaron:\n{runtime.stderr[-5000:]}")


def compile_guardian_engine(root: Path) -> None:
    kotlinc = shutil.which("kotlinc")
    if not kotlinc:
        return
    with tempfile.TemporaryDirectory(prefix="ordia-guardian-src-") as temp_dir:
        temp = Path(temp_dir)
        source = temp / "src"
        for relative in ("com/ordia/app/domain", "com/ordia/app/data/local", "com/ordia/app/data/preferences"):
            (source / relative).mkdir(parents=True, exist_ok=True)
        shutil.copy2(root / "files/app/src/main/java/com/ordia/app/domain/GuardianEngine.kt", source / "com/ordia/app/domain/GuardianEngine.kt")
        (source / "com/ordia/app/data/local/Stubs.kt").write_text(textwrap.dedent("""
            package com.ordia.app.data.local
            data class TaskEntity(val title:String="", val completed:Boolean=false, val archived:Boolean=false, val completedAt:Long?=null, val dueAt:Long?=null)
            data class HabitEntity(val id:Long=0, val targetPerPeriod:Int=1)
            data class HabitLogEntity(val habitId:Long, val epochDay:Long, val count:Int=1)
            data class FocusSessionEntity(val startedAt:Long, val actualMinutes:Int=0, val completed:Boolean=false)
            data class NoteEntity(val archived:Boolean=false)
        """).strip()+"\n", encoding="utf-8")
        (source / "com/ordia/app/data/preferences/Stubs.kt").write_text(textwrap.dedent("""
            package com.ordia.app.data.preferences
            import java.time.LocalDate
            enum class GuardianSpecies(val label:String, val defaultName:String, val description:String) {
                LUMI("Lumi","Lumi",""), MOSS("Moss","Moss",""), ORBIT("Orbit","Orbit",""),
                EMBER("Ember","Ember",""), TIDE("Tide","Tide",""), NOVA("Nova","Nova","")
            }
            data class UserPreferences(
                val guardianName:String="Lumi", val guardianSpecies:GuardianSpecies=GuardianSpecies.LUMI,
                val guardianBond:Int=0, val guardianExperience:Int=0, val guardianLastInteraction:Long=0,
                val guardianLastEvent:String="welcome", val guardianInteractionEpochDay:Long=LocalDate.now().toEpochDay(),
                val guardianInteractionsToday:Int=0
            )
            class PreferencesRepository { companion object { const val DAILY_INTERACTION_LIMIT = 12 } }
        """).strip()+"\n", encoding="utf-8")
        (source / "com/ordia/app/domain/Stubs.kt").write_text(textwrap.dedent("""
            package com.ordia.app.domain
            import com.ordia.app.data.local.*
            import java.time.*
            object DateRules { fun toLocalDate(ms:Long, zone:ZoneId=ZoneId.systemDefault()):LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }
            object HabitRules {
                fun countFor(logs:List<HabitLogEntity>, id:Long, date:LocalDate):Int = logs.firstOrNull { it.habitId == id && it.epochDay == date.toEpochDay() }?.count ?: 0
                fun currentStreak(habit:HabitEntity, logs:List<HabitLogEntity>):Int = 0
            }
            object TaskRules { fun isOverdue(task:TaskEntity, now:Long=System.currentTimeMillis()):Boolean = !task.completed && task.dueAt?.let { it < now } == true }
        """).strip()+"\n", encoding="utf-8")
        checks = source / "GuardianChecks.kt"
        checks.write_text(textwrap.dedent("""
            import com.ordia.app.domain.GuardianEngine
            fun main() {
                check(GuardianEngine.bondExperience(9_999) == 500)
                check(GuardianEngine.stageForExperience(GuardianEngine.effectiveExperience(0, 0, 9_999)) == GuardianEngine.Stage.HATCHLING)
                check(GuardianEngine.effectiveExperience(300, 800, 80) == 820)
                check(GuardianEngine.isQuietHours(22 * 60, 7 * 60, 23 * 60))
                check(!GuardianEngine.isQuietHours(22 * 60, 7 * 60, 12 * 60))
            }
        """).strip()+"\n", encoding="utf-8")
        sources = list(source.rglob("*.kt"))
        jar = temp / "guardian.jar"
        result = subprocess.run([kotlinc, *map(str,sources), "-include-runtime", "-d", str(jar)], text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90)
        if result.returncode != 0:
            fail(f"GuardianEngine no compila de forma aislada:\n{result.stderr[-5000:]}")
        runtime = subprocess.run(["java", "-jar", str(jar)], text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
        if runtime.returncode != 0:
            fail(f"Las reglas ejecutables del guardián fallaron:\n{runtime.stderr[-5000:]}")


def check_balanced_kotlin(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    stack: list[tuple[str,int]] = []
    pairs={')':'(',']':'[','}':'{'}
    state="code"; i=0; line=1
    while i < len(text):
        c=text[i]; n=text[i+1] if i+1<len(text) else ''
        if c=='\n': line+=1
        if state=="line":
            if c=='\n': state="code"
        elif state=="block":
            if c=='*' and n=='/': state="code"; i+=1
        elif state=="string":
            if c=='\\': i+=1
            elif c=='"': state="code"
        elif state=="triple":
            if text.startswith('"""',i): state="code"; i+=2
        elif state=="char":
            if c=='\\': i+=1
            elif c=="'": state="code"
        else:
            if c=='/' and n=='/': state="line"; i+=1
            elif c=='/' and n=='*': state="block"; i+=1
            elif text.startswith('"""',i): state="triple"; i+=2
            elif c=='"': state="string"
            elif c=="'": state="char"
            elif c in '([{': stack.append((c,line))
            elif c in ')]}':
                if not stack or stack[-1][0] != pairs[c]: fail(f"Delimitador inesperado {c} en {path}, línea {line}")
                stack.pop()
        i+=1
    if state in {"string","triple","char","block"}: fail(f"Literal o comentario sin cerrar en {path}")
    if stack: fail(f"Delimitador {stack[-1][0]} sin cerrar en {path}, línea {stack[-1][1]}")


def check_balanced_powershell(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    stack: list[tuple[str, int]] = []
    pairs = {")": "(", "]": "[", "}": "{"}
    state = "code"
    line = 1
    index = 0
    line_start = True
    while index < len(text):
        char = text[index]
        next_char = text[index + 1] if index + 1 < len(text) else ""
        if char == "\n":
            line += 1
            line_start = True
            if state == "comment":
                state = "code"
            index += 1
            continue
        if state == "comment":
            index += 1
            continue
        if state == "single":
            if char == "'":
                if next_char == "'":
                    index += 2
                    continue
                state = "code"
            index += 1
            continue
        if state == "double":
            if char == "`":
                index += 2
                continue
            if char == '"':
                state = "code"
            index += 1
            continue
        if state in {"here_single", "here_double"}:
            terminator = "'@" if state == "here_single" else '"@'
            if line_start and text.startswith(terminator, index):
                state = "code"
                index += 2
                line_start = False
                continue
            line_start = False
            index += 1
            continue
        if char in " \t\r":
            index += 1
            continue
        if line_start and text.startswith("@'", index):
            state = "here_single"
            index += 2
            line_start = False
            continue
        if line_start and text.startswith('@"', index):
            state = "here_double"
            index += 2
            line_start = False
            continue
        line_start = False
        if char == "#":
            state = "comment"
        elif char == "'":
            state = "single"
        elif char == '"':
            state = "double"
        elif char == "`":
            index += 1
        elif char in "([{":
            stack.append((char, line))
        elif char in ")]}":
            if not stack or stack[-1][0] != pairs[char]:
                fail(f"Delimitador PowerShell inesperado {char} en {path}, línea {line}")
            stack.pop()
        index += 1
    if state in {"single", "double", "here_single", "here_double"}:
        fail(f"Cadena PowerShell sin cerrar en {path}")
    if stack:
        fail(f"Delimitador PowerShell {stack[-1][0]} sin cerrar en {path}, línea {stack[-1][1]}")


def main() -> None:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    required = (
        "APLICAR_ORDIA_3.ps1", "INICIAR_ORDIA_3.bat", "DIAGNOSTICO_ORDIA_3.bat",
        "PACKAGE_SHA256.json", "files/app/build.gradle.kts",
        "files/app/src/main/AndroidManifest.xml", "files/app/src/debug/AndroidManifest.xml",
        "files/app/src/main/res/xml/ordia_update_paths.xml",
        "files/app/src/main/java/com/ordia/app/MainActivity.kt",
        "files/app/src/main/java/com/ordia/app/OrdiaApplication.kt",
        "files/app/src/main/java/com/ordia/app/di/AppContainer.kt",
        "files/app/src/main/java/com/ordia/app/data/preferences/PreferencesRepository.kt",
        "files/app/src/main/java/com/ordia/app/domain/GuardianEngine.kt",
        "files/app/src/main/java/com/ordia/app/backup/BackupManager.kt",
        "files/app/src/main/java/com/ordia/app/backup/BackupSecurityRules.kt",
        "files/app/src/main/java/com/ordia/app/reminders/ReminderScheduler.kt",
        "files/app/src/main/java/com/ordia/app/updates/OrdiaUpdateManager.kt",
        "files/app/src/main/java/com/ordia/app/updates/UpdateSecurityRules.kt",
        "files/app/src/main/java/com/ordia/app/updates/OrdiaUpdateWorker.kt",
        "files/app/src/test/java/com/ordia/app/backup/BackupSecurityRulesTest.kt",
        "files/app/src/test/java/com/ordia/app/updates/UpdateSecurityRulesTest.kt",
        "files/.github/workflows/android-ci.yml", "tools/patch_ordia_viewmodel.py"
    )
    for rel in required:
        if not (root / rel).is_file():
            fail(f"Falta {rel}")
    for path in root.rglob("*"):
        if path.is_symlink():
            fail(f"El paquete contiene un enlace simbólico no permitido: {path.relative_to(root)}")
    check_balanced_powershell(root / "APLICAR_ORDIA_3.ps1")

    for xml in (root / "files/app/src").rglob("*.xml"):
        try:
            ET.parse(xml)
        except ET.ParseError as exc:
            fail(f"XML inválido en {xml.relative_to(root)}: {exc}")

    workflow = root / "files/.github/workflows/android-ci.yml"
    try:
        import yaml  # type: ignore
        yaml.safe_load(workflow.read_text(encoding="utf-8"))
    except ImportError:
        pass
    except Exception as exc:
        fail(f"Workflow YAML inválido: {exc}")
    for ref in re.findall(r"^\s*uses:\s*([^\s#]+)", workflow.read_text(encoding="utf-8"), re.M):
        if not re.fullmatch(r"[^@]+@[0-9a-f]{40}", ref):
            fail(f"Acción no fijada a SHA: {ref}")

    main_manifest = root / "files/app/src/main/AndroidManifest.xml"
    debug_manifest = root / "files/app/src/debug/AndroidManifest.xml"
    require_tokens(main_manifest, ('android:usesCleartextTraffic="false"', 'android:allowBackup="false"'))
    forbid_tokens(main_manifest, (
        'android.permission.INTERNET', 'android.permission.REQUEST_INSTALL_PACKAGES',
        '.updates.UpdateInstallActivity', '.updates.UpdateDownloadReceiver', '${applicationId}.update-files'
    ))
    require_tokens(debug_manifest, (
        'android.permission.INTERNET', 'android.permission.REQUEST_INSTALL_PACKAGES',
        '${applicationId}.update-files', '.updates.UpdateInstallActivity', '.updates.UpdateDownloadReceiver',
        'android:exported="false"'
    ))

    build = root / "files/app/build.gradle.kts"
    require_tokens(build, (
        '1_300_000_000', 'GITHUB_RUN_ATTEMPT', '"3.0.0-local"', 'stableSigningConfigured',
        'buildConfigField("boolean", "SELF_UPDATE_ENABLED", "true")',
        'buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")'
    ))
    require_tokens(workflow, (
        'Missing ORDIA_UPDATE_KEYSTORE_BASE64', 'output-metadata.json', 'GITHUB_RUN_ATTEMPT',
        'refusing to overwrite immutable update bytes', 'persist-credentials: false',
        'sha256sum --check', 'permissions:', 'Test, lint and build pull request without secrets', 'Test, lint and build trusted main update'
    ))
    workflow_text = workflow.read_text(encoding="utf-8")
    pr_block = workflow_text.split("- name: Test, lint and build pull request without secrets", 1)[1].split("- name: Test, lint and build trusted main update", 1)[0]
    if "secrets." in pr_block or "ORDIA_KEYSTORE" in pr_block:
        fail("El trabajo de pull request no debe recibir secretos de firma.")

    backup_manager = root / "files/app/src/main/java/com/ordia/app/backup/BackupManager.kt"
    require_tokens(backup_manager, (
        'operationMutex.withLock', 'validateJsonEnvelope', 'withContext(Dispatchers.IO)',
        'unexpectedTopLevel', 'requiredCollections', 'database.withTransaction',
        'cancelAllAndAwait', 'allowGuardianEnabled = false', 'parsed.scheme?.lowercase() == "content"',
        'hasValidUnicodeScalars', 'CURRENT_VERSION = 3'
    ))
    require_tokens(root / "files/app/src/main/java/com/ordia/app/backup/BackupSecurityRules.kt", (
        'CodingErrorAction.REPORT', 'decodeUtf8Strict', 'hasValidUnicodeScalars',
        'validateJsonEnvelope', 'requiredCollections'
    ))
    require_tokens(root / "files/app/src/main/java/com/ordia/app/data/preferences/PreferencesRepository.kt", (
        'ReplaceFileCorruptionHandler', 'allowGuardianEnabled', 'ALLOWED_GUARDIAN_EVENTS',
        'hasValidUnicodeScalars'
    ))
    require_tokens(root / "files/app/src/main/java/com/ordia/app/reminders/ReminderScheduler.kt", (
        'cancelAllAndAwait', '.result.get(30, TimeUnit.SECONDS)'
    ))
    update_manager = root / "files/app/src/main/java/com/ordia/app/updates/OrdiaUpdateManager.kt"
    require_tokens(update_manager, (
        'VISIBILITY_VISIBLE', 'KEY_DOWNLOAD_STARTED_AT', 'KEY_EXPECTED_BYTES',
        'metadataSaved', 'FileProvider.getUriForFile', 'verifyArchive',
        'isTrustedReleaseAssetUrl', 'decodeUtf8Strict', 'CopyResult', 'reportedBytes'
    ))
    require_tokens(root / "files/app/src/main/java/com/ordia/app/updates/UpdateSecurityRules.kt", (
        'CodingErrorAction.REPORT', 'decodeUtf8Strict', 'expectedApkName',
        'isTrustedNetworkUrl', 'isTrustedReleaseAssetUrl'
    ))
    require_tokens(root / "files/app/src/main/java/com/ordia/app/updates/OrdiaUpdateWorker.kt", (
        'UpdateValidationWorker', 'enqueueUniqueWork', 'UpdateDownloadReceiver'
    ))
    require_tokens(root / "files/app/src/main/java/com/ordia/app/overlay/GuardianOverlayService.kt", (
        'ScrollView', 'availablePanelHeight', 'FLAG_NOT_TOUCH_MODAL'
    ))
    require_tokens(root / "files/app/src/main/java/com/ordia/app/ui/screens/SettingsScreen.kt", (
        'BuildConfig.SELF_UPDATE_ENABLED', 'BackupSecurityRules.MAX_UTF8_BYTES',
        'BackupSecurityRules.decodeUtf8Strict', 'startGuardian(context: Context): Boolean',
        'notificationsGranted'
    ))
    require_tokens(root / "files/app/src/main/java/com/ordia/app/OrdiaApplication.kt", (
        'BuildConfig.SELF_UPDATE_ENABLED', 'OrdiaUpdateManager.cancelSchedule'
    ))
    require_tokens(root / "APLICAR_ORDIA_3.ps1", (
        'Test-TrustedOrigin', 'Clear-SigningEnvironment', 'output-metadata.json', 'apksigner',
        'git push -u origin "HEAD:$TargetBranch"', '$BuildVerified', '$ChangesCommitted',
        '$PushCompleted', 'WaitForExit(60000)', 'Nunca se revierte un commit ya publicado', '-File -Force',
        'No se pudo crear la rama local de respaldo', 'git checkout -b $TargetBranch $OriginalHead',
        'git checkout $OriginalBranch', "$explorerArgument = '/select,\"{0}\"' -f $DesktopApk",
        'Start-Process -FilePath "explorer.exe" -ArgumentList $explorerArgument'
    ))

    require_tokens(root / "INICIAR_ORDIA_3.bat", (
        'ExecutionPolicy Bypass', 'APLICAR_ORDIA_3.ps1', 'Unblock-File', 'pause',
        'System.Management.Automation.Language.Parser', '-File "%ORDIA_SCRIPT%"', 'exit /b 91'
    ))
    require_tokens(root / "DIAGNOSTICO_ORDIA_3.bat", (
        'gradlew.bat', 'powershell.exe', 'git.exe', 'python.exe',
        'System.Management.Automation.Language.Parser'
    ))

    forbid_tokens(root / "APLICAR_ORDIA_3.ps1", (
        'Start-Process explorer.exe "/select,`"$DesktopApk`""',
    ))

    forbidden = (
        "VISIBILITY_VISIBLE_NOTIFY_COMPLETED", "--clobber", "expectedApkNames(",
        "isTrustedGitHubUrl(", "toString(Charsets.UTF_8)"
    )
    all_text = "\n".join(
        path.read_text(encoding="utf-8", errors="ignore")
        for path in root.rglob("*")
        if path.is_file()
        and path.suffix.lower() in {".kt", ".kts", ".yml", ".ps1"}
        and "tools" not in path.relative_to(root).parts
    )
    for token in forbidden:
        if token in all_text:
            fail(f"Permanece un patrón inseguro u obsoleto: {token}")

    manifest_text = (root / "files/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    if "AccessibilityService" in manifest_text or "BIND_ACCESSIBILITY_SERVICE" in manifest_text:
        fail("Ordia 3.0 no debe incluir lectura invasiva mediante AccessibilityService.")

    for kotlin in (root / "files/app/src").rglob("*.kt"):
        check_balanced_kotlin(kotlin)
        text = kotlin.read_text(encoding="utf-8")
        if "TODO(" in text or "NotImplementedError" in text:
            fail(f"Implementación incompleta en {kotlin.relative_to(root)}")

    compile_guardian_engine(root)
    run_kotlin("ContextualAnalyzer", [
        root / "files/app/src/main/java/com/ordia/app/context/ContextualSuggestion.kt",
        root / "files/app/src/main/java/com/ordia/app/context/ContextualAnalyzer.kt",
    ], r'''
        import com.ordia.app.context.*
        import java.time.LocalDateTime
        import java.time.ZoneId
        fun main() {
            val zone = ZoneId.of("America/Santo_Domingo")
            val now = LocalDateTime.of(2026,7,30,8,0).atZone(zone).toInstant().toEpochMilli()
            val visit = checkNotNull(ContextualAnalyzer.analyze("Mañana iré a tu casa a las 5 pm", now, zone))
            check(visit.kind == ContextualKind.EVENT && visit.dueAt != null && visit.confidence >= .8)
            check(ContextualAnalyzer.analyze("Estoy estudiando para el examen", now, zone)?.kind == ContextualKind.STUDY)
            check(ContextualAnalyzer.analyze("Mi contraseña es 123456", now, zone) == null)
            val a = checkNotNull(ContextualAnalyzer.analyze("Mañana debo pagar", now, zone))
            val b = checkNotNull(ContextualAnalyzer.analyze("  mañana   debo pagar ", now, zone))
            check(a.id == b.id)
        }
    ''')
    run_kotlin("UpdateSecurityRules", [root / "files/app/src/main/java/com/ordia/app/updates/UpdateSecurityRules.kt"], r'''
        import com.ordia.app.updates.UpdateSecurityRules
        fun main() {
            val h = "a".repeat(64)
            check(UpdateSecurityRules.parseVersionCodeFromTag("v3.0.4-code-1000000401") == 1000000401)
            check(UpdateSecurityRules.selectExpectedApk(listOf("Ordia-3.0-code-20.apk"), 20) == "Ordia-3.0-code-20.apk")
            check(UpdateSecurityRules.selectExpectedApk(listOf("ordia-3.0-code-20.apk"), 20) == null)
            check(UpdateSecurityRules.selectExpectedApk(listOf("Ordia-3.0-code-20.apk", "ordia-3.0-code-20.apk"), 20) == null)
            check(UpdateSecurityRules.selectExpectedApk(listOf("Ordia-3.0.apk"), 20) == null)
            check(UpdateSecurityRules.parseChecksum("$h  Ordia-3.0-code-20.apk", "Ordia-3.0-code-20.apk") == h)
            check(UpdateSecurityRules.parseChecksum("$h *Ordia-3.0-code-20.apk", "Ordia-3.0-code-20.apk") == null)
            check(UpdateSecurityRules.decodeUtf8Strict(byteArrayOf(0x61)) == "a")
            check(UpdateSecurityRules.decodeUtf8Strict(byteArrayOf(0xC3.toByte(), 0x28)) == null)
            check(UpdateSecurityRules.isTrustedNetworkUrl("https://api.github.com/repos/wandersepulveda2013/ordia-android/releases/latest"))
            check(!UpdateSecurityRules.isTrustedNetworkUrl("https://github.com.evil.example/file.apk"))
        }
    ''')
    run_kotlin("BackupSecurityRules", [root / "files/app/src/main/java/com/ordia/app/backup/BackupSecurityRules.kt"], r'''
        import com.ordia.app.backup.BackupSecurityRules
        fun main() {
            check(BackupSecurityRules.validateJsonEnvelope("{\"tasks\":[],\"notes\":[]}") == null)
            check(BackupSecurityRules.validateJsonEnvelope("{\"tasks\":[],\"\\u0074asks\":[]}") != null)
            check(BackupSecurityRules.validateJsonEnvelope("{} trailing") != null)
            check(BackupSecurityRules.validateJsonEnvelope("{}{}") != null)
            check(BackupSecurityRules.validateJsonEnvelope("[".repeat(65) + "]".repeat(65)) != null)
            check(BackupSecurityRules.decodeUtf8Strict(byteArrayOf(0x61)) == "a")
            check(BackupSecurityRules.decodeUtf8Strict(byteArrayOf(0xC3.toByte(), 0x28)) == null)
            check(BackupSecurityRules.hasValidUnicodeScalars("texto válido"))
            check(!BackupSecurityRules.hasValidUnicodeScalars("\uD800"))
            check(BackupSecurityRules.parseUniqueDayList("1,3,7", 1..7) == setOf(1,3,7))
            check(BackupSecurityRules.parseUniqueDayList("1,1", 1..7) == null)
        }
    ''')

    expected = json.loads((root / "PACKAGE_SHA256.json").read_text(encoding="utf-8"))
    actual = {
        str(path.relative_to(root)).replace("\\", "/"): sha256(path)
        for path in root.rglob("*") if is_packaged_file(root, path)
    }
    if set(actual) != set(expected):
        missing = sorted(set(actual) - set(expected))
        stale = sorted(set(expected) - set(actual))
        fail(f"Manifiesto SHA desactualizado. Sin hash: {missing}; obsoletos: {stale}")
    for rel, digest in expected.items():
        if actual[rel].lower() != digest.lower():
            fail(f"SHA-256 no coincide para {rel}")

    print(
        f"Paquete Ordia 3.0 auditoría adversarial: {len(expected)} archivos protegidos; "
        "manifiestos por variante, XML, YAML, Kotlin puro, restauración, actualización e integridad verificados."
    )

if __name__ == "__main__":
    main()
