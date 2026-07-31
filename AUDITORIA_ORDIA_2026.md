# AUDITORÍA ORDÍA 2026

> Fecha: 2026-07-31
> Repositorio: `wandersepulveda2013/ordia-android`
> Rama auditada: `feature/ordia-3.0-context-rebuild` (HEAD `8ecfa07`)
> Rama de trabajo: `feature/ordia-audit-critical-fixes`
> Metodología: inspección línea por línea de los archivos críticos + 5 agentes de exploración + verificación manual de cada hallazgo P0/P1 + línea base real (clean/test/lint/assembleDebug).

## Línea base registrada (FASE 1)

| Elemento | Valor |
|---|---|
| Gradle | 8.13 (wrapper `gradle-8.13-bin.zip`) |
| AGP | 8.9.1 |
| Kotlin | 2.1.0 |
| Compose BOM | 2026.06.00 |
| Java | Temurin 17.0.20 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Product flavors | `previewSafe`, `previewFull`, `previewAdvanced` (dimensión `distribution`) |
| Build types | debug, release (R8 + shrink) |
| `gradlew clean` | ✅ OK (7s) |
| `gradlew test` (6 variantes) | ⚠️ Falló por memoria del daemon Kotlin → **ajustado** `gradle.properties` (Xmx2g/1536m, workers.max=2, parallel=false). Tras el ajuste: **570 pruebas unitarias, 0 fallos** (95 × 6 variantes) |
| `gradlew lint` | ✅ OK — 143 warnings, 1 información, 0 errores |
| `gradlew assembleDebug` | ✅ OK — 3 APKs de 41.7 MB (`apk/<flavor>/debug/app-<flavor>-debug.apk`) |
| Pruebas instrumentadas | ❌ No ejecutadas (no hay emulador/dispositivo en este equipo) |

## Tabla de hallazgos

