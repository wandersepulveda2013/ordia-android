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

        return buildList {
            tasks.filter { !it.archived }.forEach {
                val nTitle = it.title.foldForSearch()
                if (nTitle.contains(normalized) || it.details.foldForSearch().contains(normalized)) {
                    add(Pair(SearchResult(SearchKind.TASK, it.id, it.title, it.details.take(90)), nTitle))
                }
            }
            projects.filter { !it.archived }.forEach {
                val nName = it.name.foldForSearch()
                if (nName.contains(normalized) || it.description.foldForSearch().contains(normalized)) {
                    add(Pair(SearchResult(SearchKind.PROJECT, it.id, it.name, it.description.take(90)), nName))
                }
            }
            notes.filter { !it.archived }.forEach {
                val nTitle = it.title.foldForSearch()
                if (nTitle.contains(normalized) || it.body.foldForSearch().contains(normalized)) {
                    add(Pair(SearchResult(SearchKind.NOTE, it.id, it.title, it.body.take(90)), nTitle))
                }
            }
            habits.filter { !it.archived }.forEach {
                val nTitle = it.title.foldForSearch()
                if (nTitle.contains(normalized) || it.details.foldForSearch().contains(normalized)) {
                    add(Pair(SearchResult(SearchKind.HABIT, it.id, it.title, it.details.take(90)), nTitle))
                }
            }
        }.sortedWith(compareBy<Pair<SearchResult, String>> { if (it.second.startsWith(normalized)) 0 else 1 }.thenBy { it.first.title }).map { it.first }
    }
}

private val DIACRITICS_REGEX = Regex("\\p{M}+")

private fun String.foldForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
