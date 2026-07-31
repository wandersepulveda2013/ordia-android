# Estado de Ordia

Fecha de corte: 31 de julio de 2026
Rama de trabajo: `feature/ordia-audit-critical-fixes` (base `8ecfa07`)
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
- **948 pruebas unitarias, 0 fallos** (158 × 6 variantes). Las 19 nuevas pruebas de `BackupManagerTest` cubren el flujo de restauración: round-trip export/import, validación previa diferenciada (versión, colecciones, checksum, relaciones, ciclos, duplicados, ítems incompletos), journal preventivo verificable, rollback de preferencias y verificación posterior.
- Lint sin errores (solo warnings P3/P4 documentados en la auditoría).
- 6 APKs generados; release R8 de ~16 MB con `DEBUGGABLE=false`; job `sign` del CI valida con `aapt2 dump badging`.
- APK de entrega para el celular: `deliverables/Ordia-3.0-debug-2026-07-31.apk` (variante `previewAdvanced`, debug, 36.2 MB).
- Wrapper de Gradle con `distributionSha256Sum` y validación en CI.
- Hallazgos críticos de la auditoría: **3/3 P0 corregidos, 8/8 P1 corregidos**; 2 bloqueos por CI documentados (ORD-015 migración Room, ORD-032 FTS) y 9 abiertos (P2/P3/P4) sin impacto en la línea base.
- ORD-012 (batería overlay) corregido: el polling de 60 s del `GuardianOverlayService` se sustituyó por un one-shot que despierta solo en el próximo borde de quiet hours; `analyzeText` de "Preguntar" corre en `Dispatchers.Default`.
- ORD-005 (filtro de paquetes) corregido: `ContextPrivacyFilter` se aplica ahora en `ContextEngine.processEventAsync` para todas las fuentes, antes de la inferencia local.
- ORD-014 (TOFU del checksum del modelo) corregido: el SHA-256 verificado se fija localmente tras la primera descarga y las re-descargas no vuelven a confiar en el checksum remoto.
- ORD-018 (contexto externo inseguro) corregido: `isSecureContext` usa el `sourcePackage` real del evento/IME con `SECURE_PACKAGES` completado; la ruta IME también verifica paquete y contenido sensible.

## Instalación en dispositivo (sin ADB en este equipo)

No hay `adb` disponible en este equipo. Para instalar la APK en el celular:

```powershell
# 1. Con el celular en modo desarrollador y depuración USB activada
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices          # verificar autorización
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r deliverables/Ordia-3.0-debug-2026-07-31.apk
# 2. Alternativa sin cable: copiar la APK al celular y abrir el archivo con el instalador del sistema
```

## Pendiente fuera de este entorno

- Pruebas instrumentadas en emulador (no hay ADB/dispositivo en este equipo).
- Pruebas de permisos, notificaciones y overlay en dispositivos físicos.
- Revisión de Google Play y firma final.
- ORD-015: test de migración Room 1→2 (requiere versionar `app/schemas/`).

El estado del proyecto es **beta interna** con la línea base de pruebas verde y los bloqueos documentados.