| ID | Severidad | Área | Hallazgo | Evidencia | Riesgo | Solución | Estado |
|---|---|---|---|---|---|---|---|
| ORD-001 | P0 | Concurrencia | `ContextEngine.processEvent` ejecuta `runBlocking { intelligenceEngine.analyze() }`; `LocalModelProvider.analyze` corre la inferencia TFLite (Gemma 1B/2B, segundos) en el hilo del llamante (main) sin dispatcher | `context/ContextEngine.kt:54-56`; `intelligence/LocalModelProvider.kt:136-141` | ANR de accesibilidad/IME/UI con modelo local cargado | `processEvent` suspend + `withContext(Dispatchers.Default)` en `analyze`; llamantes asíncronos | CORREGIDO (d0ff692) — `processEventAsync`/`processTextAsync` suspend con `Dispatchers.Default`; servicios con scope propio; envoltorios síncronos solo no-UI |
| ORD-002 | P0 | Privacidad | El IME detecta campos sensibles (`isSensitiveInputType`) pero el resultado es un **no-op**: captura, analiza y encola texto de contraseñas/PIN/OTP igualmente | `ime/OrdiaKeyboardService.kt:150-153` vs `:84,196-216,281-304` | Captura de credenciales vía teclado | Bandera `sensitiveField` real: cancelar análisis, limpiar buffer, bloquear en `onKey`/`onText`/`scheduleAnalysis`/`commitAndAnalyze` | CORREGIDO (e90e941) — `KeyboardPrivacyGuard.shouldIgnore` en toda la captura; `KeyboardPrivacyGuardTest` 20/20 |
| ORD-003 | P0 | Motor local | `LocalModelProvider.analyze` envía el prompt como UTF-8 crudo a `Interpreter.run(inputBytes, outputBytes)` — **no es tokenización válida** para un LLM TFLite; fallará o producirá salida basura; se presenta como "modelo operativo" | `intelligence/LocalModelProvider.kt:136-141` | Falso funcionamiento del modelo + crash de inferencia; engaño al usuario | Desactivar de forma honesta si no hay implementación real (SignatureRunner + tokenizador) o devolver estado Unsupported | CORREGIDO (c5d0443) — `isInferenceSupported=false`, `analyze()` devuelve `unsupportedReason=LocalModelProvider.UNSUPPORTED_REASON`; `LocalModelUnsupportedTest` 7/7 |
| ORD-004 | P1 | Privacidad | El IME no lee `EditorInfo.packageName`; el bloqueo de paquetes bancarios de `ContextPrivacyFilter` es inalcanzable para la fuente KEYBOARD | `ime/OrdiaKeyboardService.kt:141-154` | Texto de apps bancarias analizado | Verificar `info.packageName` contra `ContextPrivacyFilter.BLOCKED_PACKAGES` | CORREGIDO (e90e941) — `KeyboardPrivacyGuard.shouldIgnore(inputType, packageName)` consulta `ContextPrivacyFilter.isPackageBlocked` |
| ORD-005 | P1 | Privacidad | `ContextPrivacyFilter.shouldBlock` (paquetes bancarios/salud) **nunca se evalúa** en el pipeline activo: `ContextEngine.processEvent` solo aplica `IntelligenceSafetyGate` por patrones de texto | `context/ContextEngine.kt:43-67`; `context/ContextPrivacyFilter.kt:67`; único invocador `ContextIntentEngine.kt:56` (deprecado) | Contenido de apps sensibles autorizadas procesado | Invocar el filtro de paquetes en `processEvent` para todas las fuentes | ABIERTO |
| ORD-006 | P1 | Privacidad | Títulos derivados del texto del usuario se escriben en logcat sin redacción | `context/ContextEngine.kt:74,95,106`; `accessibility/OrdiaAccessibilityService.kt:75,79`; `intelligence/IntelligenceActionExecutor.kt:77` | Exposición de fragmentos de contenido en logcat | Loguear solo kind+id o hash | CORREGIDO (c5d0443) — log `"Tarea creada (id=$id)"` sin título en claro |
| ORD-007 | P1 | CI/CD | `.github/workflows/android-ci.yml:48` usa `lintDebug` — tarea **inexistente** con flavors (solo existe `lint<Variant>`) → el job `verify` falla siempre y `sign`/`publish` nunca corren | `android-ci.yml:48` | CI roto, releases imposibles | `./gradlew clean test lint assembleDebug assembleRelease` | CORREGIDO (f072873) — CI usa `lint` global con flavors y valida |
| ORD-008 | P1 | CI/CD | El job `sign` firma y `publish` distribuye un APK **debug** (`app/build/outputs/apk/debug/app-debug.apk`, ruta inexistente) como release firmado | `android-ci.yml:54,142-151,231` | APK depurable distribuida como release: debuggable=true, sin R8; el actualizador no verifica `debuggable` | Subir/firmar el APK **release** real (`apk/previewAdvanced/release/…`) + verificar `debuggable=false` | CORREGIDO (f072873/1590932) — job `sign` valida con `aapt2 dump badging` y rechaza APK debuggable; R8 arreglado (16.7 MB, `DEBUGGABLE=false`) |
| ORD-009 | P1 | Éxitos falsos | `ReminderWorker` es un **stub vacío** que devuelve `Result.success()` sin notificar; `executeReminder` usa `enqueue` no único → "Recordatorio creado" sin notificación jamás | `intelligence/IntelligenceActionExecutor.kt:131-139,180-189` | Pérdida funcional silenciosa + wake-ups duplicados | Delegar en `ReminderScheduler`/`TaskReminderWorker` con `enqueueUniqueWork` | CORREGIDO (9d315c1) — `executeReminder` usa `ReminderScheduler.scheduleAt(id, dueAt)`; eliminada clase stub |
| ORD-010 | P1 | Accesibilidad | `OrdiaAccessibilityService.isEnabled` nace `false`, `onServiceConnected` no lo activa ni lee la preferencia; **ninguna UI** envía `ACTION_ENABLE` → la captura avanzada nunca procesa eventos | `accessibility/OrdiaAccessibilityService.kt:33,35-48,91-104` | Feature rota (captura prometida inexistente) | Leer pref en `onServiceConnected` + toggle en UI | CORREGIDO (afc4095) — toggle "Captura avanzada" en Ajustes (gated por `ADVANCED_CONTEXT_ENABLED`) con estado real del sistema y `ACTION_ACCESSIBILITY_SETTINGS` |
| ORD-011 | P1 | Éxitos falsos | `ModelDownloadWorker` es **stub** (`Result.success()` sin descargar); `ensureModelDownloaded` devuelve `true` tras encolar un worker no-op | `intelligence/IntelligenceModelManager.kt:191-224,419-434` | "Modelo descargado" sin modelo real; path muerto engañoso | Implementar descarga real en el worker o eliminar `ensureModelDownloaded` | CORREGIDO (9d315c1) — `ModelDownloadWorker` es `CoroutineWorker` real con `MAX_DOWNLOAD_ATTEMPTS=5` y `MAX_MODEL_BYTES` aplicado; `IntelligenceModelManagerTest` 9/9 |
| ORD-012 | P2 | Batería | `GuardianOverlayService` hace polling `while(true) delay(60s)` en `Dispatchers.Main` + `analyzeText` sin dispatcher en "Preguntar" | `overlay/GuardianOverlayService.kt:121-131,389-391` | 1440 wakes/día del main; overlay congelado durante análisis | Programar one-shot en el borde de quiet hours; `withContext(Default)` | ABIERTO |
| ORD-013 | P2 | Motor local | `MAX_MODEL_BYTES` (3 GB) definido pero **nunca aplicado** en el bucle de descarga; servidor puede llenar el disco | `intelligence/IntelligenceModelManager.kt:33,285-296` | Agotamiento de almacenamiento | `require(totalBytes <= MAX_MODEL_BYTES)` en el bucle | CORREGIDO (9d315c1) — límite aplicado en el bucle de descarga con error visible |
| ORD-014 | P2 | Motor local | Checksum SHA-256 en TOFU: se descarga del **mismo host** que el modelo; `expectedSha256 = null` | `intelligence/IntelligenceModelManager.kt:45-75,247` | Compromiso del CDN compromete modelo y checksum | Hardcodear SHA-256 oficial (o documentar el riesgo) | ABIERTO |
| ORD-015 | P2 | BD | Migración 1→2 declarada pero **sin schema `1.json`** exportado y **sin test de migración** (`MigrationTestHelper`) | `data/local/OrdiaDatabase.kt:45-168`; `app/schemas/…/2.json` (solo v2) | Crash en arranque para usuarios con BD v1; migración no verificable | Regenerar 1.json + test de migración | BLOQUEADO — `app/schemas/` está en `.gitignore`; sin versionar el schema v1 no hay `MigrationTestHelper` en CI. Decisión pendiente: versionar schemas o aceptar el riesgo documentado |
| ORD-016 | P2 | Concurrencia | SQLite/SharedPreferences síncronos en hilos main de servicios: `ContextAuditLog` (`db.insert`/`delete`), `ContextualSuggestionStore` (`prefs.commit()`), lectura de backup 10 MB en main | `context/ContextAuditLog.kt:25-36,41-53`; `context/ContextualSuggestionStore.kt:64,70`; `ui/screens/SettingsScreen.kt:111-119` | Jank/ANR; `SecureRandom` bloqueante en main | `withContext(Dispatchers.IO)`; `apply()` | CORREGIDO (33a1e5c, 9526920) — lectura/escritura de copias movidas a `Dispatchers.IO` en SettingsScreen; `ContextualSuggestionStore` usa `apply()` en lugar de `commit()`; los `insert`/`delete` del `ContextAuditLog` ya corren en `Dispatchers.Default` vía `ContextEngine.processEventAsync` (documentado en el código; las lecturas sin llamadores activos deberán envolverse en IO al conectarse a UI) |
| ORD-017 | P2 | IME | Teclas "↵" (-4) y "ABC" (-3) caen al `else` e insertan caracteres de control (U+FFFD/U+FFFC) en la app anfitriona; `KEYCODE_DONE` nunca se produce; sin `imeOptions` (Next/Done/Search/Send) ni multiline | `ime/OrdiaKeyboardService.kt:196-218`; `res/xml/ordia_keyboard_qwerty.xml:56-60` | Corrupción de la entrada del usuario | Manejar -3 (switchToNextInputMethod) y -4 (sendDefaultEditorAction/commit según imeOptions) | CORREGIDO (e90e941) — teclas -3/-4 resueltas en `onKey` |
| ORD-018 | P2 | Acciones externas | `ExternalConfirmationController.isSecureContext()` siempre `false` (`getForegroundPackage()` devuelve null); `SECURE_PACKAGES` nunca aplica; ruta IME salta `isSecureContext`/`isSensitiveContent` | `context/external/ExternalConfirmationController.kt:191-205,251-254,484-514` | Tarjeta de sugerencia sobre app sensible sin defensa | Usar `sourcePackage` del evento/IME; completar `SECURE_PACKAGES` | ABIERTO |
| ORD-019 | P2 | CI/CD | Sin validación del wrapper de Gradle (`wrapper-validation-action`) y sin `distributionSha256Sum` | `android-ci.yml` (ausente); `gradle/wrapper/gradle-wrapper.properties:3` | Supply-chain | Añadir validación + checksum | CORREGIDO (f072873/1590932) — `wrapper-validation-action` y `distributionSha256Sum=20f1b117…aed78` |
| ORD-020 | P2 | CI/CD | `build-apk.yml` usa acciones sin pinning SHA, `gradle` global (no `./gradlew`), sin timeout, artifact "Ordia-2.0" | `.github/workflows/build-apk.yml:18,21,27,32` | Acciones mutables, builds colgados | Pinear, timeout, `./gradlew`, renombrar | CORREGIDO (f072873) — `build-apk.yml` con acciones pineadas, timeout, `./gradlew` y nombre actual |
| ORD-021 | P2 | Recordatorios | `TaskReminderWorker` devuelve `Result.success()` sin permiso `POST_NOTIFICATIONS` (API 33+) → recordatorio descartado en silencio | `reminders/TaskReminderWorker.kt:44-46` | Recordatorio que nunca llega | `Result.failure()`/reprogramar acotado + estado visible | CORREGIDO (9d315c1) — `TaskReminderWorker` con `MAX_PERMISSION_RETRIES=5` y fallo visible sin permiso |
| ORD-022 | P2 | Respaldos | Restauración sin **respaldo preventivo automático**; ventana DataStore↔Room no cubre muerte del proceso | `backup/BackupManager.kt:142-148,193-201`; `ui/screens/SettingsScreen.kt:136-152` | Pérdida de datos actuales ante fallo en ventana crítica | Exportación preventiva automática + journal | CORREGIDO (07ed3e5) — `BackupManager.importBackup` valida todo el archivo ANTES de tocar datos, crea y **verifica** el journal `ordia_pre_restore_backup.json` (existente, no vacío y parseable) en almacenamiento privado, aplica preferencias con compensación si Room falla, reemplaza los datos en UNA transacción Room (`RoomBackupStore.replaceAll`) y relee lo persistido (counts + relaciones) antes de confirmar éxito; la UI muestra las fases y el journal por nombre. Cubierto por `BackupManagerTest` (19 casos JVM) |
| ORD-023 | P2 | Privacidad | Patrones "No detectar" guardados en claro (título derivado) en prefs `ordia_keyboard` sin límite | `ime/OrdiaKeyboardService.kt:410-421` | Persistencia en claro de frases | Guardar hash (`normalizeTokensHash`) + acotar | CORREGIDO (e90e941) — patrones "No detectar" persistidos como SHA-256 (`KeyboardPrivacyGuard.sha256Hex`) |
| ORD-024 | P2 | Privacidad | Sin rate-limit por fuente/global en el pipeline contextual; solo debounce 1.5s (IME) y `notificationTimeout=500` (accesibilidad) | `context/ContextEngine.kt:43-109`; `ime/OrdiaKeyboardService.kt:267-278` | CPU/batería; ventana de procesamiento de texto ampliada | Rate-limiter por fuente (~2s + tope diario) | ABIERTO |
| ORD-025 | P3 | BD | `TaskEntity.parentTaskId` sin self-FK: borrar tarea padre deja subtareas huérfanas | `data/local/Entities.kt:46` | Datos huérfanos | Self-FK con CASCADE/SET_NULL o validación en borrado | ABIERTO |
| ORD-026 | P3 | i18n | 23 strings hardcodeadas en layouts con recursos YA existentes sin usar (lint `HardcodedText` 23, `UnusedResources` 26) | `res/layout/ordia_keyboard_view.xml`, `ordia_suggestion_card.xml`, `ordia_widget.xml`, `ordia_external_confirmation.xml` | Sin i18n; strings divergentes | Referenciar `@string/...` existentes | ABIERTO |
| ORD-027 | P3 | Accesibilidad | Touch targets < 48dp en `ordia_suggestion_card.xml` (32dp), `ordia_external_confirmation.xml` (40dp), `ordia_keyboard_view.xml` (32-36dp) | layouts XML | Accesibilidad táctil | Alturas mínimas 48dp | ABIERTO |
| ORD-028 | P3 | Accesibilidad | ~22 iconos Compose clicables sin `contentDescription` (`Icon(..., null)`) | `ui/components/TaskComponents.kt:177-213`, `EditorDialogs.kt:108-149`, `AppComponents.kt:97-342`, `QuickCaptureActivity.kt:93` | TalkBack mudo | `contentDescription` descriptivo | ABIERTO |
| ORD-029 | P3 | i18n | Textos hardcodeados en Compose y servicios (100+); varias cadenas existen en `strings.xml` sin usarse (p. ej. `external_suggestion_no_permission`) | `GuardianOverlayService.kt:195-204,443`, `QuickCaptureActivity.kt:72-139`, `Navigation.kt:170`, `OrdiaUpdateManager.kt:332-333` | Sin traducción | Centralizar en `strings.xml` + `stringResource` | ABIERTO |
| ORD-030 | P3 | Manifiesto | Mojibake de codificación en labels de servicios (`OrdA-a A� atenciA3n contextual`, `OrdA-a A� teclado contextual`) | `app/src/previewFull/AndroidManifest.xml`, `app/src/previewAdvanced/AndroidManifest.xml` | Labels corruptos en ajustes del sistema | Corregir codificación UTF-8 | ABIERTO |
| ORD-031 | P3 | Respaldos | Export JSON sin checksum propio; adjuntos guardados como URIs `content://` (referencias) | `backup/BackupManager.kt:40-64,512-521` | Backup no detecta corrupción; adjuntos no recuperables | SHA-256 en export + documentación | CORREGIDO (checksum) — formato v4 con campo `checksum` SHA-256 del contenido sin el propio campo; import v4 rechaza copias dañadas o modificadas; v2-3 aceptadas como heredadas. Adjuntos `content://` documentados como limitación |
| ORD-032 | P3 | BD | Búsquedas con `LIKE '%q%'` (full scan) sin FTS4/5 en TaskDao/ProjectDao/NoteDao | `data/local/Daos.kt:40,82,127` | Degradación con BD grande | FTS4/5 | BLOQUEADO/DIFERIDO — FTS exige nuevas entidades FTS4 + migración de esquema (v3) que no es verificable en CI por la misma causa que ORD-015 (`app/schemas/` sin versionar). Riesgo asumido mientras la BD no sea grande |
| ORD-033 | P3 | Privacidad | `ContextAuditLog.titleHash` trunca SHA-256 a 64 bits sin sal → recuperable por diccionario | `context/ContextAuditLog.kt:172-179` | Reconstrucción de frases | SHA-256 completo + sal por instalación | CORREGIDO (d0ff692) — `titleHash` con SHA-256 completo y sal aleatoria persistente (16 bytes) |
| ORD-034 | P3 | Accesibilidad | Recursión sin límite de profundidad en `extractTextFromNode`; config XML vs programática duplicada; eventos registrados que el handler ignora | `accessibility/OrdiaAccessibilityService.kt:35-48,120-130` | StackOverflow improbable; batería | Límite 10 niveles; alinear config | CORREGIDO (afc4095) — `AccessibilityTextPolicy.canDescend` ≤10 niveles; config XML alineada; `AccessibilityTextPolicyTest` 15/15 |
| ORD-035 | P3 | Accesibilidad | `extractTextFromNode` no descarta nodos password/masked | `accessibility/OrdiaAccessibilityService.kt:120-130` | Fuga si el SO no redacta | Chequeo `isPassword()`/masked | CORREGIDO (afc4095) — `AccessibilityTextPolicy.isSensitiveNode` descarta nodos password/masked |
| ORD-036 | P4 | Varios | Lint menor: `Autofill` (1), `ButtonStyle` (2), `StaticFieldLeak` (1), `DataExtractionRules` (1), `ObsoleteSdkInt` (4), `KaptUsageInsteadOfKsp` (1) | `lint-results-previewAdvancedDebug.xml` | Calidad | Corregir en iteraciones P4 | ABIERTO |
| ORD-037 | P4 | Varios | `data_extraction_rules.xml`/`backup_rules.xml` sin referencia en el manifest (con `allowBackup=false`) | `app/src/main/AndroidManifest.xml:8-16` | Señal contradictoria | Referenciar o eliminar | ABIERTO |

