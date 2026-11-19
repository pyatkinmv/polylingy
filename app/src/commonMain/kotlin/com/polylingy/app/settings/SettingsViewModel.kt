package com.polylingy.app.settings

import androidx.lifecycle.ViewModel
import com.polylingy.content.Exercise
import com.polylingy.core.content.DisplaySpec
import com.polylingy.core.content.Course
import com.polylingy.core.content.GradingSpec
import com.polylingy.core.content.MasterySpec
import com.polylingy.core.content.ExerciseSpec
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.content.Preset
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.content.SchedulerSpec
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.learning.Attempt
import com.polylingy.core.learning.CourseRules
import com.polylingy.core.learning.PassFail
import com.polylingy.core.learning.toRules
import com.polylingy.core.ports.Clock
import com.polylingy.core.study.ProgressCounts
import com.polylingy.core.study.StudyService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/** The three pickers as the settings screen offers them. */
enum class PickerKind { CONVEYOR, SHUFFLE, ALTERNATE }

/** When a topic done today comes back if every review is clean: [dates], then [learned] — learned for good. */
data class ReviewPlan(val dates: List<LocalDate>, val learned: Boolean)

/**
 * The study settings of one course: the four questions are the settings (docs/learning-model.md, section 7). A ready
 * set only fills in questions 1–3; [matching] tells which set the answers match, if any. Nothing is saved until [save].
 */
class SettingsViewModel(private val study: StudyService, private val clock: Clock, val courseId: Uuid) : ViewModel() {
    private val stored: RulesSpec? = study.settings(courseId)

    /** The course as it was when the screen opened; null if it is gone. */
    val course: Course? = study.progress(courseId)?.course

    /** Progress with the saved settings, to compare with [after]. */
    val before: ProgressCounts? = study.progress(courseId)?.counts

    private val _draft = MutableStateFlow(stored ?: RulesSpec().resolved())

    /** The settings as edited, every part written out. */
    val draft: StateFlow<RulesSpec> = _draft.asStateFlow()

    private val _after = MutableStateFlow(before)

    /** Progress as it would be with [draft]. */
    val after: StateFlow<ProgressCounts?> = _after.asStateFlow()

    /** The last daily limit, to come back to when "no limit" is turned off. */
    private var lastLimit: Int = stored?.picker?.newPerDay ?: DEFAULT_LIMIT

    val changed: Boolean get() = _draft.value != stored

    /** Whether the draft changes questions 1–3, which recount progress; the picker never does. */
    val recounts: Boolean
        get() = _draft.value.let { it.grading != stored?.grading || it.mastery != stored?.mastery || it.scheduler != stored?.scheduler }

    /** The ready set the answers to questions 1–3 match, if any. */
    val matching: Preset? get() = _draft.value.matchingPreset()

    private val grading get() = _draft.value.grading!!
    private val mastery get() = _draft.value.mastery as MasterySpec.Consecutive
    private val picker get() = _draft.value.picker!!

    /** Fills in questions 1–3 from a ready set; question 4 stays. */
    fun usePreset(preset: Preset) {
        val spec = preset.spec
        update { it.copy(preset = preset, grading = spec.grading, mastery = spec.mastery, scheduler = spec.scheduler) }
    }

    // 1. When does an answer count?

    fun allCorrect() = update { it.copy(grading = GradingSpec.AllCorrect(grading.typos)) }

    fun mostCorrect() = update { it.copy(grading = GradingSpec.Threshold(DEFAULT_MIN, grading.typos)) }

    /** The threshold in percent, by steps of 5 between 50 and 95. */
    fun changeMin(by: Int) {
        val g = grading as? GradingSpec.Threshold ?: return
        val percent = ((g.min * 100).roundToInt() + by).coerceIn(50, 95)
        update { it.copy(grading = g.copy(min = percent / 100.0)) }
    }

    fun typos(forgive: Boolean) = update {
        it.copy(grading = when (val g = grading) {
            is GradingSpec.AllCorrect -> g.copy(typos = forgive)
            is GradingSpec.Threshold -> g.copy(typos = forgive)
        })
    }

    // 2. When is a topic done for now?

    fun changeLearning(by: Int) = update { it.copy(mastery = mastery.copy(learning = (mastery.learning + by).coerceIn(1, MAX_IN_A_ROW))) }

    fun changeReview(by: Int) = update { it.copy(mastery = mastery.copy(review = (mastery.review + by).coerceIn(1, MAX_IN_A_ROW))) }

    // 3. When does a done topic come back?

