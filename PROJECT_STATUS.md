# Estado de Ordía

Fecha de corte: 1 de agosto de 2026
Rama de trabajo: `feature/ordia-assistant-evolution` (base `8ecfa07`)
Referencia completa de hallazgos y correcciones: [`AUDITORIA_ORDIA_2026.md`](AUDITORIA_ORDIA_2026.md)

## Implementado en código fuente

| Área | Estado | Alcance |
|---|---|---|
| Inicio y adaptación | Implementado | Onboarding, modos Simple/Organizado/Avanzado, teléfono/tableta |
| Tareas | Implementado | CRUD, subtareas, prioridades, estados, etiquetas, repetición, archivo |
| Planificación | Implementado | Semana, mes, duración y plan automático local |
| Proyectos | Implementado | CRUD, progreso, tareas y notas relacionadas |
| Notas | Implementado | Bloques, plantillas, adjuntos, autosave |
| Hábitos y rutinas | Implementado | Registros, rachas, objetivos y pasos |
| Enfoque | Implementado | Temporizador, vínculo con tareas e historial |
| Búsqueda | Implementado | Tareas, proyectos, notas y hábitos; tolera tildes |
| Guardián | Implementado | Superposición, arrastre, modos y acciones rápidas |
| Recordatorios | Implementado | WorkManager, silencio, completar y posponer |
| Widget | Implementado | Próximo paso, pendientes y captura rápida |
| Copia y restauración | Implementado | JSON local versionado (v4 con checksum SHA-256), restauración atómica con validación previa, journal preventivo verificable y verificación posterior |
| Estadísticas y archivo | Implementado | Resumen de progreso, restauración y borrado definitivo |
| Privacidad | Implementado | Sin INTERNET, backup automático desactivado, permisos contextuales |
| Contexto | Implementado | IME y accesibilidad con guardas de privacidad (campos sensibles, apps bloqueadas), motor local con estado Unsupported honesto |

## Verificado en este entorno

