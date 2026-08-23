package com.ordia.app.backup

class BackupManager {
    fun getBackupCollections(): List<String> {
        return listOf(
            "projects",
            "tasks",
            "notes",
            "habits",
            "habitLogs",
            "focusSessions",
            "routines",
            "routineSteps",
            "tags",
            "taskTags",
            "attachments"
        )
    }
}
