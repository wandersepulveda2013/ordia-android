# ROADMAP - MEGA EVOLUCIÓN AUTÓNOMA DE ORDÍA

## WAVE 1: Foundation & Design System (En curso)
- Paleta de colores coherente y contrastes (Theme.kt, Colors.kt si necesario).
- Tipografía pulida y espaciados generales.
- Rediseño de componentes core (AppComponents.kt): Botones, Chips, Cards, TextFields.
- Eliminación de bordes redundantes o tarjetas anidadas en exceso.
- Mejora de accesibilidad base (touch targets, contraste).

## WAVE 2: Home & Navigation
- Rediseño de TodayScreen.kt (Home).
- Simplificación del layout, priorizando "AHORA" / "DESPUÉS".
- Re-estructurar navegación principal si es necesario.

## WAVE 3: Universal Capture
- Implementar motor de captura rápido y universal.
- Parseo de intención (NaturalTaskParser mejoras o nueva clase).

## WAVE 4: Clipboard Intelligence
- Interceptar clipboard al abrir app.
- Análisis de contenido y sugerencias de acciones (Agendar, Recordar, Guardar nota).

## WAVE 5: Ordía Keyboard Foundation
- Crear esqueleto de InputMethodService.
- UI del teclado (letras, números, símbolos).

## WAVE 6: Keyboard Intelligence
- Barra Ordía sobre teclado.
- Acciones contextuales discretas mientras se escribe.

## WAVE 7: Automation Engine
- Estructura "Cuando X, Entonces Y".
- UI para configurar o habilitar automatizaciones.

## WAVE 8: Decision Engine & What Now 2.0
- Mejorar GuardianCoach y What Now.
- Recomendación única y fundamentada.

## WAVE 9: Guardians
- Dar identidad y acciones a Guardianes (Finanzas, Hábitos, Tiempo, etc).

## WAVE 10: Notes (Notion/Keep style)
- Mejoras a NoteEditorScreen.
- Listas, bloques, plantillas básicas.

## WAVE 11: Performance & Accessibility
- Auditoría general (startup, recompositions, TalkBack).

## WAVE 12: Polish & Animations
- Microinteracciones.
- Morphing, transiciones.
