# ARCHITECTURE_DECISIONS - Ordia (notepad minimalista)

- 2026-09-02 -- Ruta unica de salida del editor: `NoteEditorScreen` centraliza el guardar+navegar en `exitSaving()`, usada por toolbar y por `BackHandler`; una sola ruta de salida; reduce divergencia y futuros olvidos.No cambia modelo ni persistencia.
- 2026-09-02 -- Confirmacion de borrado en UI: decision de UX/seguridad; el borrado real sigue siendo sincrono en `NoteDao.delete`; la capa UI anade un paso de confirmacion (`AlertDialog`) sin cambiar modelo/persistencia.
- Sin cambios de arquitectura de datos en esta sesion (Room/KAPT, sin migraciones nuevas.call
