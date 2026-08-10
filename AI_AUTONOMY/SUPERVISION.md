# SUPERVISION — Ordía

> Guía para humanos que supervisan el sistema autónomo.
> Cómo detener, observar y validar el trabajo de Jules.

## 1. Cómo se ve el estado

- Rama de trabajo autónomo: `jules/autonomous-ordia`.
- Rama de publicación del rebuild: `feature/ordia-total-rebuild-2026-08-10`.
- Memoria persistente: `AI_AUTONOMY/` (MISSION, CURRENT_STATE, BACKLOG, DECISIONS, RUN_LOG, AGENTS, SUPERVISION).
- Automatización: workflow `.github/workflows/ordia-autonomous-jules.yml` (cron cada 2h + dispatch).
- CI existente (main): `.github/workflows/android-ci.yml` y `.github/workflows/build-apk.yml`.

### Comportamiento del workflow Jules

1. **Failsafe por variable**: si `vars.ORDIA_AUTONOMY_ENABLED` es `false`/`0`/`no`/`off`,
   la sesión NO se lanza. Si no existe (o es cualquier otro valor), se lanza (autonomía por defecto).
2. **Failsafe por archivo**: si existe `AI_AUTONOMY/AUTONOMY_BYPASS` en `jules/autonomous-ordia`,
   la sesión NO se lanza.
3. **Session lock por PR**: si existe una PR abierta hacia `jules/autonomous-ordia`,
   la sesión NO se lanza (evita solape entre ciclos de 2h).
4. **Rama de trabajo**: la sesión de Jules trabaja sobre `jules/autonomous-ordia`
   (verificado contra la API de Jules; si la rama aún no está sincronizada, el ciclo se omite).
5. **PRs**: `automationMode: AUTO_CREATE_PR` → Jules crea la PR hacia `jules/autonomous-ordia`, NUNCA a `main`.

## 2. Cómo detener la autonomía (parada de emergencia)

1. **Desactivar la variable**: en GitHub Settings → Secrets and variables → Actions →
   Variables → `ORDIA_AUTONOMY_ENABLED` = `false`. El workflow la comprueba y no lanza.
2. **Bypass por archivo**: crear `AI_AUTONOMY/AUTONOMY_BYPASS` en `jules/autonomous-ordia`
   (cualquier contenido). El workflow lo detecta y NO inicia sesiones nuevas.
3. **Cancelar runs**: en la pestaña Actions, Cancelar el run activo.

Tras la parada, el branch autónomo queda como estaba; nada se fuerza ni se revierte
automáticamente salvo lo que la propia sesión haya commiteado.

## 3. Cómo revisar una sesión

1. `git log --oneline -10` en `jules/autonomous-ordia`.
2. `git show <sha>` para revisar el diff de cada commit.
3. Leer `AI_AUTONOMY/RUN_LOG.md` (append-only) para ver qué afirmó la sesión.
4. Ejecutar `./gradlew test` y `./gradlew lintPreviewSafeDebug` localmente si quieres
   verificar antes de confiar.

## 4. Señales de alarma

- Commits que tocan `main` (no debe pasar nunca).
- Cambios que simulan capacidades (IA falsa, éxito inventado, progreso falso).
- Tests eliminados o comentados.
- Secretos en el repo.
- `git push --force` en historial (git lo registrará; sospecha).

## 5. Decisión de avance a main

- Solo un humano decide mover `jules/autonomous-ordia` → `main`.
- Sugerencia: revisar el RUN_LOG, correr la suite completa y hacer merge por PR normal.
