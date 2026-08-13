# Current State

- Resolved Jetpack Compose icon deprecations (Icons.Outlined.* -> Icons.AutoMirrored.Outlined.*)
- Resolved Jetpack Compose Divider deprecation (Divider -> HorizontalDivider)
- Next up: Reviewing NaturalTaskParser test improvements and bug fixes.
- Enhanced NaturalTaskParser to properly support "esta noche", "mediodía", and "medianoche" time expressions, mapping them to 20:00, 12:00, and 00:00 respectively, and correctly hiding the string matches from the resulting task titles. Unit tests also updated.
- Removed obsolete 'TODO' string occurrences from GuardianCoach.kt and TasksScreen.kt that might falsely flag as unresolved tasks in code searches.
