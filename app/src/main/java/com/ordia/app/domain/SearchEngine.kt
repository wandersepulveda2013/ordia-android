package com.ordia.app.domain

import java.text.Normalizer
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.TaskEntity

enum class SearchKind { TASK, PROJECT, NOTE, HABIT }

data class SearchResult(val kind: SearchKind, val id: Long, val title: String, val subtitle: String)

object SearchEngine {
    fun search(
        query: String,
        tasks: List<TaskEntity>,
        projects: List<ProjectEntity>,
        notes: List<NoteEntity>,
        habits: List<HabitEntity>
    ): List<SearchResult> {
        val normalized = query.foldForSearch()
        if (normalized.isBlank()) return emptyList()
        fun matches(vararg values: String) = values.any { it.foldForSearch().contains(normalized) }
        return buildList {
            tasks.filter { !it.archived && matches(it.title, it.details) }.forEach {
                add(SearchResult(SearchKind.TASK, it.id, it.title, it.details.take(90)))
            }
            projects.filter { !it.archived && matches(it.name, it.description) }.forEach {
                add(SearchResult(SearchKind.PROJECT, it.id, it.name, it.description.take(90)))
            }
            notes.filter { !it.archived && matches(it.title, it.body) }.forEach {
                add(SearchResult(SearchKind.NOTE, it.id, it.title, it.body.take(90)))
            }
            habits.filter { !it.archived && matches(it.title, it.details) }.forEach {
                add(SearchResult(SearchKind.HABIT, it.id, it.title, it.details.take(90)))
            }
        }.sortedWith(compareBy<SearchResult> { if (it.title.foldForSearch().startsWith(normalized)) 0 else 1 }.thenBy { it.title })
    }
}

private fun String.foldForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
