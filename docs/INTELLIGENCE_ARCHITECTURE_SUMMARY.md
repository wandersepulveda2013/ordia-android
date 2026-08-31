# Arquitectura de Inteligencia Local — Ordía 3

> Fecha: 2026-07-30
> Rama: `feature/ordia-3.0-context-rebuild`
> Commits: 7 commits de inteligencia sobre la rama existente
> APK: `app-previewAdvanced-debug.apk` (51.7 MB, `com.ordia.app.preview.advanced`)
> Compilación: ✅ SUCCESSFUL

## Resumen del trabajo

Se construyó el sistema completo de inteligencia local para Ordía 3,
reemplazando el antiguo pipeline de reglas (`ContextIntentEngine`) por
una arquitectura de dos capas que soporta un modelo de lenguaje real
(Gemma 2B vía MediaPipe) ejecutado en el dispositivo.

### Archivos creados (14 en `intelligence/` package)

| Archivo | Propósito | Líneas |
|---------|-----------|--------|
| `IntelligenceSchema.kt` | Esquema JSON estructurado con 8 campos | 157 |
| `IntelligenceRequest.kt` | Solicitud de análisis (texto + metadatos) | 31 |
| `IntelligenceResponse.kt` | Respuesta estructurada (schema + confianza) | 47 |
| `IntelligenceProvider.kt` | Interfaz común para proveedores | 39 |
| `IntelligenceSafetyGate.kt` | Filtro de privacidad ANTES del análisis | 100 |
| `IntelligenceMemory.kt` | Memoria de acciones confirmadas (solo labels) | 137 |
| `IntelligenceModelManager.kt` | Descarga + verificación SHA-256 + caché | 275 |
| `BasicRuleProvider.kt` | Parser de reglas/regex como fallback | 243 |
| `LocalModelProvider.kt` | Gemma 2B vía MediaPipe LLM Inference | 195 |
| `IntelligenceRouter.kt` | Enrutador entre proveedores | 121 |
| `OrdiaIntelligenceEngine.kt` | API pública singleton | 96 |
| `IntelligenceActionPlanner.kt` | Traduce esquema a plan ejecutable | 132 |
| `IntelligenceActionExecutor.kt` | Crea tareas reales en Room DB | 176 |
| `IntelligenceDiagnostics.kt` | Suite de 100 frases de prueba | 269 |

### Archivos modificados (6)

| Archivo | Cambio |
|---------|--------|
| `ContextEngine.kt` | Ahora usa `OrdiaIntelligenceEngine` en vez de `ContextIntentEngine` |
| `ContextIntentEngine.kt` | Marcado `@Deprecated`, mantenido como backend de BasicRuleProvider |
| `ContextIntent.kt` | Añadido `UNKNOWN` a `ContextIntentKind` |
| `ContextCaptureSource.kt` | Añadidos `OVERLAY` y `DIAGNOSTICS` |
| `GuardianOverlayService.kt` | Nuevo modo "Asistente" con input de texto + inteligencia |
| `build.gradle.kts` | Dependencia `com.google.mediapipe:tasks-text` |

### Commits (7)

```
9071e40 feat(intelligence): wire into ContextEngine, Guardian, CaptureSource
d929e5b feat(intelligence): add IntelligenceDiagnostics — suite de 100 frases
b76c37d feat(intelligence): add Router, Engine, ActionPlanner, ActionExecutor
945886c feat(intelligence): add LocalModelProvider con MediaPipe LLM Inference
a62a220 feat(intelligence): add BasicRuleProvider — envuelve engine legacy
b50eaa9 feat(intelligence): add SafetyGate, Memory, ModelManager
176a864 feat(intelligence): add core models — Schema, Request, Response, Provider
```

## Arquitectura

```
Texto capturado (IME, notificación, overlay, selección)
    │
    ▼
IntelligenceSafetyGate ─── ¿Bloqueado? → Response(privacyResult=BLOCKED)
    │
    ▼
IntelligenceRouter
    │
     ─ ¿Modelo local disponible? → LocalModelProvider (Gemma 2B)
    │                               Prompt con esquema JSON
    │                               MediaPipe LLM Inference
    │
    └─ ¿No disponible? → BasicRuleProvider (reglas/regex)
                          Envuelve ContextIntentEngine legacy
    │
    ▼
IntelligenceResponse(schema, confianza, proveedor)
    │
     ─ IntelligenceActionPlanner → Plan ejecutable
     ─ IntelligenceActionExecutor → Tarea en Room DB
    └─ IntelligenceMemory → Solo acciones confirmadas
```

## Esquema de salida (JSON)

```json
{
  "actor": "yo|alguien|alguienMas|nosotros",
  "polarity": "positivo|negativo",
  "certainty": "cierto|probable|dudoso|condicional",
  "temporalDirection": "pasado|presente|futuro|futuroCercano|condicionalFuturo",
  "actionSuggested": "task|shopping|appointment|...|none",
  "actionParameters": {"place": "...", "person": "...", "item": "..."},
  "followUpQuestion": "string|null",
  "privacyResult": "segura|bloqueada"
}
```

## Brechas documentadas (requieren ADB / dispositivo)

| # | Brecha | Impacto |
|---|--------|---------|
| 1 | **Modelo TFLite no descargado** | `LocalModelProvider.loadModel()` no puede cargar el modelo en MediaPipe sin el archivo. La infraestructura de descarga/verificación está completa. |
| 2 | **Suite de 100 frases no ejecutada** | `IntelligenceDiagnostics.runFullSuite()` requiere dispositivo Android para validar que las 50 frases "aceptar" y 50 "rechazar" produzcan los resultados esperados. |
| 3 | **6 casos especiales no verificados** | Los 6 casos (Mañana iremos, no iremos, Juan irá, Ayer fuimos, Tal vez, Cuando salga...) no pueden validarse sin el modelo cargado. |
| 4 | **IME en dispositivo** | La integración IME → OrdiaIntelligenceEngine funciona por diseño (ContextEngine.processText), pero no se probó con escritura real. |
| 5 | **Guardian overlay** | El modo "Asistente" se agregó al panel del guardián pero requiere validación visual y táctil en dispositivo. |
| 6 | **Actualización A→B** | El actualizador (OrdiaUpdateManager) ya existía y está completo. La prueba de actualización real requiere ADB y firma. |

## Próximos pasos recomendados

1. Conectar dispositivo con ADB
2. Instalar APK: `adb install app/build/outputs/apk/previewAdvanced/debug/app-previewAdvanced-debug.apk`
3. Verificar que la app inicia sin crash
4. Probar modo "Asistente" en el guardián flotante
5. Descargar el modelo Gemma 2B desde la UI de configuración
6. Ejecutar `IntelligenceDiagnostics.runFullSuite()` desde algún punto de entrada
7. Validar los 6 casos especiales manualmente
