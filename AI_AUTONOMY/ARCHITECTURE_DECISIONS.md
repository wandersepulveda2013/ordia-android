# ARCHITECTURE_DECISIONS — Decisiones significativas

## ORD-3.0-A01 (2026-08-26) — Guard contra persistencia de notas vacías
- **Decisión**: `NotepadViewModel.save` ignora crear notas nuevas cuando título y contenido están en
  blanco (para notas existentes se mantiene la semántica de guardado; no hay auto-borrado de vacías).
- **Motivo**: evitar notas fantasma en la lista sin introducir complejidad de borrado automático.
- **Alternativa descartada**: borrar notas existentes vacías al guardar — cambio de comportamiento
  potencialmente destructivo; se evalúa aparte si hace falta.

## Decisiones heredadas (era jules, vigentes)
- Room con **KAPT** (no KSP) — ver DECISIONS.md / ORD-036. No revertir sin documentar.
- Flavors `previewSafe`, `previewAdvanced`, `previewFull`; package names derivados.
- `selectedId` + `rememberSaveable` para alternar lista/editor (sin Navigation component) — evaluado
  como suficiente para MVP; migración a Navigation registrada en NEXT_TASKS.md (P1).
