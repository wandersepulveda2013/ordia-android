# ARCHITECTURE_DECISIONS — Ordía (bloc de notas)

> Solo decisiones técnicas significativas y duraderas.

## DEC-003 (2026-08-27) — Título de nota: dato de una línea (no solo vista)

El campo de título del editor es `singleLine=true` Y su `onValueChange` aplana
los saltos de línea (sustituye `\n` por espacio) antes de actualizar el estado y
el autosave. Motivo: la lista pinta los títulos con `maxLines=1`, así que un
`\n` persistido en el título quedaría invisible pero almacenado — incoherencia
de datos verificada por test (`singleLine` por sí solo no filtra un pegado). No
hay migración Room (no se altera el esquema); es saneamiento en la capa de
entrada de UI.

## DEC-002 (2026-08-27) — Autosave del editor con ciclo de draft (debounce 800 ms)

El editor ya no persiste "solo al volver atrás": el `NotepadViewModel` mantiene
un estado de draft (id efectivo + flag de nota nueva) entre `beginDraft`,
`autosave` (debounced 800 ms) y `commitDraft` (back/"Hecho"). Todo escribe a una
persistencia compartida bajo un único `draftId`, lo que evita que el back-save
posterior cree una segunda nota (mismo id), preserva la guardia de nota nueva
vacía (BUG-002) y no resucita una nota borrada a mitad de edición
(save-after-delete). El id del draft se reutiliza aunque la nota se creara por
autosave (en lugar de por guardado manual), así que no hay duplicados. Decisión
pragmática: el debounce y el borrado de la nota fantasma se gestionan en el
ViewModel (una capa), sin tocar Room ni la navegación.

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
