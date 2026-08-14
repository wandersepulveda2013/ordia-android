# WAVE 1: Foundation + Design System

Based on the MEGA EVOLUTION mission requirements (Sections 1, 24, 25), Ordia needs a unique visual identity, and avoiding standard Material Design generic UI. It needs a strict set of centralized custom components for consistency.

The project currently has `AppComponents.kt` and `TaskComponents.kt`, but it's missing the core custom `OrdiaButton`, `OrdiaCard`, `OrdiaInput`, `OrdiaSurface` mentioned in the mission. It is using raw `Button`, `OutlinedButton`, `Card`, and `OutlinedTextField` everywhere.

### Scope for Wave 1:
1.  **Create `OrdiaDesignSystem.kt`** in `app/src/main/java/com/ordia/app/ui/components/`.
2.  **Define `OrdiaButton`**: Primary, secondary/outline, text variants, avoiding generic Material shadows/colors.
3.  **Define `OrdiaCard`**: Minimalist surface cards with proper shape and subtle borders, completely replacing standard `Card`.
4.  **Define `OrdiaSurface`**: Background surfaces replacing raw `Surface`.
5.  **Define `OrdiaInput`**: Minimalist text fields replacing `OutlinedTextField` (remove the 'boxed' look if possible, or make it elegantly integrated).
6.  **Refactor `AppComponents.kt`, `TaskComponents.kt`, and `EditorDialogs.kt`** to use these new foundation components.
7.  **Verify Accessibility** (contrast, touch targets) for these components.
