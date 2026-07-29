# Estrategia de pruebas

## Puertas mínimas

1. `python3 tools/verify_project.py`
2. `./tools/run_domain_checks.sh` cuando exista `kotlinc`
3. `./gradlew testDebugUnitTest`
4. `./gradlew lintDebug`
5. `./gradlew assembleDebug`
6. Pruebas instrumentales en emulador API 26 y API 36
7. Prueba manual en al menos un teléfono físico

## Unitarias

Cubren fechas, búsqueda, recurrencia, hábitos, prioridad, texto natural, horas de silencio, planificación diaria y recomendaciones del guardián.

## Instrumentales

- apertura Compose de humo;
- creación de base Room en memoria y operaciones esenciales.

## Escenarios manuales críticos

- completar y posponer una notificación;
- recordatorio dentro de horas de silencio;
- denegar y luego conceder notificaciones;
- activar/desactivar guardián y reiniciar la app;
- arrastrar el guardián a ambos bordes;
- captura rápida como tarea y nota;
- compartir texto desde otra app;
- crear, editar, completar, repetir, archivar y restaurar una tarea;
- exportar y restaurar una copia completa;
- adjuntar un documento y reabrir la nota;
- rotación y tamaños de teléfono/tableta;
- modo oscuro y texto grande;
- planificación automática con tareas que no caben en el día.

## Criterio de salida

No publicar con pruebas fallidas, errores de lint de gravedad alta, migraciones sin probar, pérdida de datos, bloqueos, ANR, permisos sin explicación o funciones visibles sin implementación.
