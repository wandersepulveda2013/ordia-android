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