## Estado de corrección (rama `feature/ordia-audit-critical-fixes`)

| Estado | P0 | P1 | P2 | P3 | P4 | Total |
|---|---|---|---|---|---|---|
| CORREGIDO | 3 | 7 | 8 | 4 | 0 | 22 |
| BLOQUEADO (CI: `app/schemas/` sin versionar) | 0 | 0 | 1 | 1 | 0 | 2 |
| ABIERTO | 0 | 1 | 4 | 6 | 2 | 13 |
| DESCARTADO | — | — | — | — | — | 2 (agentes) |

Cerrados: ORD-001, 002, 003, 004, 006, 007, 008, 009, 010, 011, 013, 016, 017, 019, 020, 021, 022, 023, 031, 033, 034, 035.
Bloqueados: ORD-015 (migración Room 1→2 sin test en CI), ORD-032 (FTS).
Abiertos prioritarios: ORD-005 (filtro de paquetes solo en IME, no en pipeline contextual), ORD-014 (TOFU del checksum, `expectedSha256` sigue nulo), ORD-012 (batería overlay), ORD-018 (contexto externo inseguro), ORD-024 (rate-limit).

### P0/P1 resumen

- **P0 corregidos (3/3):** ORD-001 concurrencia, ORD-002 privacidad IME, ORD-003 motor local honesto.
- **P1 corregidos (7/8):** ORD-004, 006, 007, 008, 009, 010, 011. **P1 abierto (1):** ORD-005 — `ContextPrivacyFilter.shouldBlock` solo se evalúa en la fuente IME; accessibility y notificaciones aún no consultan la lista de paquetes bloqueados.

