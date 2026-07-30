# Ordia 3.0.2 — instalador corregido

Incluye la base auditada de Ordia 3.0 y corrige la ejecución en Windows:

- `INICIAR_ORDIA_3.bat` permite iniciar con doble clic sin depender de la asociación de archivos `.ps1`.
- `DIAGNOSTICO_ORDIA_3.bat` identifica herramientas o rutas faltantes.
- Los cambios locales se protegen antes de crear o cambiar de rama.
- El desarrollo se realiza en una rama aislada.
- El `push` apunta a la rama de Ordia 3.0 y nunca a la rama original.
- Al terminar, vuelve a la rama original y recupera los cambios locales.

La atención contextual permanece local, opcional y confirmada por el usuario. No se usa `AccessibilityService` ni se almacenan conversaciones completas.

- `PROMPT_OPEN_CODE_SUBIR_ORDIA_3.txt`: instrucciones completas para verificar, subir la rama y abrir el PR con OpenCode.
