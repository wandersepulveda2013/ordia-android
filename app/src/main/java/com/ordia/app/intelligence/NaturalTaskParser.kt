package com.ordia.app.intelligence

import java.util.Calendar

data class ParseResult(
    val isTask: Boolean,
    val title: String,
    val dueDate: Long?
)

object NaturalTaskParser {

    private val dateKeywords = mapOf(
        "hoy" to 0,
        "mañana" to 1,
        "pasado mañana" to 2
    )

    private val taskIntentKeywords = listOf(
        "comprar", "pagar", "llamar", "recordar", "hacer", "terminar", "estudiar", "preparar", "enviar"
    )

    fun parse(input: String, now: Long = System.currentTimeMillis()): ParseResult {
        val lowerInput = input.lowercase().trim()

        var foundDaysOffset: Int? = null
        var foundKeyword: String? = null

        // Sort keys by length descending to match longest phrase first (e.g., "pasado mañana" before "mañana")
        for (keyword in dateKeywords.keys.sortedByDescending { it.length }) {
            if (lowerInput.contains(keyword)) {
                foundDaysOffset = dateKeywords[keyword]
                foundKeyword = keyword
                break
            }
        }

        val isTask = taskIntentKeywords.any { lowerInput.startsWith(it) } || foundDaysOffset != null

        val dueDate = foundDaysOffset?.let {
            val calendar = Calendar.getInstance().apply { timeInMillis = now }
            calendar.add(Calendar.DAY_OF_YEAR, it)
            // Reset to end of day to make due date generic for the day
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            calendar.timeInMillis
        }

        // Clean up the title by removing the matched keyword if it exists
        var title = input
        if (foundKeyword != null) {
            // Regex to remove the keyword and any trailing/leading extra spaces
            title = title.replace(Regex("(?i)\\b$foundKeyword\\b"), "").replace(Regex("\\s+"), " ").trim()
        }

        // Ensure title is not empty, fallback to input if it somehow gets empty
        if (title.isBlank()) {
           title = input
        }

        return ParseResult(
            isTask = isTask,
            title = title,
            dueDate = dueDate
        )
    }
}
