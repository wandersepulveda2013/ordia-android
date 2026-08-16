# Ordia 3 Context Rebuild — Auditoría completa

> Fecha: 2026-07-30
> Rama: feature/ordia-3.0-context-rebuild
> Revisados: 24 archivos principales

## 1. Navegación (Navigation.kt)

### Problemas detectados

- **14 destinos directos** + 3 con parámetros. El usuario ve demasiadas opciones.
- La navegación compacta tiene solo 4 pestañas pero "Más" contiene 11 destinos secundarios — el cajón de "Más" es un menú plano sin jerarquía.
- En modo simple/organizado/avanzado cambian los items del rail lateral pero no hay diferencias reales de funcionalidad.
- No hay navegación contextual: desde el guardián flotante no se puede navegar.
- `navigateSingle()` en lugar de limpiar correctamente el backstack — posible acumulación de rutas.
- Sin deep linking real para el teclado ni para el asistente de voz.

### Recomendación

Reducir a 5 destinos principales: Hoy, Plan, Capturar, Guardián, Más. Mover funcionalidades secundarias al menú Más con secciones.

## 2. TodayScreen.kt (453 líneas)

### Problemas detectados

- **Demasiada información simultánea**: tarjeta de ritmo, 3 cards de acción, 4 stat cards, captura inteligente, guardián, atrasadas, plan de hoy, hábitos, proyectos, enfoque — todo en una sola columna.
- Encabezado alto con saludo + subtítulo extenso.
- Texto explicativo excesivo en cada tarjeta.
- Dos LazyRow que compiten por atención (ActionCards + StatCards).
- El bloque "Captura inteligente" es un OutlinedTextField dentro de un Card — ocupa espacio que podría ser inline.
- El Card del guardián repite información ya visible en otras pantallas.
- Las estadísticas (pendientes, atrasadas, enfoque, completado) se muestran siempre aunque el usuario esté en modo Simple.
- El scroll es excesivo para una pantalla que debería ser un vistazo rápido.

### Recomendación

Mostrar solo: saludo breve + guardián + próxima acción + captura rápida + máximo 3 elementos prioritarios. Mover detalles a pantallas secundarias.

## 3. SettingsScreen.kt (536 líneas)

### Problemas detectados

- **Todo en una pantalla**: apariencia, guardián, actualizaciones, planificación, datos — no hay separación por categorías.
- 33 secciones/items en un solo LazyColumn.
- Mezcla de temas de configuración (privacidad con apariencia).
- Texto explicativo largo en cada tarjeta.
- El banner de preview ocupa espacio innecesario después de la primera vez.
- Las secciones de guardián y actualizaciones son extensas.
- No hay búsqueda.

### Recomendación

Separar en categorías con navegación: Apariencia, Atención contextual, Guardián, Voz, Privacidad, Notificaciones, Copias, Avanzado.

## 4. VirtualGuardian.kt (245 líneas)

### Problemas detectados

- **Círculos con ojos y boca**. No hay cuerpo, extremidades, ni movimiento articulado.
- LUMI: círculos concéntricos (sol).
- MOSS: círculo + hoja (planta).
- ORBIT: círculo + óvalo + punto (sistema solar).
- EMBER: forma de gota (llama).
- TIDE: forma de gota (agua).
- NOVA: estrella de 10 puntas (explosión).
- Las animaciones son solo traslación vertical y parpadeo.
- No hay "andar", "saltar", "jugar", "acariciar", "reaccionar".
- Los estados de ánimo solo cambian la forma de la boca (arco/línea).
- La evolución solo añade un círculo extra o un arco — no cambia la forma real.
- `drawAmbient` dibuja puntos parpadeantes alrededor — no es interacción real.

### Recomendación

Arquitectura de máquina de estados con 33+ estados, animación esquelética, sprites o Rive. Tres criaturas distintas con siluetas reconocibles, extremidades, ojos expresivos.

## 5. GuardianEngine.kt (322 líneas)

### Problemas detectados

- Lógica de estado de ánimo basada en ratios de finalización — no en personalidad.
- `derivedExperience` solo computa experiencia numérica.
- No hay personalidad, energía, curiosidad, confianza, vínculo ni preferencias.
- `snapshot()` es un cálculo plano, no una máquina de estados.
- Las etapas (HATCHLING, YOUNG, COMPANION, GUARDIAN, ELDER) solo cambian la apariencia del círculo.

### Recomendación

Añadir personalidad, energía, vínculo, horarios, comportamiento contextual. Convertir a máquina de estados viva.

## 6. GuardianPetView.kt (View-based overlay)

### Problemas detectados

- Canvas personalizado dibuja círculos con ojos.
- Animación básica de flotación (traslación Y) y parpadeo.
- Tocarlo solo reproduce una vibración — no hay detección de caricia ni juego.
- No responde a gestos (doble toque, deslizar, arrastrar).
- No hay tarjetas contextuales ni captura rápida.
- No hay comandos de voz.

### Recomendación

Rediseñar como centro de control externo con gestos: simple tap (tarjeta compacta), double tap (completar), long press (voz), swipe up (agenda), swipe down (ocultar).

## 7. GuardianOverlayService.kt (411 líneas)

### Problemas detectados

- Servicio básico de superposición con View raíz.
- Solo muestra el GuardianPetView + un botón de cierre.
- No hay tarjetas contextuales.
- No hay gestión de gestos multitáctiles.
- No hay respuesta a eventos externos.
- Sin configuración de tamaño/transparencia/posición recordada.