    fun scheduler(scheduler: SchedulerSpec) = update { it.copy(scheduler = scheduler) }

    fun retire(retire: Boolean) {
        val ladder = _draft.value.scheduler as? SchedulerSpec.Ladder ?: return
        update { it.copy(scheduler = ladder.copy(retire = retire)) }
    }

    // 4. What comes next in a session?

    fun picker(kind: PickerKind) = update {
        val limit = picker.newPerDay
        val exercise = picker.exercise
        it.copy(picker = when (kind) {
            PickerKind.CONVEYOR -> PickerSpec.Conveyor(limit, exercise)
            PickerKind.SHUFFLE -> PickerSpec.Shuffle(limit, exercise)
            PickerKind.ALTERNATE -> PickerSpec.Alternate(limit, exercise)
        })
    }

    // 5. Which exercise of a topic?

    fun exercise(spec: ExerciseSpec) = update {
        it.copy(picker = when (val p = picker) {
            is PickerSpec.Conveyor -> p.copy(exercise = spec)
            is PickerSpec.Shuffle -> p.copy(exercise = spec)
            is PickerSpec.Alternate -> p.copy(exercise = spec)
        })
    }

    fun changeLimit(by: Int) {
        val limit = ((picker.newPerDay ?: lastLimit) + by).coerceIn(1, MAX_LIMIT)
        lastLimit = limit
        setLimit(limit)
    }

    fun noLimit(none: Boolean) = setLimit(if (none) null else lastLimit)

    private fun setLimit(limit: Int?) = update {
        it.copy(picker = when (val p = picker) {
            is PickerSpec.Conveyor -> p.copy(newPerDay = limit)
            is PickerSpec.Shuffle -> p.copy(newPerDay = limit)
            is PickerSpec.Alternate -> p.copy(newPerDay = limit)
        })
    }

    /** When a topic done today comes back under the draft's schedule, every review clean; at most [count] dates. */
    fun plan(count: Int = 5): ReviewPlan = plan(_draft.value.toRules(Random(0)), clock.now(), count)

    /** Saves the settings; an error message, or null when saved. */
    // How exercises look — not one of the four questions; changes nothing in progress.

    private val display get() = _draft.value.display ?: DisplaySpec()

    fun annotations(show: Boolean) = update { it.copy(display = display.copy(annotations = show)) }

    fun avoidTyping(avoid: Boolean) = update { it.copy(display = display.copy(avoidTyping = avoid)) }

    /** The palette of the course, by its id (see [com.polylingy.content.compose.Palettes]). */
    fun palette(id: String) = update { it.copy(display = display.copy(palette = id)) }

    /** The style of the course: how marks and details are set off. */
    fun style(style: com.polylingy.content.CourseStyle) = update { it.copy(display = display.copy(style = style)) }

    /** The course's topics that have exercises, and those whose every exercise needs typing: avoiding typing keeps them. */
    val typing: Pair<Int, Int>
        get() {
            val topics = course?.allTopics.orEmpty().map { topic -> topic.exercises.filterNot { it is Exercise.Unknown } }.filter { it.isNotEmpty() }
            return topics.count { exercises -> exercises.all { it.needsKeyboard } } to topics.size
        }

    fun save(): String? = try {
        study.saveSettings(courseId, _draft.value)
        null
    } catch (e: IllegalArgumentException) {
        log.warn { "Settings of course $courseId not saved: ${e.message}" }
        e.message ?: "The settings can’t be saved"
    }

    private fun update(change: (RulesSpec) -> RulesSpec) {
        _draft.value = change(_draft.value)
        _after.value = study.progressWith(courseId, _draft.value)?.counts
    }

    companion object {
        const val DEFAULT_MIN = 0.75
        const val DEFAULT_LIMIT = 10
        const val MAX_IN_A_ROW = 10
        const val MAX_LIMIT = 100

        private fun <M> plan(rules: CourseRules<M, PassFail>, now: ZonedInstant, count: Int): ReviewPlan {
            val clean = listOf(Attempt(PassFail.PASS, emptyList(), now))
            var memory = rules.scheduler.schedule(null, clean, now.localDate)
            val dates = mutableListOf<LocalDate>()
            while (dates.size < count) {
                val due = rules.scheduler.dueDate(memory) ?: return ReviewPlan(dates, learned = true)
                dates += due
                memory = rules.scheduler.schedule(memory, clean, due)
            }
            return ReviewPlan(dates, learned = rules.scheduler.dueDate(memory) == null)
        }
    }
}
