import re
from datetime import datetime, timezone

def update_files():
    # Update BACKLOG.md
    with open('AI_AUTONOMY/BACKLOG.md', 'r', encoding='utf-8') as f:
        backlog_content = f.read()

    new_backlog = re.sub(
        r'\| P3 \| UX \| Pulido visual de pantallas renovadas del workspace \| capturas tras sesión \| OPEN \|',
        r'| P3 | UX | Pulido visual de pantallas renovadas del workspace (evitar drafts vacíos y auto-focus en título) | capturas tras sesión | FIXED |\n| P3 | UX | Pulido visual de pantallas renovadas del workspace (otros) | capturas tras sesión | OPEN |',
        backlog_content
    )

    with open('AI_AUTONOMY/BACKLOG.md', 'w', encoding='utf-8') as f:
        f.write(new_backlog)

    now = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')

    # Update CURRENT_STATE.md
    with open('AI_AUTONOMY/CURRENT_STATE.md', 'r', encoding='utf-8') as f:
        current_state = f.read()

    new_state = re.sub(
        r'- \*\*Fecha/hora \(UTC\)\*\*: .*',
        f'- **Fecha/hora (UTC)**: {now} (sesión: UX improvements for NoteEditorScreen)',
        current_state
    )

    new_state = re.sub(
        r'## Último trabajo realizado\n\n.*?(?=\n##)',
        f'## Último trabajo realizado\n\nSesión UX Improvements:\n- Modificado `NotepadViewModel` para descartar automáticamente notas vacías.\n- Modificado `NoteEditorScreen` para auto-focar el campo de título al crear una nueva nota.\n- Verificado con tests unitarios y lint.\n',
        new_state,
        flags=re.DOTALL
    )

    with open('AI_AUTONOMY/CURRENT_STATE.md', 'w', encoding='utf-8') as f:
        f.write(new_state)

    # Append to RUN_LOG.md
    log_entry = f"""
## {now} — UX Improvements: NoteEditorScreen

Sesión de implementación de mejoras de UX para la pantalla de edición de notas.

### Commits (esta sesión)
- feat(ux): auto-focus title and discard empty note drafts

### Verificación
- `:app:compilePreviewSafeDebugKotlin` — SUCCESS
- `:app:testPreviewSafeDebugUnitTest` — BUILD SUCCESSFUL
- `:app:lintPreviewSafeDebug` — BUILD SUCCESSFUL

### Contratos preservados
- Compatibilidad con base de datos Room y lógica de negocio.

"""
    with open('AI_AUTONOMY/RUN_LOG.md', 'a', encoding='utf-8') as f:
        f.write(log_entry)

update_files()
