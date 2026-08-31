package com.ordia.app.backup
import org.json.JSONObject
import org.json.JSONArray
class BackupManager {
    fun exportBackup(): JSONObject {
        val root = JSONObject()
        root.put("projects", JSONArray())
        root.put("tasks", JSONArray())
        root.put("notes", JSONArray())
        root.put("habits", JSONArray())
        root.put("habitLogs", JSONArray())
        root.put("focusSessions", JSONArray())
        root.put("routines", JSONArray())
        root.put("routineSteps", JSONArray())
        root.put("tags", JSONArray())
        root.put("taskTags", JSONArray())
        root.put("attachments", JSONArray())
        return root
    }
}
