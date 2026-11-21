package com.polylingy.core.study

import com.polylingy.content.ItemResult
import com.polylingy.core.FixedClock
import com.polylingy.core.InMemoryCourseRepository
import com.polylingy.core.InMemoryCourseSettings
import com.polylingy.core.InMemoryEventLog
import com.polylingy.core.Moscow
import com.polylingy.core.answer
import com.polylingy.core.at
import com.polylingy.core.content.Course
import com.polylingy.core.content.GradingSpec
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.content.Preset
import com.polylingy.core.content.RulesSpec
import com.polylingy.core.content.Topic
import com.polylingy.core.course
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.topic
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** The numbers of the statistics screen come from the journal and the course's own policies; see docs/learning-model.md. */
class StatisticsTest {
    private val today = LocalDate(2026, 11, 19)
    private val clock = FixedClock(at(today))
    private val journal = InMemoryEventLog()
    private val settings = InMemoryCourseSettings()
    private val a = topic("a")
    private val b = topic("b")
    private val c = course(a, b)
    private val courses = InMemoryCourseRepository(c)
    private val study = StudyService(courses, settings, journal, clock, Uuid.NIL)
    private val stats = StatisticsService(courses, settings, journal, clock, study)

    private fun daysAgo(n: Int) = today.minus(n, DateTimeUnit.DAY)

    private fun right(topic: Topic, on: LocalDate, hour: Int = 12, zone: TimeZone = Moscow, course: Course = c) =
        journal.append(answer(course, topic, true, at(on, hour, zone)))

    private fun wrong(topic: Topic, on: LocalDate) = journal.append(answer(c, topic, false, at(on)))

    private fun typo(topic: Topic, on: LocalDate) = journal.append(answer(c, topic, listOf(ItemResult.TYPO), at(on)))

    /** Three right answers in a row: the default ladder masters a topic after that (learning: 3 in a row) and reviews it next day. */
    private fun master(topic: Topic, on: LocalDate) = repeat(3) { right(topic, on) }

    @Test fun anEmptyJournalIsAllZerosAndAWeekOfEmptyForecast() {
        val s = stats.statistics()
        assertEquals(emptyMap(), s.days)
        assertEquals(0, s.streak)
        assertEquals(0, s.bestStreak)
        assertEquals(0, s.totalAnswers)
        assertNull(s.passedShare)
        assertEquals(0, s.learned)
        assertEquals(2, s.topicsTotal)
        assertEquals(7, s.forecast.size)
        assertEquals(today, s.forecast.first().date)
    }

    @Test fun answersAreCountedPerDayInTheZoneTheyWereGivenIn() {
        right(a, daysAgo(1), hour = 23)
        right(a, daysAgo(1), hour = 9)
        // 23:30 in Los Angeles is already the next day in Moscow, but it was yesterday where it happened
        journal.append(answer(c, a, true, ZonedInstant(at(daysAgo(1), 23, TimeZone.of("America/Los_Angeles")).instant, TimeZone.of("America/Los_Angeles"))))
        val s = stats.statistics()
        assertEquals(3, s.days.getValue(daysAgo(1)).answers)
        assertEquals(3, s.totalAnswers)
        assertEquals(1, s.daysActive)
    }

    @Test fun passingFollowsTheCoursesGradingAndTyposAreShownApart() {
        right(a, today)
        typo(a, today)
        wrong(a, today)
        val forgiving = stats.statistics().days.getValue(today)
        assertEquals(DayActivity(answers = 3, passed = 2, withTypos = 1, newTopics = 1), forgiving)
        settings.save(c.id, RulesSpec(grading = GradingSpec.AllCorrect(typos = false)), clock.now().instant)
        val strict = stats.statistics().days.getValue(today)
        assertEquals(DayActivity(answers = 3, passed = 1, withTypos = 1, newTopics = 1), strict, "the same journal, another grading rule")
        assertEquals(2, strict.failed)
    }

    @Test fun theStreakIsNotBrokenBeforeTheDayIsOver() {
        for (n in 1..3) right(a, daysAgo(n))
        assertEquals(3, stats.statistics().streak, "nothing yet today: counted from yesterday")
        right(a, today)
        assertEquals(4, stats.statistics().streak)
    }

    @Test fun aMissedDayBreaksTheStreakAndTheBestOneIsKept() {
        for (n in listOf(0, 1, 2, 5, 6, 7, 8, 9)) right(a, daysAgo(n))
        val s = stats.statistics()
        assertEquals(3, s.streak)
        assertEquals(5, s.bestStreak)
        assertEquals(8, s.daysActive)
    }

    @Test fun learnedNowAndAWeekAgoComeFromReplayOfTheJournalUpToThatDay() {
        master(a, daysAgo(10))
        master(b, daysAgo(2))
        val s = stats.statistics()
        assertEquals(2, s.learned)
        assertEquals(1, s.learnedWeekAgo, "b was mastered only two days ago")
    }

    @Test fun theForecastUsesTheSchedulersDatesAndTodayHoldsWhatIsDue() {
        master(a, daysAgo(1))   // the ladder's first step is 1 day: due today
        master(b, today)        // due tomorrow
        val f = stats.statistics().forecast
        assertEquals(1, f[0].topics, "a is due today")
        assertEquals(1, f[1].topics, "b is due tomorrow")
        assertEquals(0, f[2].topics)
    }

    @Test fun theDailyGoalIsTheCoursesLimitOfNewTopics() {
        right(a, today)
        right(a, today)
        right(b, daysAgo(1))
        assertEquals(Preset.DEFAULT_NEW_PER_DAY, stats.statistics().work.newPerDay, "a course that says nothing has the default limit, which is the goal")
        settings.save(c.id, RulesSpec(picker = PickerSpec.Conveyor(newPerDay = 4)), clock.now().instant)
        val work = stats.statistics().work
        assertEquals(4, work.newPerDay)
        assertEquals(1, work.newBegun, "a was begun today; b yesterday")
        assertEquals(2, work.answers)
    }

    @Test fun eachDayKnowsHowManyTopicsWereBegunOnIt() {
        right(a, daysAgo(2))
        right(b, daysAgo(2))
        right(a, daysAgo(1))     // a was begun before: not new
        right(b, daysAgo(1))
        val days = stats.statistics().days
        assertEquals(2, days.getValue(daysAgo(2)).newTopics)
        assertEquals(0, days.getValue(daysAgo(1)).newTopics)
    }

    @Test fun onlyExistingCoursesCountAndOneCourseCanBeAskedFor() {
        right(a, today)
        val other = course(topic("x"))
        journal.append(answer(other, other.topics.first(), true, at(today)))     // a course that is not in the app
        assertEquals(1, stats.statistics().totalAnswers)
        val second = course(topic("y"))
        courses.save(second)
        journal.append(answer(second, second.topics.first(), true, at(today)))
        assertEquals(2, stats.statistics().totalAnswers)
        assertEquals(1, stats.statistics(c.id).totalAnswers)
    }
}
