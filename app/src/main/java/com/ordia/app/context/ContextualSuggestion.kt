package com.ordia.app.context

enum class ContextualKind { TASK, EVENT, STUDY, NOTE }

data class ContextualSuggestion(
    val id: String,
    val kind: ContextualKind,
    val title: String,
    val dueAt: Long? = null,
    val confidence: Double,
    val sourcePackage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(id.matches(Regex("[a-f0-9]{64}")))
        require(title.isNotBlank() && title.length <= 100)
        require(confidence in 0.0..1.0)
        require(sourcePackage == null || sourcePackage.length <= 180)
    }
}
