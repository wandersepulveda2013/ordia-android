# ARCHITECTURE_DECISIONS — Ordía (bloc de notas)

> Solo decisiones técnicas significativas y duraderas.

## DEC-001 (2026-08-26) — Restaurar notas borradas reinsertando con el mismo id

El undo de borrado reutiliza `NoteDao.insert(REPLACE)` con la entidad original:
`@PrimaryKey(autoGenerate = true)` solo genera id cuando `id == 0`, así que la
reinserción conserva el id original, el orden y cualquier referencia externa.
Alternativas descartadas: papelera lógica (columna `deletedAt`) — más potente
pero exige migración y cambios en todas las consultas; desproporcionado para el
tamaño actual del producto. Si en el futuro se necesita papelera con caducidad,
será una decisión nueva con migración Room v2.

## Decisiones heredadas del rebuild (commit `ceb1ff3`, no de esta automatización)

- Room + KAPT (ver ORD-036 en el historial: KSP sombrea kotlinx-serialization y
  rompe el processor de Room). No revertir a KSP sin documentar.
- Flavors `previewSafe` / `previewFull` / `previewAdvanced` con `applicationId`
  propio; el bloc de notas solo usa código común en `src/main`.
- Persistencia única: Room `ordia.db` v1, tabla `notes`
  (id, title, content, createdAt, updatedAt, pinned), `exportSchema = true`.
