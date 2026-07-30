# Implementación de Ordia 2.0

## Incluido

- renovación visual de componentes, inicio, tareas, estadísticas y navegación;
- seis guardianes, cinco etapas, personalidad, energía, vínculo y refugio;
- overlay opcional con límites de pantalla, accesibilidad y horas silenciosas;
- experiencia derivada de tareas, hábitos, notas y enfoque;
- restauración versión 3 con validación previa y preferencias;
- actualización periódica endurecida y exclusiva del canal sideload;
- CI de compilación y publicación con permisos separados;
- aplicador con respaldo, firma estable, rollback temprano y preservación de builds válidos.

## Progresión

`OrdiaRoot` deriva la actividad desde el estado real y llama a `syncGuardianExperience`. La sincronización conserva el máximo válido en lugar de sumar eventos de interfaz. El vínculo se almacena por separado y aporta una bonificación limitada.

## Restauración

Antes de modificar datos se validan:

- formato y versión;
- raíz JSON única y UTF-8 estricto;
- presencia de todas las colecciones;
- límites y campos obligatorios;
- IDs, relaciones, ciclos, fechas, estados y URIs;
- preferencias y estado del guardián.

Después de una transacción Room exitosa se cancelan y reconstruyen los recordatorios futuros.

## Validación del aplicador

```text
gradlew clean test lintDebug assembleDebug --stacktrace
```

Si pruebas o compilación fallan, se restaura el SHA original. Si el build ya fue verificado, un fallo tardío no destruye el código correcto ni revierte un commit publicado.

## Límite del paquete

Este lote contiene archivos modificados, no todo el repositorio ni Android SDK. La compilación Android integral se realiza en el equipo que conserva el proyecto completo.
