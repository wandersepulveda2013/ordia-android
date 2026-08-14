# ROADMAP INTERNO: MEGA EVOLUCIÓN DE ORDÍA

## Arquitectura Actual
- Kotlin + Jetpack Compose (UI)
- MVI/MVVM con `OrdiaViewModel` centralizado (Deuda técnica: un solo ViewModel masivo para toda la app).
- Room Database (offline-first).
- Repositories (TaskRepository, ProjectRepository, etc.).
- AlarmManager para recordatorios (ReminderScheduler).

## Deuda Técnica (Top 10 Riesgos)
1. `OrdiaViewModel` excesivamente grande y monolítico.
2. Inyección de dependencias manual (AppContainer) poco escalable.
3. `UpdateChecker` y `UpdateInstaller` pueden fallar silenciosamente en diferentes OEMs.
4. Complejidad en las migraciones de Room (falta esquema estructurado explícito).
5. Exceso de recomposiciones por estado global en un solo Flow.
6. Gestión del estado de Onboarding fuertemente acoplada a SharedPreferences y ViewModel global.
7. Manejo de corrutinas global vs ligado al ciclo de vida en algunas capas.
8. Bloqueo de UI por lecturas pesadas de Room si no se pagina adecuadamente (tareas y notas).
9. Falta de test de instrumentación en entorno CI/CD.
10. `DateRules` y lógica de tiempo no encapsuladas al 100% (riesgos de timezone).

## Oportunidades Funcionales (Top 20)
1. Universal Capture (texto libre a entidades).
2. Keyboard App (Ordia Keyboard).
3. What Now 2.0 (Motor de recomendación).
4. Automatizaciones (Eventos -> Acciones).
5. Smart Clipboard Scanner (con privacidad).
6. Notas enriquecidas (bloques, markdown).
7. Decision Engine integrado.
8. Búsqueda Universal Semántica.
9. Guardianes con "personalidad" real (Bots).
10. Insights analíticos de comportamiento (local).
11. Filtros contextuales (Zero UI).
12. NLP Parser avanzado para Tareas.
13. Agrupación inteligente de notificaciones.
14. Dependencias entre tareas (bloqueadores).
15. Gestión de energía y duración de tareas.
16. Comandos rápidos (Command Palette).
17. Navegación predictiva basada en la hora.
18. Respuestas rápidas en teclado.
19. Plantillas de proyectos/rutinas.
20. Modo de enfoque profundo (bloqueo de distracciones).

## Debilidades UX (Top 20)
1. Colores "Arcoíris" (Gold, Sage, Rose, etc.) vs. minimalismo deseado.
2. Tipografía sin jerarquía premium y demasiados tamaños.
3. Componentes genéricos Material 3 sin identidad fuerte.
4. Avatar de Guardián hecho con Canvas muy básico.
5. Onboarding largo y explicativo (no "Show, don't tell").
6. Backgrounds con patrón de puntos (parece dashboard corporativo).
7. Falta de microinteracciones fluidas.
8. Tarjetas con bordes repetitivos y sombras excesivas.
9. Demasiados clics para capturar tareas.
10. Densidad de información muy alta en Planner/Tasks.
11. Espaciado inconsistente o muy ajustado.
12. Formularios largos para crear elementos.
13. Iconografía inconsistente o poco refinada.
14. Contraste en tema oscuro y claro puede mejorar.
15. Falta de motion al completar/eliminar.
16. Empty states aburridos.
17. Botones flotantes (FAB) genéricos.
18. Feedback visual pobre (Snackbars básicos).
19. Falta de modo "Zero UI" en la pantalla de inicio.
20. Configuración abrumadora para usuarios nuevos.

## Fases de la Evolución (Waves)
- **WAVE 1**: Foundation & Design System (Limpieza visual, tipografía, paleta, componentes base).
- **WAVE 2**: Home & Universal Capture (Rediseño Home, motor NLP base).
- **WAVE 3**: Onboarding & Zero UI (Nueva filosofía de entrada).
- **WAVE 4**: What Now & Decision Engine.
- **WAVE 5**: Ordia Keyboard (Fundamentos).
- **WAVE 6**: Clipboard & Keyboard Intelligence.
- **WAVE 7**: Automation Engine & Guardians.
- **WAVE 8**: Rich Notes (Bloques, Markdown).
- **WAVE 9**: Performance & Refactoring del ViewModel Monolítico.
- **WAVE 10**: Polish, Motion & Accessibility.
