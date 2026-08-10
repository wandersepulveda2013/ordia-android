# DECISIONS — Ordía

> Registro de decisiones de arquitectura/estrategia (append-only).
> Formato: `FECHA | DECISIÓN | MOTIVO | CONSECUENCIA`

| FECHA | DECISIÓN | MOTIVO | CONSECUENCIA |
|-------|----------|--------|--------------|
| 2026-08-10 | El trabajo autónomo vive SOLO en `jules/autonomous-ordia`; `main` jamás recibe auto-merge. | Proteger la base estable de cambios no revisados por humanos. | Todo PR autónomo apunta a `jules/autonomous-ordia`; el humano revisa y decide si avanza a `main`. |
| 2026-08-10 | El rebuild de Codex se consolida en commits coherentes (tema por tema) y NO en un único commit gigante. | Historial legible y revisable. | 9 commits + rama de publicación `feature/ordia-total-rebuild-2026-08-10`. |
| 2026-08-10 | Cron autónomo cada 2 horas (minuto 17) + `workflow_dispatch`. | Suficiente para progreso continuo sin saturar CI; permite arranque manual. | Ventana de oportunidad cada 2h; si una sesión dura más, el session lock impide solape. |
| 2026-08-10 | Session lock por rama: `jules/autonomous-ordia` se bloquea durante la sesión; sin lock, nueva sesión no inicia. | Evitar dos agentes editando lo mismo. | El runner termina sin cambios si detecta lock activo (retry posterior). |
| 2026-08-10 | Failsafe `ORDIA_AUTONOMY_ENABLED` y verificación del archivo de bypass: si se desactiva, ninguna sesión nueva arranca. | Parada de emergencia independiente del código. | Apagar autonomía = crear/marcar archivo de bypass + desactivar variable. |
| 2026-08-10 | No reintentar indefinidamente; ante N fallos consecutivos, el workflow abre un ISSUE y la rama `jules/autonomous-ordia` queda intacta (revert si fuera necesario). | Evitar ciclos de agentes que se pisan y corrompen el repo. | Fallo visible y accionable para el humano. |
| 2026-08-10 | El workflow Jules usa `vars.ORDIA_AUTONOMY_ENABLED` como failsafe con valor por defecto ACTIVO (si la variable no existe o no es `false`/`0`/`no`/`off`, se lanza). | Coherente con el workflow previo (que lanzaba siempre) y con la decisión de autonomía continua; la parada explícita es la variable `false` o el archivo `AI_AUTONOMY/AUTONOMY_BYPASS`. | Requiere que el humano configure la variable solo si quiere DESACTIVAR la autonomía. |
| 2026-08-10 | Session lock por PR abierta: si hay cualquier PR abierta hacia `jules/autonomous-ordia`, no se lanza una nueva sesión. | Con cron cada 2h, evita que dos sesiones de Jules se solapen sobre la misma rama. | Una PR abierta de una sesión anterior pausa los ciclos hasta que se cierre/mergee; el humano puede cerrarla. |
| 2026-08-10 | El workflow verifica contra la API de Jules que la rama `jules/autonomous-ordia` exista en el source ANTES de lanzar; si no, el ciclo se omite (no falla el job). | La rama nueva puede tardar en sincronizarse con el conector de Jules; lanzar contra una rama inexistente fallaría en la sesión. | Primeros ciclos pueden ser no-op hasta que Jules vea la rama. |
| 2026-08-10 | Cron cada 2 horas en el minuto 17 (`17 */2 * * *`). | Suficiente para progreso continuo sin saturar CI; evita el minuto 00 y reduce colisión con otros jobs; `workflow_dispatch` permite arranque manual. | Ventana de oportunidad cada 2h. |
