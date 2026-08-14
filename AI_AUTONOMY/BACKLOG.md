# Backlog de Evolución Ordía (Prioridades)

## P0 (Regresiones/Errores Graves)
- Replanificación puede causar concurrencia (evaluar en DayPlanner/Repository).
- Chequeo de actualizaciones podría fallar si el tag no coincide. (Pendiente).

## P1 (Problemas graves de UX)
- Interfaz puede sobrecargar si hay más de 10 tareas urgentes hoy.
- Recordatorios no tienen pruebas explícitas de persistencia.

## P2 (Mejoras funcionales importantes)
- What Now: Añadir botón de "reprogramar", "posponer" en UI. (El dominio se mejoró).
- Parser Lenguaje Natural: Añadir variaciones extremas.
- Parser de Captura Universal: Detectar subtareas / proyectos / notas.
- Rutinas: Adaptabilidad al fallar un día.

## P3 (Pulido/Refinamiento)
- Revisar "Command Palette".
- Widget de próximos pasos.
- Componentes visuales unificados.
