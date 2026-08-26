# COMPLETED — Mejoras completadas (solo mejoras reales, no microcambios)

## 2026-08-26 — Baseline + seguridad de datos del editor (branch openhands/autonomous-notes)
1. **Baseline verde**: entorno Android SDK 36 + JDK 21 configurado; 15 tests (DAO+Repository) PASS.
2. **P0 — Back del sistema en editor**: antes, el botón/gesto back del sistema cerraba la app y
   perdía la nota en edición (no había Navigation; el estado quedaba en `rememberSaveable` del
   composable). Ahora `BackHandler` guarda y vuelve a la lista, como la flecha de la barra.
3. **Guard contra notas fantasma**: `NotepadViewModel.save` ya no persiste notas nuevas con
   título y contenido en blanco (evita notas vacías al abrir el editor y salir).
4. **Tests**: `NotepadViewModelTest` (5 tests) — guard de nota en blanco, inserción, actualización
   con bump de `updatedAt`, y no-recreación tras eliminación. 20/20 PASS.
