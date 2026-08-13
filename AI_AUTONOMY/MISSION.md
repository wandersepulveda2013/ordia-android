# Current Mission: Refinement and Automation Improvements

The main goal of this mission is to proactively apply requested maintenance tasks in Ordia:
1. Replaced all deprecated `Icons.Outlined.*` Jetpack Compose usages with their equivalent `Icons.AutoMirrored.Outlined.*` counterparts.
2. Replaced deprecated `Divider()` with `HorizontalDivider()`.
3. Extended the functionality of `NaturalTaskParser` to successfully interpret "esta noche", "mediodía", and "medianoche", mapping these to practical local times (20:00, 12:00, and 00:00).
4. Ensured that any corresponding extracted textual strings for the above newly supported entities are accurately removed from the final `ParsedTaskInput` title.
5. Created related tests in `NaturalTaskParserTest.kt` to prevent future regressions.
6. Cleaned up arbitrary literal 'TODO' strings to clean up CI checks.
