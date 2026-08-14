# Ordía - Current State

## Architecture
- Android Kotlin App, Jetpack Compose for UI.
- Local-first (Room Database).
- ViewModels for UI state.
- Hilt/Dagger NOT used currently, manual DI in AppContainer.
- Coroutines & Flows for reactive programming.

## UX Weaknesses (Top 20)
1. Generic Material 3 look.
2. Lack of cohesive animation system.
3. Spacing is sometimes too tight.
4. Button hierarchy is not strictly defined (primary vs secondary vs ghost).
5. Input fields look like standard Android fields, lacking personality.
6. Dialogs and sheets are standard Material.
7. Cards have a default border/shadow that might be too rigid.
8. Navigation transitions are default.
9. Loading states lack elegance.
10. Empty states are too static.
11. Typography relies on default SansSerif without distinct character.
12. Contrast in some dark mode accents might be low.
13. Heavy reliance on borders instead of spacing for grouping.
14. Touch targets on some custom chips could be small.
15. Lack of micro-interactions on click/toggle.
16. Unclear visual distinction between different task priorities.
17. The home screen is slightly cluttered with 20 stats/cards.
18. Notifications and snackbars don't feel "premium".
19. Settings screen is probably a generic list.
20. Iconography is standard Material out-of-the-box.

## Opportunities (Top 20)
1. Universal Capture (Text/Voice/Image to entity).
2. Keyboard App integration.
3. Smart Clipboard listener.
4. Adaptive UI ("What Now" focused).
5. Automation engine (IF/THEN).
6. Smart Guardians that take action.
7. Advanced rich-text Notes (Notion-like but simple).
8. NLP for date/time parsing.
9. Universal semantic search.
10. Reduced friction onboarding.
11. Contextual command palette.
12. Local analytics for habits/productivity.
13. Smart grouping of tasks.
14. Auto-rescheduling of overdue tasks.
15. Quick swipe actions with haptic feedback.
16. Dynamic color based on time of day / mood.
17. Offline-first LLM/NLP (if possible locally, else heuristics).
18. Focus mode with deep integrations.
19. Visual progress rings with fluid animations.
20. Zero-UI interactions (voice/intent based).

## Technical Risks (Top 10)
1. Room migrations during large schema changes.
2. Performance of deep subtask recursion.
3. Background battery drain from Guardians/Automations.
4. Privacy issues with Clipboard/Keyboard listening.
5. Concurrency bugs in local DB.
6. Complexity of NLP parsing locally.
7. UI recomposition overhead with complex animations.
8. App size increase if bringing local models.
9. Backup/Restore consistency with new features.
10. Keyboard API restrictions in newer Android versions.
