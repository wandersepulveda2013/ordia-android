1. **Theme Reorganization**: Rename `PaperColors.kt` to `Colors.kt` and extract typography into `Type.kt` to follow the standard `Theme.kt`, `Type.kt`, `Colors.kt` structure in `app/src/main/java/com/ordia/app/ui/theme/`. Update `Theme.kt` accordingly.
2. **Design System Creation**: Create `OrdiaDesignSystem.kt` in `app/src/main/java/com/ordia/app/ui/components/`. Implement only the components actually used by the current minimalist notepad:
   - `OrdiaSurface` / `OrdiaScaffold`
   - `OrdiaTopAppBar`
   - `OrdiaFloatingActionButton`
   - `OrdiaInput` (custom text field without borders)
   - `OrdiaCard` or `OrdiaNoteRow` (for the list items)
3. **Refactor Existing Screens**:
   - Update `NotesListScreen.kt` and `NoteEditorScreen.kt` to use the new `OrdiaDesignSystem` components.
   - Enhance `NoteEditorScreen.kt` to use `FocusRequester` and `LaunchedEffect` to automatically focus the input when creating a new note, per the guidelines.
4. **Testing and Verification**: Ensure all tests pass. Run `./gradlew testPreviewSafeDebugUnitTest`, `./gradlew lintPreviewSafeDebug`, and ensure the app compiles (`./gradlew assembleDebug`). Static verification (`verify_project.py`) will fail for missing specific files (like `OrdiaViewModel.kt`), but this is expected as per the wave instructions (we are not adding empty stubs).
5. **Update Autonomy Files**: Document the execution of WAVE 1 in `AI_AUTONOMY/CURRENT_STATE.md`, `AI_AUTONOMY/RUN_LOG.md`, and `AI_AUTONOMY/BACKLOG.md`.
6. **Pre-commit and Submit**: Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done, then submit the change.
