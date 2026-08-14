# Ordía Mega Evolution - Backlog

## WAVE 1: Foundation + Design System
- [ ] Create `OrdiaDesignSystem.kt` to centralize all foundational visual components (OrdiaCard, OrdiaButton, OrdiaSurface, OrdiaInput).
- [ ] Refine `Theme.kt` and `Type.kt`. Keep `accentSwatches` and base semantic colors (Alert, Success, Focus, Automation) against a strict white, black, and gray base. Use lighter font weights and generous spacing in `Type.kt`.
- [ ] Migrate existing standard Material 3 components in `AppComponents.kt` and `TaskComponents.kt` to the new `Ordia` prefixed components to ensure centralized visual coherence.
- [ ] Clean up redundant or excessively nested cards/surfaces. Remove unnecessary gradients or excessive shadows (no "AI-generated" tropes).

## Technical Debt / CI
- [ ] Fix `Divider()` to `HorizontalDivider()` in `TaskComponents.kt` and others.
- [ ] Ensure any remaining deprecations are addressed (e.g. `LocalClipboardManager`).