- `./gradlew clean test lint assembleDebug assembleRelease`: **OK** en las 6 variantes (3 flavors × debug/release).
- **1644 pruebas unitarias, 0 fallos** (274 × 6 variantes).
- Lint sin errores (solo warnings P3/P4 documentados en la auditoría; `previewAdvancedDebug` con 0 `UnusedResources`).
- 6 APKs generados; release R8 de ~16 MB con `DEBUGGABLE=false`; job `sign` del CI valida con `aapt2 dump badging`.
- APK de entrega para el celular: `deliverables/Ordia-3.0-debug-2026-08-01.apk` (variante `previewAdvanced`, debug, 37.2 MB, paquete `com.ordia.app.preview.advanced`, `3.0.0-preview-advanced`, minSdk 26, target 36; verificada con `aapt2 dump badging`).
- Wrapper de Gradle con `distributionSha256Sum` y validación en CI.
- Evolución de asistente de organización personal completada (10 bloques, commits `25d2c5a`→`8e77d3a`, validación final `56a0adc`): analizador NLU en español con captura segura a bandeja; preview de interpretación con chips; plan automático del día con motivos/conflictos, log de automatizaciones y deshacer (`DayPlanner`/`AutomationLogEntity`/`TaskSnapshotCodec`, Room v3 con `MIGRATION_2_3`); replanificación del día; tarjeta "Qué hago ahora" (`WhatNowEngine`); resincronización de recordatorios ante zona horaria/hora/fecha; rutinas adaptables (respetan ejecución diaria y son deshacibles); resumen del día y ritmo semanal (`SummaryEngine`); subtareas inteligentes (autocompletar padre, reapertura, límite de profundidad 3, deshacer); aprendizaje local opt-in del planificador (`LearningEngine`, preferencia desactivada por defecto, nunca en la nube). Regresión completa 1644 tests (274×6), 0 fallos; lint `previewAdvancedDebug` 0 errores / 0 `UnusedResources`; APK regenerado y verificado con aapt2. `56a0adc` corrige la única regresión de lint detectada en la validación final (string `root_context_inactive` sin uso desde BLOQUE 1). Limitación documentada: no hay emulador/ADB en este equipo; la adaptabilidad y el flujo se verificaron por tests, lint y APK instalable.
- Hallazgos críticos de la auditoría: **3/3 P0 corregidos, 8/8 P1 corregidos, 12/12 P2 corregidos, 10/10 P3 corregidos**; 2 bloqueos por CI documentados (ORD-015 migración Room, ORD-032 FTS) y 0 hallazgos abiertos.
- ORD-025 (subtareas huérfanas) cerrado: `TaskRepository.deletePermanently` usa `TaskDao.deleteSubtreeAndSelf`, que recoge el subárbol completo (`TaskTree.collectIds`, BFS tolerante a ciclos, JVM puro) y lo borra en una única transacción; 7 tests nuevos.
- ORD-029 (i18n en Kotlin) **cerrado**: localizados los 4 archivos citados por la auditoría (65 strings), todos los literales visibles de pantallas Compose, componentes y servicios (~300 strings en 6 archivos de recursos nuevos) y, en 1b6f8d0, los últimos literales residuales (NotesScreen, NoteEditorScreen, FocusScreen, HabitsScreen, PlannerScreen, SettingsScreen) con 14 claves muertas eliminadas. En 95bb5cd se cerró el resto: `label` de los 15 destinos de `Navigation.kt` → `@StringRes labelRes` (`nav_*`) resuelto con `stringResource`; vocabulario del guardián (`species`/`stage`/`mood`/`archetype`/`interaction` en GuardianScreen, TodayScreen, SettingsScreen, VirtualGuardian y GuardianPetView) → mapper `ui/GuardianLabels.kt` (36 recursos). **El dominio queda sin texto de producto** (`GuardianEngine`/`GuardianSpecies` pierden `label`/`description`; conservan `minimumXp`/`bond`/`event`/`defaultName`). Lint `previewAdvancedDebug`: 0 errores / 0 `UnusedResources` / 102 warnings; regresión 1278 tests, 0 fallos; APK regenerado y verificado. Residual documentado: mensajes dinámicos del guardián generados en dominio (`GuardianCoach`) — contenido del motor, no literales de UI.
- Rediseño visual limpio completado (8 bloques, commits `1e33a17`→`70a0353`): paleta violeta/lavanda/dorado con roles M3 completos light/dark, tipografía densificada (encabezados 30→18sp) y radios 10–24dp (`Theme.kt`/`Type.kt`); `ScreenHeader`/`SectionHeader` compactos; componentes base nuevos (`OrdiaListItem`, `StatusBadge`, `OrdiaLoading`, `OrdiaError`) y `StatCard`/`EmptyState` densificados; barra inferior a 64dp con etiqueta solo en el item seleccionado y rail compacto; MoreScreen con apartados agrupados por barras divisorias; radios alineados en Today, Planner, Statistics, TaskRow y formularios; Guardian/Onboarding con proporciones equilibradas. Sin FAB global: las acciones primarias ya viven en `ScreenHeader` y un FAB duplicado sería decorativo (regla P3C). Sin cambios de lógica, datos, navegación ni strings visibles. Regresión completa 1278 tests (213×6), 0 fallos; lint `previewAdvancedDebug` 0 errores / 0 `UnusedResources` / 102 warnings; APK regenerado y verificado. Limitación documentada: no hay emulador/ADB en este equipo, por lo que no se pueden capturar pantallas comparativas; la adaptabilidad se verificó por código y regresión.
- Flujo de respaldo/restauración verificado hoy: `BackupManagerTest` (19) + `BackupSecurityRulesTest` (10) en verde; regresión completa 213×6=1278 tests, 0 fallos; APK debug instalable regenerado en `deliverables/Ordia-3.0-debug-2026-07-31.apk` (paquete `com.ordia.app.preview.advanced`, `3.0.0-preview-advanced`) y verificado con `aapt2`.
- ORD-028 (contentDescription en Compose) cerrado: verificado que los ~22 `Icon(..., null)` son decorativos con texto adyacente (contrato Compose: null = decorativo, excluido de TalkBack); añadidas 3 descripciones de acción en `EditorDialogs.kt` para botones cuyo texto es un valor (fecha/prioridad/repetición).
- ORD-027 (touch targets) cerrado: 13 targets a 48dp en `ordia_suggestion_card.xml`, `ordia_external_confirmation.xml` y `ordia_keyboard_view.xml`; además `minHeight=48dp` en `dont_detect_text` y `widget_capture`.
- ORD-026 (i18n en layouts) cerrado: las 23 strings hardcodeadas de los 4 layouts XML pasan a recursos `@string` (15 existentes + 8 nuevos con el mismo texto); lint `HardcodedText` 23→0 y `UnusedResources` 26→9 en `previewAdvancedDebug`.
- ORD-030 (labels de servicios) cerrado: manifests fuente y mergeados verificados como **UTF-8 válido** (el "mojibake" era un falso positivo de lectura Latin-1); corregido el acento del label del notification listener en `previewFull` ("Ordia" → "Ordía").
- ORD-037 (reglas de backup muertas) cerrado: eliminados `res/xml/backup_rules.xml` y `data_extraction_rules.xml`, config inaplicable con `allowBackup=false`. Matiz en ORD-036: en API 31+ el mecanismo activo es `android:dataExtractionRules`, así que en 9107f3d se añadió un archivo nuevo con exclusión total referenciado desde el manifest (mantiene las copias desactivadas; no reabre ORD-037).
- ORD-036 (lint P4) cerrado: `Autofill`, `ButtonStyle`, `StaticFieldLeak`, `ObsoleteSdkInt` (3) y `DataExtractionRules` resueltos con cambio de código o supresión documentada (`app/lint.xml`, `tools:ignore`, `@SuppressLint`); iconos adaptativos movidos de `mipmap-anydpi-v26` a `mipmap-anydpi`. `KaptUsageInsteadOfKsp` documentado como bloqueo de ecosistema (KSP 2.1.0-1.0.29 embebe kotlinx-serialization 1.7.3 y sombrea el 1.8.1 de Room 2.8.4 → `AbstractMethodError`; subir KSP exige Kotlin ≥ 2.1.10). Lint `previewAdvancedDebug`: 0 errores / 102 warnings (antes 110); regresión 1278 tests, 0 fallos; APK regenerado y verificado.
- ORD-012 (batería overlay) corregido: el polling de 60 s del `GuardianOverlayService` se sustituyó por un one-shot que despierta solo en el próximo borde de quiet hours; `analyzeText` de "Preguntar" corre en `Dispatchers.Default`.
- ORD-005 (filtro de paquetes) corregido: `ContextPrivacyFilter` se aplica ahora en `ContextEngine.processEventAsync` para todas las fuentes, antes de la inferencia local.
- ORD-014 (TOFU del checksum del modelo) corregido: el SHA-256 verificado se fija localmente tras la primera descarga y las re-descargas no vuelven a confiar en el checksum remoto.
- ORD-018 (contexto externo inseguro) corregido: `isSecureContext` usa el `sourcePackage` real del evento/IME con `SECURE_PACKAGES` completado; la ruta IME también verifica paquete y contenido sensible.
- ORD-024 (rate-limit) corregido: mínimo 2 s entre eventos de la misma fuente y tope global de 500 eventos/día en el pipeline contextual.

## Instalación en dispositivo (sin ADB en este equipo)

No hay `adb` disponible en este equipo. Para instalar la APK en el celular:

```powershell
# 1. Con el celular en modo desarrollador y depuración USB activada
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices          # verificar autorización
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r deliverables/Ordia-3.0-debug-2026-08-01.apk
# 2. Alternativa sin cable: copiar la APK al celular y abrir el archivo con el instalador del sistema
```

## Pendiente fuera de este entorno

- Pruebas instrumentadas en emulador (no hay ADB/dispositivo en este equipo).
- Pruebas de permisos, notificaciones y overlay en dispositivos físicos.
- Revisión de Google Play y firma final.
- ORD-015: test de migración Room 1→2 (requiere versionar `app/schemas/`).

El estado del proyecto es **beta interna** con la línea base de pruebas verde y los bloqueos documentados.
