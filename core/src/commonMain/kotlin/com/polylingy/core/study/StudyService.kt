package com.polylingy.core.study

import com.polylingy.core.content.Course
import com.polylingy.core.content.Exercise
import com.polylingy.core.content.Topic
import com.polylingy.core.journal.AnswerSubmitted
import com.polylingy.core.journal.AutoCheck
import com.polylingy.core.journal.EventEnvelope
import com.polylingy.core.journal.Rating
import com.polylingy.core.ports.Clock
import com.polylingy.core.ports.CourseRepository
import com.polylingy.core.ports.EventLog
import com.polylingy.core.progress.LearningRules
import com.polylingy.core.progress.ProgressStatus
import com.polylingy.core.progress.TopicState
import com.polylingy.core.progress.replay
import com.polylingy.core.rules.RandomTopicPicker
import com.polylingy.core.rules.TopicPicker
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.uuid.Uuid

data class CourseStats(val course: Course, val newCount: Int, val toReview: Int, val doneToday: Int) {
    val eligible: Int get() = newCount + toReview
}

data class NextExercise(val topic: Topic, val exercise: Exercise)

/** Use cases the study screens call; all learning rules live behind this class. */
class StudyService(
    private val courses: CourseRepository,
    private val journal: EventLog,
    private val clock: Clock,
    private val deviceId: Uuid,
    private val rules: LearningRules = LearningRules(),
    private val picker: TopicPicker = RandomTopicPicker,
    private val random: Random = Random.Default,
) {
    /** Counters for the course list, oldest course first. Topics without exercises can't be studied and aren't counted. */
    fun courseStats(): List<CourseStats> {
        val all = courses.all().sortedBy { it.createdAt }
        val states = replay(all, journal.all(), rules)
        val today = today()
        return all.map { course ->
            var newCount = 0
            var toReview = 0
            var doneToday = 0
            for (topic in course.studyableTopics()) {
                val progress = states.getValue(topic.id).progress
                when {
                    progress.status == ProgressStatus.NEW -> newCount++
                    progress.isEligibleOn(today) -> toReview++
                    progress.lastGraduatedOn == today -> doneToday++
                }
            }
            CourseStats(course, newCount, toReview, doneToday)
        }
    }

    /** A random exercise from a topic eligible today, or null when the course is done for today. */
    fun nextExercise(courseId: Uuid, currentTopicId: Uuid? = null): NextExercise? {
        val course = courses.get(courseId) ?: return null
        val states = replay(listOf(course), journal.all(), rules)
        val today = today()
        val eligible = course.studyableTopics().filter { states.getValue(it.id).progress.isEligibleOn(today) }
        if (eligible.isEmpty()) return null

        val topicId = picker.pick(eligible.map { it.id }, currentTopicId, random)
        val topic = eligible.first { it.id == topicId }
        return NextExercise(topic, topic.exercises.random(random))
    }

    /** Records an answer in the journal. Graduation and scheduling follow from it on the next replay. */
    fun submitAnswer(
        courseId: Uuid,
        topicId: Uuid,
        exerciseId: Uuid,
        input: String,
        autoCheck: AutoCheck?,
        rating: Rating?,
    ) {
        journal.append(
            EventEnvelope(
                eventId = Uuid.random(),
                deviceId = deviceId,
                occurredAt = clock.now(),
                event = AnswerSubmitted(courseId, topicId, exerciseId, input, autoCheck, rating),
            ),
        )
    }

    fun topicStates(courseId: Uuid): Map<Uuid, TopicState> {
        val course = courses.get(courseId) ?: return emptyMap()
        return replay(listOf(course), journal.all(), rules)
    }

    private fun today(): LocalDate = clock.now().localDate

    private fun Course.studyableTopics() = topics.filter { it.exercises.isNotEmpty() }
}
