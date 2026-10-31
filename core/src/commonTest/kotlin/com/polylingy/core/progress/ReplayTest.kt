package com.polylingy.core.progress

import com.polylingy.core.answer
import com.polylingy.core.at
import com.polylingy.core.course
import com.polylingy.core.topic
import com.polylingy.core.journal.ZonedInstant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplayTest {
    private val day1 = LocalDate(2026, 1, 15)
    private val a = topic("A")
    private val b = topic("B")
    private val c = course(a, b)

    private var hour = 8
    private fun ans(topic: com.polylingy.core.content.Topic, correct: Boolean, date: LocalDate = day1) =
        answer(c, topic, correct, at(date, hour++))

    private fun replayOf(vararg events: com.polylingy.core.journal.EventEnvelope) = replay(listOf(c), events.toList())

    @Test fun everyTopicStartsNew() {
        val states = replayOf()
        assertEquals(ProgressStatus.NEW, states.getValue(a.id).progress.status)
        assertEquals(ProgressStatus.NEW, states.getValue(b.id).progress.status)
    }

    // ── topics are tracked independently (ported from the Flutter session tests) ──

    @Test fun completingADoesNotAffectBStreak() {
        val states = replayOf(ans(a, true), ans(b, true), ans(a, true), ans(a, true), ans(b, true))
        assertEquals(ProgressStatus.TO_REVIEW, states.getValue(a.id).progress.status)
        assertEquals(2, states.getValue(b.id).streak.consecutiveCorrect)
    }

    @Test fun incorrectOnADoesNotResetBStreak() {
        val states = replayOf(ans(b, true), ans(b, true), ans(a, false), ans(b, true))
        assertEquals(ProgressStatus.TO_REVIEW, states.getValue(b.id).progress.status)
        assertEquals(1, states.getValue(a.id).streak.mistakes)
    }

    @Test fun twoTopicsCompleteAtDifferentTimes() {
        val states = replayOf(
            ans(a, true), ans(a, true), ans(a, true),
            ans(b, false), ans(b, true), ans(b, true), ans(b, true),
        )
        assertEquals(3, states.getValue(a.id).progress.intervalDays)   // 0 mistakes: EF 2.6
        assertEquals(3, states.getValue(b.id).progress.intervalDays)   // 1 mistake: EF 2.5, round(2.5) = 3
        assertEquals(2.5, states.getValue(b.id).progress.easeFactor, 0.001)
    }

    // ── streak lives until graduation ───────────────────────────────────────

    @Test fun streakSurvivesDayChanges() {
        val day2 = LocalDate(2026, 1, 16)
        val states = replayOf(ans(a, true, day1), ans(a, true, day1), ans(a, true, day2))
        val progress = states.getValue(a.id).progress
        assertEquals(day2, progress.lastGraduatedOn)
        assertEquals(LocalDate(2026, 1, 19), progress.nextReviewDate)
    }

    @Test fun secondGraduationContinuesFromPreviousSchedule() {
        val review = LocalDate(2026, 1, 18)
        val states = replayOf(
            ans(a, true), ans(a, true), ans(a, true),
            ans(a, true, review), ans(a, true, review), ans(a, true, review),
        )
        val progress = states.getValue(a.id).progress
        assertEquals(8, progress.intervalDays)                          // round(3 × 2.7)
        assertEquals(LocalDate(2026, 1, 26), progress.nextReviewDate)
    }

    @Test fun eventsAreReplayedInTimeOrder() {
        val first = ans(a, false)
        val rest = listOf(ans(a, true), ans(a, true), ans(a, true))
        val states = replay(listOf(c), rest.reversed() + first)
        assertEquals(2.5, states.getValue(a.id).progress.easeFactor, 0.001)   // the mistake still counts once
    }

    @Test fun eventsAtTheSameInstantKeepJournalOrder() {
        // A wrong answer followed by three right ones, all stamped with the same moment:
        // the order must come from the journal, never from random event ids.
        repeat(50) {
            val now = at(day1)
            val events = listOf(false, true, true, true).map { answer(c, a, it, now) }
            val progress = replay(listOf(c), events).getValue(a.id).progress
            assertEquals(ProgressStatus.TO_REVIEW, progress.status)
            assertEquals(2.5, progress.easeFactor, 0.001)   // graduated with exactly one mistake
        }
    }

    // ── which day an answer belongs to ──────────────────────────────────────

    @Test fun dayIsTakenFromTheEventsOwnZone() {
        val moscow = TimeZone.of("Europe/Moscow")
        // 00:30 in Moscow on Jan 16 is still Jan 15 in UTC.
        val lateNight = ZonedInstant(LocalDateTime(2026, 1, 16, 0, 30).toInstant(moscow), moscow)
        val states = replayOf(ans(a, true), ans(a, true), answer(c, a, true, lateNight))
        assertEquals(LocalDate(2026, 1, 16), states.getValue(a.id).progress.lastGraduatedOn)
    }

    // ── deleted content ─────────────────────────────────────────────────────

    @Test fun eventsForDeletedTopicsAreIgnored() {
        val withoutA = c.copy(topics = listOf(b))
        val states = replay(listOf(withoutA), listOf(ans(a, true), ans(a, true), ans(a, true)))
        assertEquals(setOf(b.id), states.keys)
    }

    @Test fun eventsForATopicUnderAnotherCourseAreIgnored() {
        val other = course(a)
        val states = replay(listOf(c), List(3) { answer(other, a, true, at(day1, 8 + it)) })
        assertEquals(ProgressStatus.NEW, states.getValue(a.id).progress.status)
    }
}
