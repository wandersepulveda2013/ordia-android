$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$PackageRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$SourceRoot = Join-Path $PackageRoot "files"
$ToolsRoot = Join-Path $PackageRoot "tools"
$DefaultRepo = "C:\Users\wsepulveda\Documents\GitHub\ordia-android"
$Repo = if ($env:ORDIA_REPO) { $env:ORDIA_REPO } else { $DefaultRepo }
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupRoot = Join-Path $env:LOCALAPPDATA "Ordia\backups\before-3.0-$Timestamp"
$Log = Join-Path $BackupRoot "ordia-3.0-build.log"
$Result = Join-Path ([Environment]::GetFolderPath("Desktop")) "ORDIA-3.0-RESULTADO.txt"
$DesktopApk = Join-Path ([Environment]::GetFolderPath("Desktop")) "Ordia-3.0.apk"
$StashCreated = $false
$OriginalBranch = $null
$OriginalHead = $null
$SigningRoot = Join-Path $env:LOCALAPPDATA "Ordia\signing"
$StableKeystore = Join-Path $SigningRoot "ordia-update.keystore"
$SigningCredentials = Join-Path $SigningRoot "credentials.clixml"
$RemoteSigningReady = $false
$BuildVerified = $false
$ChangesCommitted = $false
$PushCompleted = $false
$TrustedRemote = $false
$OriginUrl = ""
$TargetBranch = "feature/ordia-3.0"

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host $Text -ForegroundColor Yellow
}


function Test-TrustedOrigin([string]$Url) {
    if ([string]::IsNullOrWhiteSpace($Url)) { return $false }
    $normalized = $Url.Trim().TrimEnd('/')
    return @(
        "https://github.com/wandersepulveda2013/ordia-android",
        "https://github.com/wandersepulveda2013/ordia-android.git",
        "git@github.com:wandersepulveda2013/ordia-android",
        "git@github.com:wandersepulveda2013/ordia-android.git",
        "ssh://git@github.com/wandersepulveda2013/ordia-android",
        "ssh://git@github.com/wandersepulveda2013/ordia-android.git"
    ) -contains $normalized
}

function Clear-SigningEnvironment {
    foreach ($name in @("ORDIA_KEYSTORE_PATH", "ORDIA_KEYSTORE_PASSWORD", "ORDIA_KEY_ALIAS", "ORDIA_KEY_PASSWORD")) {
        [Environment]::SetEnvironmentVariable($name, $null, "Process")
    }
}

function Set-GitHubSecret([string]$GhPath, [string]$Name, [string]$Value) {
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $GhPath
    $startInfo.Arguments = "secret set $Name --repo wandersepulveda2013/ordia-android"
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) { return $false }
        $process.StandardInput.Write($Value)
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(60000)) {
            try { $process.Kill($true) } catch { }
            Write-Warning "GitHub CLI excedió el límite de 60 segundos al guardar $Name."
            return $false
        }
        if ($process.ExitCode -ne 0) {
            $errorText = $process.StandardError.ReadToEnd()
            if (-not [string]::IsNullOrWhiteSpace($errorText)) { Write-Warning $errorText.Trim() }
        }
        return $process.ExitCode -eq 0
    } finally {
        $process.Dispose()
    }
}

function Restore-OriginalWork {
    param([switch]$BuildFailed)
    Set-Location $Repo
    if ($BuildFailed -and -not $BuildVerified -and -not $PushCompleted) {
        Write-Warning "Restaurando la rama de trabajo porque las pruebas o la compilación no terminaron correctamente..."
        & git reset --hard $OriginalHead *> $null
        & git clean -fd *> $null
    }
    if (-not [string]::IsNullOrWhiteSpace($OriginalBranch)) {
        & git checkout $OriginalBranch *> $null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "No se pudo volver automáticamente a $OriginalBranch. Tus cambios previos siguen protegidos en git stash."
            return
        }
    }
    if ($StashCreated) {
        & git stash pop
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Git no pudo reaplicar automáticamente todos tus cambios anteriores. Permanecen protegidos en git stash."
        }
    }
}

