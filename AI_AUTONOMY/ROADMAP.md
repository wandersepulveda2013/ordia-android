# ROADMAP - MEGA EVOLUCIÓN AUTÓNOMA

## WAVE 1: foundation + design system (CURRENT)
- [x] Consolidate UI components into `core` package (Skipped for now, as it causes massive import issues and is not functionally required for Wave 1)
- [ ] Define OrdiaDesignSystem (Typography, Colors, Shapes, Spacing, Animation)
- [ ] Centralize reusable UI components (OrdiaButton, OrdiaInput, OrdiaCard, etc.)
- [ ] Refactor existing components to use the new Design System
- [ ] Update Theme.kt and Type.kt to reflect the new visual identity
- [ ] Fix Compose deprecations (Divider -> HorizontalDivider, etc.)

## WAVE 2: home + navigation
- [ ] Redesign Home Screen (Today, Now, Later)
- [ ] Simplify Navigation
- [ ] Implement new Contextual Interface logic

## WAVE 3: universal capture
- [ ] Implement Universal Capture Engine
- [ ] Create UI for Quick Capture

## WAVE 4: clipboard intelligence
- [ ] Background clipboard monitoring (within privacy limits)
- [ ] Suggest actions based on copied text

## WAVE 5: Ordía Keyboard foundation
- [ ] Set up InputMethodService
- [ ] Create basic keyboard UI
- [ ] Implement basic typing functionality

## WAVE 6: keyboard intelligence
- [ ] Add Ordía Action Bar to Keyboard
- [ ] Implement contextual actions from keyboard input

## WAVE 7: automation engine
- [ ] Build "When X then Y" rule engine
- [ ] Create default automation rules

## WAVE 8: What Now
- [ ] Upgrade Decision Engine logic
- [ ] Improve What Now UI recommendations

## WAVE 9: Guardians
- [ ] Implement autonomous agents (Time, Habits, Finances, etc.)
- [ ] Give Guardians memory and proactive actions

## WAVE 10: Notes
- [ ] Enhance Note Editor (blocks, formatting, checklists)
- [ ] Improve Note UI and organization

## WAVE 11: performance
- [ ] Profile memory and UI rendering
- [ ] Optimize database queries and flows

## WAVE 12: accessibility
- [ ] Improve contrast and talkback support
- [ ] Enhance touch targets

## WAVE 13: polish + animations
- [ ] Add micro-interactions and transitions
- [ ] Refine motion design

## WAVE 14: QA adversarial
- [ ] Edge cases testing (large data, configuration changes)
- [ ] Bug fixing

## WAVE 15: release hardening
- [ ] Final sanity checks
- [ ] Prepare for production release
