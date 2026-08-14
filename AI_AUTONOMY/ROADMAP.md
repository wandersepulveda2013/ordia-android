# ROADMAP - Ordía Mega Evolución

## WAVE 1: Foundation + Design System (En curso)
**Objetivo:** Establecer la base visual y estructural. "A veces menos es más".
- [ ] Centralizar colores semánticos (SemanticAlert, SemanticSuccess, SemanticFocus, SemanticAutomation) en `Theme.kt`.
- [ ] Refinar `Type.kt` (eliminar pesos excesivos, tamaños coherentes).
- [ ] Crear archivo `OrdiaDesignSystem.kt` con componentes base (OrdiaCard, OrdiaButton, OrdiaInput, OrdiaSurface).
- [ ] Migrar componentes existentes (`AppComponents.kt`, `TaskComponents.kt`) a usar prefijos `Ordia`.
- [ ] Asegurar soporte estricto de accesibilidad (touch targets, talkback) en los componentes base.
- [ ] Completar tests de componentes.

## WAVE 2: Home + Navigation
- [ ] Implementar nuevo `OrdiaRoot.kt` adaptativo.

(siguientes waves se detallarán al avanzar)
