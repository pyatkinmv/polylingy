package com.polylingy.app.stats

import androidx.lifecycle.ViewModel
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.study.Statistics
import com.polylingy.core.study.StatisticsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

/** What the statistics screen shows: the numbers for [scope] (null — all courses) and the courses to pick from. */
data class StatisticsState(val scope: Uuid?, val courses: List<Pair<Uuid, String>>, val stats: Statistics)

class StatisticsViewModel(private val service: StatisticsService, private val courses: CourseRepository) : ViewModel() {
    private val _state = MutableStateFlow(load(scope = null))
    val state: StateFlow<StatisticsState> = _state.asStateFlow()

    /** Shows one course, or all of them with null. */
    fun selectScope(courseId: Uuid?) {
        _state.value = load(courseId)
    }

    /** Recounts, e.g. when the screen is opened again after some answers. */
    fun refresh() {
        _state.value = load(_state.value.scope)
    }

    private fun load(scope: Uuid?) = StatisticsState(scope, courses.all().sortedBy { it.createdAt }.map { it.id to it.name }, service.statistics(scope))
}