function Validate-PackageIntegrity {
    param([string]$PackageRoot)
    $manifestPath = Join-Path $PackageRoot "PACKAGE_SHA256.json"
    if (-not (Test-Path $manifestPath)) { throw "Falta PACKAGE_SHA256.json en $PackageRoot" }

    # Parse JSON to hashtable (PS5.1 compatible)
    Add-Type -AssemblyName System.Web.Extensions -ErrorAction Stop
    $serializer = New-Object System.Web.Script.Serialization.JavaScriptSerializer
    $expected = $serializer.DeserializeObject((Get-Content $manifestPath -Raw -Encoding UTF8))

    $actual = @{}
    Get-ChildItem $PackageRoot -Recurse -File -Force | ForEach-Object {
        $relative = $_.FullName.Substring($PackageRoot.Length).TrimStart('\','/').Replace('\','/')
        # Skip manifest itself and generated/cache artifacts
        if ($relative -eq "PACKAGE_SHA256.json") { return }
        if ($relative -match '__pycache__' -or $relative -match '\.pyc$' -or $relative -match '\.pyo$' -or $_.Name -eq '.DS_Store') { return }
        # Path safety: reject parent traversal and absolute paths
        if ($relative -match '(^|/)\.\.(/|$)' -or [System.IO.Path]::IsPathRooted($relative)) {
            throw "Ruta insegura detectada en el paquete: $relative"
        }
        $actual[$relative] = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }

    # Compare key sets (use @() to ensure array even with StrictMode)
    $expectedKeys = @($expected.Keys | Sort-Object)
    $actualKeys = @($actual.Keys | Sort-Object)
    $missing = @($actualKeys | Where-Object { -not $expected.ContainsKey($_) })
    $stale = @($expectedKeys | Where-Object { -not $actual.ContainsKey($_) })

    if ($missing.Count -gt 0 -or $stale.Count -gt 0) {
        $msg = "Manifiesto SHA desactualizado."
        if ($missing.Count -gt 0) { $msg += " Sin hash: $($missing -join ', ')" }
        if ($stale.Count -gt 0) { $msg += " Obsoletos: $($stale -join ', ')" }
        throw $msg
    }

    # Compare hashes
    $mismatch = New-Object System.Collections.ArrayList
    foreach ($rel in $expectedKeys) {
        if ($actual[$rel] -ne $expected[$rel]) {
            [void]$mismatch.Add($rel)
        }
    }
    if ($mismatch.Count -gt 0) { throw "SHA-256 no coincide para: $($mismatch -join ', ')" }

    Write-Host "Paquete Ordia 3.0 integridad verificada: $($expectedKeys.Count) archivos protegidos." -ForegroundColor Green
}

function Invoke-ViewModelPatch {
    param([string]$RepoPath)
    $vmPath = Join-Path $RepoPath "app\src\main\java\com\ordia\app\ui\OrdiaViewModel.kt"
    if (-not (Test-Path $vmPath)) { Write-Warning "No se encontró OrdiaViewModel.kt en $vmPath"; return }

    # Read and normalize line endings to \n for reliable replacement
    $text = [IO.File]::ReadAllText($vmPath) -replace "`r`n", "`n"
    $original = $text

    # --- Remove obsolete guardian XP hooks from 2.0 draft ---
    $text = $text.Replace("            if (completing) preferencesRepository.addGuardianExperience(12, `"task_complete`")`n", "")
    $text = $text.Replace("            if (note.id == 0L) preferencesRepository.addGuardianExperience(2, `"note_created`")`n", "")
    $text = $text.Replace("            if (completed) preferencesRepository.addGuardianExperience((5 + actual / 5).coerceAtMost(30), `"focus_complete`")`n", "")

    # --- Simplify habit logging (remove XP hook) ---
    $oldHabit = (@"
            if (current >= habit.targetPerPeriod) {
                habitRepository.removeLog(habit.id, date.toEpochDay())
            } else {
                habitRepository.log(HabitLogEntity(habit.id, date.toEpochDay(), current + 1))
                val completed = current + 1 >= habit.targetPerPeriod
                preferencesRepository.addGuardianExperience(if (completed) 10 else 3, if (completed) "habit_complete" else "habit_progress")
            }
"@) -replace "`r`n", "`n"
    $newHabit = (@"
            if (current >= habit.targetPerPeriod) habitRepository.removeLog(habit.id, date.toEpochDay())
            else habitRepository.log(HabitLogEntity(habit.id, date.toEpochDay(), current + 1))
"@) -replace "`r`n", "`n"
    $text = $text.Replace($oldHabit, $newHabit)

    # --- Conditional import: kotlinx.coroutines.flow.first ---
    if (-not $text.Contains("import kotlinx.coroutines.flow.first")) {
        $text = $text.Replace("import kotlinx.coroutines.flow.combine`n", "import kotlinx.coroutines.flow.combine`nimport kotlinx.coroutines.flow.first`n")
    }

    # --- Replace importBackup function ---
    $oldImport = (@"
    fun importBackup(raw: String) = viewModelScope.launch {
        val result = backupManager.importJson(raw)
        _events.emit(UiEvent.Message(result.message))
        updateWidget()
    }
"@) -replace "`r`n", "`n"
    $newImport = (@"
    fun importBackup(raw: String) = viewModelScope.launch {
        val result = backupManager.importJson(raw)
        if (result.success) {
            val restored = preferencesRepository.preferences.first()
            if (restored.autoUpdateEnabled) com.ordia.app.updates.OrdiaUpdateManager.schedule(appContext)
            else com.ordia.app.updates.OrdiaUpdateManager.cancelSchedule(appContext)
            appContext.stopService(android.content.Intent(appContext, com.ordia.app.overlay.GuardianOverlayService::class.java))
        }
        _events.emit(UiEvent.Message(result.message))
        updateWidget()
    }
"@) -replace "`r`n", "`n"
    if ($text.Contains($oldImport)) {
        $text = $text.Replace($oldImport, $newImport)
    } elseif (-not $text.Contains("com.ordia.app.updates.OrdiaUpdateManager.schedule(appContext)")) {
        throw "No se pudo aplicar el parche importBackup de forma segura."
    }

    # --- Task-state integrity improvements ---
    $text = $text.Replace(
        "val pendingTasks: List<TaskEntity> get() = rootTasks.filter { !it.completed && !it.archived }",
        "val pendingTasks: List<TaskEntity> get() = rootTasks.filter { !it.completed && !it.archived && it.status != TaskStatus.CANCELLED }"
    )
    $text = $text.Replace(
        "val todayTasks: List<TaskEntity> get() = pendingTasks.filter { TaskRules.isDueToday(it) }",
        "val todayTasks: List<TaskEntity> get() = pendingTasks.filter { TaskRules.isDueToday(it) && !TaskRules.isOverdue(it) }"
    )
    $text = $text.Replace(
        "tasks.filter { it.parentTaskId == parentId && !it.archived }.sortedBy { it.sortOrder }",
        "tasks.filter { it.parentTaskId == parentId && !it.archived && it.status != TaskStatus.CANCELLED }.sortedBy { it.sortOrder }"
    )
    $text = $text.Replace(
        "val related = rootTasks.filter { it.projectId == projectId && !it.archived }",
        "val related = rootTasks.filter { it.projectId == projectId && !it.archived && it.status != TaskStatus.CANCELLED }"
    )
    $text = $text.Replace(
        "if (normalized.reminderAt != null || normalized.dueAt != null) reminderScheduler.schedule(normalized.copy(id = id)) else reminderScheduler.cancel(id)",
        "if (normalized.status != TaskStatus.CANCELLED && !normalized.completed && (normalized.reminderAt != null || normalized.dueAt != null)) reminderScheduler.schedule(normalized.copy(id = id)) else reminderScheduler.cancel(id)"
    )

    # --- Conditional imports for TaskMutationGate and withLock ---
    if (-not $text.Contains("import com.ordia.app.domain.TaskMutationGate")) {
        $text = $text.Replace("import com.ordia.app.domain.TaskRules`n", "import com.ordia.app.domain.TaskRules`nimport com.ordia.app.domain.TaskMutationGate`n")
    }
    if (-not $text.Contains("import kotlinx.coroutines.sync.withLock")) {
        $text = $text.Replace("import kotlinx.coroutines.launch`n", "import kotlinx.coroutines.launch`nimport kotlinx.coroutines.sync.withLock`n")
    }

    # --- Replace toggleTask with mutex-guarded version ---
    $oldToggle = (@"
    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val completing = !task.completed
            taskRepository.update(
                task.copy(
                    completed = completing,
                    status = if (completing) TaskStatus.COMPLETED else if (task.dueAt == null) TaskStatus.INBOX else TaskStatus.PLANNED,
                    completedAt = if (completing) now else null,
                    updatedAt = now
                )
            )
            if (completing) {
                reminderScheduler.cancel(task.id)
                RecurrenceEngine.nextOccurrence(task, now)?.let { next ->
                    val nextId = taskRepository.add(next)
                    reminderScheduler.schedule(next.copy(id = nextId))
                }
            } else {
                reminderScheduler.schedule(task)
            }
            updateWidget()
        }
    }
"@) -replace "`r`n", "`n"
    $newToggle = (@"
    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            TaskMutationGate.mutex.withLock {
                val current = taskRepository.get(task.id) ?: return@withLock
                val now = System.currentTimeMillis()
                val completing = !current.completed
                val updated = current.copy(
                    completed = completing,
                    status = if (completing) TaskStatus.COMPLETED else if (current.dueAt == null) TaskStatus.INBOX else TaskStatus.PLANNED,
                    completedAt = if (completing) now else null,
                    updatedAt = now
                )
                taskRepository.update(updated)
                if (completing) {
                    reminderScheduler.cancel(current.id)
                    RecurrenceEngine.nextOccurrence(current, now)?.let { next ->
                        val nextId = taskRepository.add(next)
                        reminderScheduler.schedule(next.copy(id = nextId))
                    }
                } else if (updated.reminderAt != null || updated.dueAt != null) reminderScheduler.schedule(updated)
            }
            updateWidget()
        }
    }
"@) -replace "`r`n", "`n"
    if ($text.Contains($oldToggle)) {
        $text = $text.Replace($oldToggle, $newToggle)
    } elseif (-not $text.Contains("TaskMutationGate.mutex.withLock")) {
        throw "No se pudo aplicar el parche toggleTask de forma segura."
    }

    # Write back only if changed
    if ($text -ne $original) {
        [IO.File]::WriteAllText($vmPath, $text, [System.Text.Encoding]::UTF8)
        Write-Host "ViewModel normalizado para Ordia 3.0" -ForegroundColor Green
    } else {
        Write-Host "ViewModel ya estaba normalizado" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "ORDIA 3.0.2 — INSTALADOR CORREGIDO Y VALIDADO" -ForegroundColor Cyan
Write-Host "Rediseño + guardián virtual + mascota flotante + actualización automática" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $Repo)) { throw "No se encontró el repositorio en $Repo" }
if (-not (Test-Path (Join-Path $Repo "gradlew.bat"))) { throw "La carpeta no parece ser el proyecto Android de Ordia." }
if (-not (Test-Path (Join-Path $Repo ".git"))) { throw "Ordia no contiene el repositorio Git esperado." }
if (-not (Test-Path $SourceRoot)) { throw "El paquete está incompleto: falta la carpeta files." }

New-Item -ItemType Directory -Force -Path $BackupRoot | Out-Null
Set-Location $Repo
$OriginalBranch = (& git branch --show-current).Trim()
$OriginalHead = (& git rev-parse HEAD).Trim()
if ([string]::IsNullOrWhiteSpace($OriginalHead)) { throw "Git no pudo determinar el SHA actual del repositorio." }
if ([string]::IsNullOrWhiteSpace($OriginalBranch)) { throw "El repositorio está en detached HEAD. Cambia a una rama antes de aplicar Ordia 3.0." }

$OriginUrl = ((& git remote get-url origin 2>$null) -join "").Trim()
$TrustedRemote = Test-TrustedOrigin $OriginUrl
if (-not $TrustedRemote) {
    Write-Warning "El remoto origin no corresponde al repositorio oficial de Ordia. Se compilará localmente, pero no se tocarán secretos ni se hará push."
}
& git branch "backup/ordia-before-3-$Timestamp" $OriginalHead *> $null
if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la rama local de respaldo." }

Write-Step "0/8 Validando la integridad del paquete..."
Validate-PackageIntegrity -PackageRoot $PackageRoot

Write-Step "1/8 Protegiendo cualquier trabajo local existente..."
$dirty = (& git status --porcelain) -join "`n"
if (-not [string]::IsNullOrWhiteSpace($dirty)) {
    & git stash push --include-untracked --message "Ordia 3.0 automatic backup $Timestamp"
    if ($LASTEXITCODE -ne 0) { throw "No se pudieron proteger los cambios locales con git stash." }
    $StashCreated = $true
}

# Prepare an isolated feature branch only after local work is safe.
if ($OriginalBranch -eq $TargetBranch) {
    $TargetBranch = "feature/ordia-3.0-$Timestamp"
}
& git show-ref --verify --quiet "refs/heads/$TargetBranch"
if ($LASTEXITCODE -eq 0) {
    $TargetBranch = "feature/ordia-3.0-$Timestamp"
}
& git checkout -b $TargetBranch $OriginalHead
if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la rama segura $TargetBranch desde $OriginalHead." }

# Keep a readable copy outside the repository as an additional safety layer.
Get-ChildItem -Path $SourceRoot -Recurse -File -Force | ForEach-Object {
    $relative = $_.FullName.Substring($SourceRoot.Length).TrimStart('\','/')
    $target = Join-Path $Repo $relative
    if (Test-Path $target) {
        $backupTarget = Join-Path $BackupRoot $relative
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $backupTarget) | Out-Null
        Copy-Item $target $backupTarget -Force
    }
}
$viewModel = Join-Path $Repo "app\src\main\java\com\ordia\app\ui\OrdiaViewModel.kt"
if (Test-Path $viewModel) {
    $backupVm = Join-Path $BackupRoot "app\src\main\java\com\ordia\app\ui\OrdiaViewModel.kt"
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $backupVm) | Out-Null
    Copy-Item $viewModel $backupVm -Force
}

try {
    Write-Step "2/8 Aplicando el lote Ordia 3.0..."
    Get-ChildItem -Path $SourceRoot -Recurse -File -Force | ForEach-Object {
        $relative = $_.FullName.Substring($SourceRoot.Length).TrimStart('\','/')
        $target = Join-Path $Repo $relative
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
        Copy-Item $_.FullName $target -Force
    }

    Write-Step "3/8 Normalizando el progreso del guardián..."
    Invoke-ViewModelPatch -RepoPath $Repo

    Write-Step "4/8 Configurando una firma estable para actualizaciones de prueba..."
    New-Item -ItemType Directory -Force -Path $SigningRoot | Out-Null
    $keyAlias = $null
    $storePassword = $null
    $keyPassword = $null

    $signingPairValid = (Test-Path $StableKeystore) -and (Test-Path $SigningCredentials)
    if ($signingPairValid) {
        try {
            $credentials = Import-Clixml $SigningCredentials
            $keyAlias = [string]$credentials.Alias
            $storePassword = [System.Net.NetworkCredential]::new("", $credentials.StorePassword).Password
            $keyPassword = [System.Net.NetworkCredential]::new("", $credentials.KeyPassword).Password
            # Never reuse Android generic debug key as an update identity.
            if ($keyAlias -eq "androiddebugkey" -or [string]::IsNullOrWhiteSpace($storePassword)) {
                $signingPairValid = $false
            }
        } catch {
            $signingPairValid = $false
        }
    }

    if (-not $signingPairValid) {
        $orphanRoot = Join-Path $BackupRoot "orphan-signing"
        New-Item -ItemType Directory -Force -Path $orphanRoot | Out-Null
        if (Test-Path $StableKeystore) { Move-Item $StableKeystore (Join-Path $orphanRoot "ordia-update.keystore") -Force }
        if (Test-Path $SigningCredentials) { Move-Item $SigningCredentials (Join-Path $orphanRoot "credentials.clixml") -Force }

        $adbForSigning = Get-Command adb -ErrorAction SilentlyContinue
        if ($adbForSigning) {
            $installedPackages = (& adb shell pm list packages com.ordia.app.debug 2>$null) -join "`n"
            if ($installedPackages -match "com\.ordia\.app\.debug") {
                Write-Warning "Se creará una clave exclusiva para Ordia. Si la APK instalada fue firmada con otra clave, Android requerirá exportar una copia, desinstalarla una sola vez e instalar la nueva versión."
            }
        }
        $keytool = Get-Command keytool -ErrorAction SilentlyContinue
        if (-not $keytool) { throw "No se encontró keytool para crear la firma estable de Ordia." }
        $randomBytes = New-Object byte[] 24
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
        $storePassword = (($randomBytes | ForEach-Object { $_.ToString("x2") }) -join "")
        $keyPassword = $storePassword
        $keyAlias = "ordia-update"
        & $keytool.Source -genkeypair -noprompt -keystore $StableKeystore -storetype PKCS12 `
            -alias $keyAlias -keyalg RSA -keysize 3072 -validity 3650 `
            -storepass $storePassword -keypass $keyPassword `
            -dname "CN=Ordia Update, OU=Ordia, O=Ordia, L=Santo Domingo, C=DO"
        if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la firma estable exclusiva de Ordia." }

        [pscustomobject]@{
            Alias = $keyAlias
            StorePassword = ConvertTo-SecureString $storePassword -AsPlainText -Force
            KeyPassword = ConvertTo-SecureString $keyPassword -AsPlainText -Force
        } | Export-Clixml -Path $SigningCredentials -Force
    }

    if (-not (Test-Path $StableKeystore)) { throw "No se pudo preparar la firma estable de Ordia." }
    $env:ORDIA_KEYSTORE_PATH = $StableKeystore
    $env:ORDIA_KEYSTORE_PASSWORD = $storePassword
    $env:ORDIA_KEY_ALIAS = $keyAlias
    $env:ORDIA_KEY_PASSWORD = $keyPassword

    $keytoolCheck = Get-Command keytool -ErrorAction SilentlyContinue
    if (-not $keytoolCheck) { throw "No se encontró keytool para verificar la firma estable de Ordia." }
    & $keytoolCheck.Source -list -keystore $StableKeystore -storepass $storePassword -alias $keyAlias *> $null
    if ($LASTEXITCODE -ne 0) { throw "La firma estable existe, pero sus credenciales no son válidas." }

    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if ($gh -and $TrustedRemote) {
        try {
            & gh auth status *> $null
            if ($LASTEXITCODE -eq 0) {
                $base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($StableKeystore))
                $secretResults = @(
                    (Set-GitHubSecret $gh.Source "ORDIA_UPDATE_KEYSTORE_BASE64" $base64),
                    (Set-GitHubSecret $gh.Source "ORDIA_UPDATE_KEYSTORE_PASSWORD" $storePassword),
                    (Set-GitHubSecret $gh.Source "ORDIA_UPDATE_KEY_ALIAS" $keyAlias),
                    (Set-GitHubSecret $gh.Source "ORDIA_UPDATE_KEY_PASSWORD" $keyPassword)
                )
                if ($secretResults -notcontains $false) {
                    $secretNames = (& gh secret list --repo wandersepulveda2013/ordia-android --json name --jq '.[].name') -join "`n"
                    $requiredSecrets = @(
                        "ORDIA_UPDATE_KEYSTORE_BASE64",
                        "ORDIA_UPDATE_KEYSTORE_PASSWORD",
                        "ORDIA_UPDATE_KEY_ALIAS",
                        "ORDIA_UPDATE_KEY_PASSWORD"
                    )
                    $RemoteSigningReady = ($requiredSecrets | Where-Object { $secretNames -notmatch "(?m)^$([regex]::Escape($_))$" }).Count -eq 0
                    if ($RemoteSigningReady) {
                        Write-Host "Firma estable sincronizada con GitHub Actions sin exponerla en el repositorio." -ForegroundColor Green
                    } else {
                        Write-Warning "GitHub no confirmó todos los secretos. El push automático será omitido."
                    }
                } else {
                    Write-Warning "No se completó la sincronización de secretos. El push automático será omitido."
                }
            } else {
                $RemoteSigningReady = $false
                Write-Warning "GitHub CLI no está autenticado. La compilación continuará sin push automático."
            }
        } catch {
            $RemoteSigningReady = $false
            Write-Warning "No se pudieron configurar los secretos de GitHub. La compilación local continuará sin push automático."
        }
    } else {
        $RemoteSigningReady = $false
        if (-not $TrustedRemote) {
            Write-Warning "No se configurarán secretos ni push porque el remoto origin no es el repositorio oficial esperado."
        } else {
            Write-Warning "GitHub CLI no está instalado. La APK local se firmará correctamente, pero el push automático será omitido para evitar una publicación con firma incompatible."
        }
    }

    Write-Step "5/8 Ejecutando pruebas y compilación limpia..."
    & .\gradlew.bat clean test lintDebug assembleDebug --stacktrace 2>&1 | Tee-Object -FilePath $Log
    if ($LASTEXITCODE -ne 0) { throw "Gradle detectó errores. Consulta $Log" }

    $apk = Join-Path $Repo "app\build\outputs\apk\debug\app-debug.apk"
    $metadataPath = Join-Path $Repo "app\build\outputs\apk\debug\output-metadata.json"
    if (-not (Test-Path $apk)) { throw "Gradle terminó sin producir $apk" }
    if (-not (Test-Path $metadataPath)) { throw "Gradle no produjo output-metadata.json para validar la versión." }
    $metadata = Get-Content $metadataPath -Raw | ConvertFrom-Json
    $builtVersionCode = [long]$metadata.elements[0].versionCode
    if ($builtVersionCode -le 0) { throw "El APK contiene un versionCode inválido." }
    $apksigner = Get-Command apksigner -ErrorAction SilentlyContinue
    if ($apksigner) {
        & $apksigner.Source verify --verbose $apk *> $null
        if ($LASTEXITCODE -ne 0) { throw "apksigner rechazó la APK generada." }
    } else {
        Write-Warning "apksigner no está en PATH; Gradle y Android verificarán la firma, pero se omite esa comprobación adicional."
    }
    Copy-Item $apk $DesktopApk -Force
    $apkInfo = Get-Item $apk
    $apkHash = (Get-FileHash $apk -Algorithm SHA256).Hash
    $BuildVerified = $true

    Write-Step "6/8 Creando el commit verificado..."
    $paths = @()
    Get-ChildItem -Path $SourceRoot -Recurse -File -Force | ForEach-Object {
        $paths += $_.FullName.Substring($SourceRoot.Length).TrimStart('\','/').Replace('\','/')
    }
    $paths += "app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt"
    foreach ($path in ($paths | Sort-Object -Unique)) { & git add -- $path }

    & git diff --cached --quiet
    if ($LASTEXITCODE -ne 0) {
        & git commit -m "Build Ordia 3.0 with contextual attention and virtual guardians"
        if ($LASTEXITCODE -ne 0) { throw "La compilación pasó, pero Git no pudo crear el commit." }
    }
    $commit = (& git rev-parse --short HEAD).Trim()
    $ChangesCommitted = $true

    Write-Step "7/8 Publicando la rama de código sin publicar una Release automática..."
    $pushStatus = "Omitido: el remoto no es el repositorio oficial; el commit queda local"
    if ($TrustedRemote) {
        if (-not $RemoteSigningReady) {
            Write-Warning "La firma remota no quedó configurada. La rama de código puede subirse y probarse, pero no debe fusionarse a main hasta configurar los secretos de firma."
        }
        try {
            & git push -u origin "HEAD:$TargetBranch"
            if ($LASTEXITCODE -eq 0) {
                $pushStatus = "Completado"
                $PushCompleted = $true
            if ($gh) {
                & gh pr view $TargetBranch --repo wandersepulveda2013/ordia-android *> $null
                if ($LASTEXITCODE -ne 0) {
                    & gh pr create --repo wandersepulveda2013/ordia-android --base main --head $TargetBranch `
                        --title "Ordia 3.0: atención contextual privada y guardianes" `
                        --body "Integra la base 2.0 auditada y la nueva atención contextual local, opcional y confirmada por el usuario."
                }
            }

            } else { $pushStatus = "Falló; el commit queda local" }
        } catch {
            $pushStatus = "Falló; el commit queda local"
        }
    } else {
        Write-Warning "No se hará push porque origin no coincide con el repositorio oficial de Ordia."
    }

    Write-Step "8/8 Restaurando tus cambios previos e instalando cuando sea posible..."
    Restore-OriginalWork

    $adbStatus = "No se detectó un dispositivo Android conectado"
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if ($adb) {
        $devices = (& adb devices) -join "`n"
        if ($devices -match "\tdevice") {
            $installedDump = (& adb shell dumpsys package com.ordia.app.debug 2>$null) -join "`n"
            $installedMatch = [regex]::Match($installedDump, "versionCode=(\d+)")
            $installedCode = if ($installedMatch.Success) { [long]$installedMatch.Groups[1].Value } else { -1L }
            if ($installedCode -gt $builtVersionCode) {
                $adbStatus = "Instalación omitida: el dispositivo tiene una versión más nueva ($installedCode)"
            } else {
                & adb install -r $apk
                $adbStatus = if ($LASTEXITCODE -eq 0) { "APK instalada en el dispositivo" } else { "ADB no pudo instalar la APK" }
            }
        }
    }

    @"
ORDIA 3.0 — RESULTADO
Fecha: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
Rama original: $OriginalBranch
Rama de Ordia 3.0: $TargetBranch
Commit: $commit
Push: $pushStatus
APK: $DesktopApk
Tamaño: $($apkInfo.Length) bytes
SHA-256: $apkHash
VersionCode: $builtVersionCode
Instalación: $adbStatus
Log: $Log
Copia de seguridad: $BackupRoot

Android no permite una instalación completamente silenciosa de APK para aplicaciones normales. Ordia comprueba y descarga nuevas versiones automáticamente, pero Android solicitará la confirmación final.
"@ | Set-Content -Path $Result -Encoding UTF8

    Write-Host ""
    Clear-SigningEnvironment
    Write-Host "ORDIA 3.0.2 COMPILADA CORRECTAMENTE" -ForegroundColor Green
    Write-Host "APK: $DesktopApk"
    Write-Host "Commit: $commit"
    Write-Host "Push: $pushStatus"
    Write-Host "Resultado: $Result"
    $explorerArgument = '/select,"{0}"' -f $DesktopApk
    Start-Process -FilePath "explorer.exe" -ArgumentList $explorerArgument
}
catch {
    Clear-SigningEnvironment
    $message = $_.Exception.Message
    Restore-OriginalWork -BuildFailed
    $recovery = if (-not $BuildVerified) {
        "Las pruebas o la compilación no terminaron correctamente; el repositorio fue restaurado al SHA original."
    } elseif ($ChangesCommitted) {
        "La APK fue verificada y el commit local se conservó. Falló una operación posterior; revisa el informe antes de publicar."
    } else {
        "La APK fue verificada, pero falló una operación posterior antes del commit. Los cambios permanecen en el árbol de trabajo y tu trabajo anterior sigue protegido en git stash."
    }
    @"
ORDIA 3.0 — PROCESO INCOMPLETO
Fecha: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
Error: $message
Estado: $recovery
Log: $Log
Copia de seguridad: $BackupRoot

Nunca se revierte un commit ya publicado ni se destruye una compilación válida por un fallo tardío de red, GitHub CLI o ADB.
"@ | Set-Content -Path $Result -Encoding UTF8
    Write-Host ""
    Write-Host "ORDIA 3.0 — PROCESO INCOMPLETO" -ForegroundColor Red
    Write-Host $message -ForegroundColor Red
    Write-Host $recovery -ForegroundColor Yellow
    Write-Host "Informe: $Result" -ForegroundColor Yellow
    exit 1
}
