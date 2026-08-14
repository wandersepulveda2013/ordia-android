# Ordía Mega Evolution - Backlog

## WAVE 1: Foundation + Design System
- [x] Audit current typography, colors, and shapes.
- [x] Define and implement the core `OrdiaDesignSystem.kt` and update `Theme.kt`, `Type.kt`.
- [x] Create base custom components (`OrdiaButton`, `OrdiaCard`, `OrdiaInput`, etc.) in `AppComponents.kt`.
- [x] Ensure dark/light modes and accessibility contrast requirements are met.
- [x] Add base animations/transitions language.

## WAVE 2: Home + Navigation + Onboarding
- [ ] Redesign Home Screen (focus on NOW, LATER, TODAY).
- [ ] Rewrite Onboarding flow (premium, brief, no long forms).
- [ ] Refine Navigation (adaptive, contextual).

## WAVE 3: Universal Capture
- [ ] Implement Universal Capture Engine backend.
- [ ] Add entry points for text, voice, clipboard, etc.
- [ ] Develop intelligence to parse intents (tasks, dates, routines).

## WAVE 4: Clipboard Intelligence
- [ ] Implement clipboard listener/service.
- [ ] Add intent detection for copied text.
- [ ] Create non-intrusive UI to offer actions.

## WAVE 5: Ordía Keyboard Foundation
- [ ] Setup `InputMethodService` structure.
- [ ] Implement basic keyboard UI (keys, numbers, symbols, shift, delete, etc.).

## WAVE 6: Keyboard Intelligence
- [ ] Add the Ordía Bar above the keyboard.
- [ ] Implement contextual suggestions based on typing.
- [ ] Add keyboard-specific clipboard functionality.

## WAVE 7: Automation Engine
- [ ] Design Automation Engine architecture (Rules, Triggers, Actions).
- [ ] Implement "When X Then Y" core logic.
- [ ] Create pre-built automation rules.

## WAVE 8: What Now 2.0 / 3.0
- [ ] Enhance `GuardianCoach.kt` logic.
- [ ] Factor in time, energy, deadlines, routines, and user history.
- [ ] Present a single primary recommendation with explicit reasoning.

## WAVE 9: Guardians
- [ ] Refactor Guardians to be autonomous agents with functional personalities.
- [ ] Assign specific responsibilities (Time, Habits, Finances, etc.).

## WAVE 10: Notes
- [ ] Redesign Notes screen (closer to Notion/Keep but simpler).
- [ ] Support blocks, lists, formatting, attachments.

## WAVE 11: Universal Search
- [ ] Implement cross-entity search (tasks, notes, events, etc.).
- [ ] Add command palette functionality.

## WAVE 12: Performance & Offline First
- [ ] Audit and optimize startup, DB queries, UI rendering.
- [ ] Ensure offline robustness for core features.

## WAVE 13: Accessibility & Polish
- [ ] Comprehensive accessibility audit (TalkBack, contrast, targets).
- [ ] Refine micro-interactions and animations.

## WAVE 14: QA Adversarial
- [ ] Run extreme tests (large datasets, rotation, low memory, etc.).
- [ ] Fix edge cases.

## WAVE 15: Release Hardening
- [ ] Final CI/CD checks, migrations validation, release builds.
