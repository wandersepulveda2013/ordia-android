# Auditoría de Ordia — base para 2.0

## Resumen

Ordia ya dispone de una base funcional considerable: persistencia Room, preferencias DataStore, tareas, subtareas, recurrencia, proyectos, notas, hábitos, rutinas, enfoque, búsqueda, estadísticas, archivo, respaldo, widget y recordatorios.

La principal debilidad no era la ausencia de módulos, sino la percepción de producto básico causada por una jerarquía visual limitada, tarjetas uniformes, métricas comprimidas, navegación visible en contextos donde distraía y poca orientación sobre la siguiente acción.

## Hallazgos prioritarios

1. **Inicio funcional pero plano:** ofrecía mucha información, aunque sin un centro visual dominante ni acciones rápidas claras.
2. **Métricas comprimidas:** tres tarjetas en una sola fila reducían legibilidad en teléfonos pequeños.
3. **Tareas con poca información visual:** fecha, proyecto, prioridad y subtareas competían dentro de una sola línea.
4. **Filtros limitados:** faltaba una vista explícita de tareas atrasadas y opciones de orden.
5. **Estadísticas poco accionables:** mostraban datos, pero no explicaban patrones ni combinaban tareas, hábitos y enfoque.
6. **Navegación persistente en detalles:** la barra inferior permanecía visible en editores, restando espacio y claridad.
7. **Menú «Más» sin agrupación:** todas las herramientas aparecían en una lista continua.
8. **Tema correcto pero poco distintivo:** el sistema cálido original era coherente, aunque necesitaba mayor contraste, profundidad y estados de superficie.
9. **API deprecada:** `Divider` debía sustituirse por `HorizontalDivider`.
10. **Documentación inconsistente:** el README afirmaba que existía una automatización de APK que no estaba presente en el repositorio leído.

## Cambios del lote

- sistema visual completo;
- componentes compartidos;
- inicio premium;
- tareas avanzadas;
- estadísticas ampliadas;
- navegación adaptativa refinada;
- menú «Más» por categorías;
- versión 1.1.0;
- documentación corregida.

## Riesgos que deben verificarse en Android Studio

- compilación con el BOM Compose 2026.06.00;
- contraste real en modo oscuro en dispositivos OLED;
- comportamiento de tarjetas en escalas de fuente superiores a 1.3;
- anchura de metadatos de tareas con nombres de proyecto muy largos;
- pruebas instrumentales de navegación y restauración de estado;
- validación de recordatorios después de reiniciar el dispositivo.

## Próximos lotes recomendados

1. rediseñar editores de tarea y nota;
2. calendario semanal con interacción avanzada;
3. centro de notificaciones y recordatorios;
4. onboarding visual con datos de ejemplo opcionales;
5. importación y exportación guiadas;
6. pruebas Compose de los flujos críticos;
7. pipeline CI con publicación automática del APK como artefacto.

## Ampliación 2.0 — Guardianes y actualización continua

- La esfera textual del servicio flotante se reemplaza por un renderizador animado con seis especies.
- El guardián adquiere identidad, vínculo, etapa, estado y energía.
- La evolución se conecta con tareas, hábitos, notas y enfoque mediante reglas verificables.
- Se añade un refugio navegable y controles de interacción.
- Se incorpora comprobación periódica de GitHub Releases y descarga gestionada por Android.
- Se añade firma estable de desarrollo para conservar la compatibilidad de actualizaciones.
- Se añade una prueba unitaria del motor de guardianes.

### Riesgo controlado

La instalación totalmente silenciosa no se implementa porque Android la bloquea para aplicaciones ordinarias. Ordia automatiza comprobación y descarga, pero conserva la confirmación de seguridad del sistema.