## Hallazgos de agentes descartados tras verificación manual

| Hallazgo del agente | Verificación | Decisión |
|---|---|---|
| "previewFull no declara los servicios" (A1 del agente 5, reportado P1) | `previewFull/AndroidManifest.xml` **SÍ declara** `GuardianOverlayService`, `OrdiaNotificationListenerService`, `OrdiaKeyboardService`, `FileProvider`, `UpdateInstallActivity`, `UpdateDownloadReceiver` | **FALSO** — descartado. Solo faltan accessibility y external confirmation (exclusivas de `previewAdvanced`) |
| "El título persistido es fragmento literal del texto" (H3 del agente 1, reportado P1) | El pipeline activo (`ContextEngine.intelligenceResponseToIntent`) construye títulos sintéticos ("Yo: Pago"). El texto original solo aparece en el título de `IntelligenceActionPlanner` (ruta executor) y se loguea en `IntelligenceActionExecutor.kt:77` | **PARCIALMENTE SOBREESTIMADO** — el riesgo real de persistencia en claro es menor; el logging (ORD-006) sí es real |

## Fortalezas verificadas (no son hallazgos)

- `fallbackToDestructiveMigration()` **no** se usa en producción.
- Sin `allowMainThreadQueries`, sin `GlobalScope`, sin `Thread.sleep`, sin `AlarmManager`, sin `WakeLock` en todo el código.
- DAOs y repositorios 100% `suspend`/`Flow`.
- `BackupManager` con validación defensiva excelente (envelope anti-duplicados, límites, grafo de relaciones, transaccionalidad con rollback y compensación de preferencias).
- `OrdiaUpdateManager` endurecido: allow-lists de hosts, SHA-256 doble, comparación de firmas, límites de tamaño, FileProvider restringido a `verified_updates/`.
- `ReminderScheduler` usa `enqueueUniqueWork(..., REPLACE)` sin duplicados; `TaskReminderWorker` sin reintentos infinitos; `UpdateValidationWorker` con reintentos acotados a 3 y constraints de red/batería.
- Widget sin polling (`updatePeriodMillis="0"`).
- PendingIntents todos `FLAG_IMMUTABLE`; componentes expuestos mínimos y protegidos con `BIND_*`.
- `previewSafe` recorta permisos y componentes correctamente.
- 570 pruebas unitarias pasan en las 6 variantes; lint sin errores.
- Hash con sal en `ContextualSuggestionStore`; enmascarado de tarjetas/emails en `IntelligenceSafetyGate`.

## Nivel real del proyecto

**Alpha avanzada / pre-beta interna (con P0 y la mayoría de P1 corregidos).** El código compila, la línea base de pruebas es verde (139 tests en `previewAdvanced`), la arquitectura general es sólida y el backup/update están endurecidos. Los 3 P0 (concurrencia, privacidad del IME, motor local honesto) están cerrados y 7 de 8 P1 están corregidos; la rama `feature/ordia-audit-critical-fixes` puede considerarse beta interna con el P1 abierto (ORD-005) y los bloqueos documentados (ORD-015/032) pendientes de decisión.