### Recomendación

Añadir tarjetas contextuales, gestos completos, respuesta a eventos, posicionamiento recordado, aplicaciones excluidas, horas silenciosas.

## 8. ContextualAnalyzer.kt (128 líneas)

### Problemas detectados

- Analizador simple basado en palabras clave.
- Detecta fechas sueltas pero no frases completas.
- No distingue "Mañana iremos al supermercado" de "Mañana es viernes".
- No hay análisis semántico local.
- No hay clasificación por intención (tarea, evento, compra).
- La confianza es un valor fijo (0.85) o una heurística simple.
- No integra con teclado, voz, pantalla ni comandos del guardián.

### Recomendación

Nuevo motor con ContextCaptureSource, ContextIntent, ContextIntentEngine, filtro de intenciones permitidas, deduplicación, coordinador de confirmación.

## 9. ContextualSettingsStore.kt (43 líneas)

### Problemas detectados

- Solo almacena enabled/notificationSuggestions/allowedPackages.
- No hay configuración por fuente (teclado, voz, pantalla, guardián).
- No hay pausa programada.
- No hay aplicaciones excluidas por el usuario.

### Recomendación

Extender para soportar todas las fuentes de captura con control individual.

## 10. OrdiaNotificationListenerService.kt (38 líneas)

### Problemas detectados

- Solo filtra por paquete permitido (WhatsApp, Telegram, Signal).
- No usa el nuevo sistema de intenciones.
- No hay debounce ni deduplicación.
- El texto se pasa directamente a ContextualAnalyzer.
- Solo 4 aplicaciones soportadas.

### Recomendación

Integrar con ContextIntentEngine, añadir debounce, deduplicación y filtro de contenido.

## 11. GuardianScreen.kt (293 líneas)

### Problemas detectados

- Mucho contenido: nombre, especie, etapas, estado, estadísticas, vínculo, mensaje.
- Tarjetas anidadas y texto explicativo extenso.
- La sección de evolución muestra solo el nombre de la etapa.
- No hay interacción real (no se puede acariciar, jugar ni hablar).

### Recomendación

Simplificar: mostrar guardián grande + estado + acciones rápidas. Mover evolución y estadísticas a paneles secundarios.

## 12. ContextualAttentionScreen.kt (141+ líneas)

### Problemas detectados

- Solo configura notificaciones como fuente.
- No hay secciones para teclado, voz, pantalla ni guardián.
- La UI de aplicaciones autorizadas solo lista 4 apps.
- No hay indicador de privacidad en vivo.

### Recomendación

Rediseñar con todas las fuentes, privacidad en vivo, on/off granular.

## 13. Backup (BackupManager.kt)

### Problemas detectados

- BackupManager exporta/importa todo como JSON — funcional pero sin protección de privacidad.
- No hay exclusión de datos contextuales.

## 14. Recordatorios (ReminderScheduler.kt)

### Problemas detectados

- Canales de notificación básicos.
- No hay recordatorios contextuales ni basados en ubicación.

## 15. Variantes Gradle (build.gradle.kts)

### Problemas detectados

- 2 variantes actuales: previewSafe, previewFull.
- Falta previewAdvanced para el modo de accesibilidad avanzado.
- previewSafe no tiene INTERNET ni superposición — correcto.
- previewFull tiene todo — correcto pero necesita separación clara.

### Recomendación

Añadir previewAdvanced con AccessibilityService y teclado. previewSafe debe seguir siendo la variante para Google Play.

## 16. Manifiestos

### Problemas detectados

- previewSafe: correcto, sin permisos sensibles.
- previewFull: correcto, con todos los permisos.
- previewAdvanced: no existe aún.

## Problemas generales de UX

### Espacio desperdiciado
- Encabezados con altura excesiva.
- Tarjetas con padding interno grande (16-22dp).
- Texto explicativo en casi cada elemento.
- Espaciado entre ítems de 14-20dp.

### Tarjetas repetitivas
- StatCards y ActionCards compiten por atención en TodayScreen.
- Múltiples SectionHeaders y ScreenHeaders se repiten.
- EmptyState con texto extenso.

### Densidad visual
- La pantalla de inicio muestra ~15 elementos informativos.
- Cada elemento tiene borde, sombra, icono + texto.
- El usuario no puede priorizar qué ver.

### Funciones difíciles de descubrir
- Captura inteligente está en TodayScreen, no en un destino dedicado.
- Compartir texto requiere navegar a Ajustes → Atención contextual.
- El guardián flotante no muestra ayuda inicial.
- No hay onboarding contextual para nuevas funciones.

### Flujos que obligan a entrar
- Toda acción requiere abrir la app.
- No se puede crear tarea desde fuera (notificación, widget limitado).
- El guardián solo mira — no permite crear ni completar.

## Resumen de acciones prioritarias

1. Rebrand "Ordia" → "Ordía" (todos los textos visibles)
2. Nuevo motor contextual (intención + fuentes múltiples)
3. Rediseño navegación (5 destinos)
4. Simplificación TodayScreen
5. SettingsScreen por categorías
6. Guardián como centro de control externo
7. Máquina de estados para guardianes
8. previewAdvanced + AccessibilityService
9. Filtro de intención por lista permitida
10. Sistema de voz (Hey + pulsar)
