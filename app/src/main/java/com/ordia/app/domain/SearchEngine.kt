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

        val results = ArrayList<Pair<SearchResult, Boolean>>()

        for (it in tasks) {
            if (it.archived) continue
            val titleFolded = it.title.foldForSearch()
            if (titleFolded.contains(normalized) || it.details.foldForSearch().contains(normalized)) {
                results.add(SearchResult(SearchKind.TASK, it.id, it.title, it.details.take(90)) to titleFolded.startsWith(normalized))
            }
        }
        for (it in projects) {
            if (it.archived) continue
            val titleFolded = it.name.foldForSearch()
            if (titleFolded.contains(normalized) || it.description.foldForSearch().contains(normalized)) {
                results.add(SearchResult(SearchKind.PROJECT, it.id, it.name, it.description.take(90)) to titleFolded.startsWith(normalized))
            }
        }
        for (it in notes) {
            if (it.archived) continue
            val titleFolded = it.title.foldForSearch()
            if (titleFolded.contains(normalized) || it.body.foldForSearch().contains(normalized)) {
                results.add(SearchResult(SearchKind.NOTE, it.id, it.title, it.body.take(90)) to titleFolded.startsWith(normalized))
            }
        }
        for (it in habits) {
            if (it.archived) continue
            val titleFolded = it.title.foldForSearch()
            if (titleFolded.contains(normalized) || it.details.foldForSearch().contains(normalized)) {
                results.add(SearchResult(SearchKind.HABIT, it.id, it.title, it.details.take(90)) to titleFolded.startsWith(normalized))
            }
        }

        return results.sortedWith(compareBy<Pair<SearchResult, Boolean>> { if (it.second) 0 else 1 }.thenBy { it.first.title }).map { it.first }
    }
}

private val DIACRITICS_REGEX = Regex("\\p{M}+")

private fun String.foldForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
