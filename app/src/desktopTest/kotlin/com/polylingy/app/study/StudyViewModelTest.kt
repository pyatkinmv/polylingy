package com.polylingy.app.study

import com.polylingy.app.MovableClock
import com.polylingy.app.testContainer
import com.polylingy.client.CourseFile
import com.polylingy.core.journal.AutoCheck
import kotlinx.datetime.LocalDateTime
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StudyViewModelTest {
    private val clock = MovableClock()
    private val container = testContainer(clock)
    private val course = container.courseService.create("Spanish").let { c ->
        container.courseService.addTopic(c.id, CourseFile.parseTopic(
            """{"subject": "Colors", "exercises": [{"task": "red: {0} / {1}", "answer": {"0": "rojo", "1": "roja"}}]}""",
        ))
    }

    private fun newVm() = StudyViewModel(course.id, container.studyService, container.courseService, container.storage.courses, Random(1))

    @AfterTest fun close() = container.storage.close()

    private fun StudyViewModel.answer(input: String, correct: Boolean) {
        onInput(input)
        submit()
        rate(correct)
    }

    @Test fun startsWithAnExerciseAndTheCourseName() {
        val vm = newVm()
        assertEquals("Spanish", vm.courseName)
        assertEquals("Colors", assertIs<StudyState.Answering>(vm.state.value).topic.subject)
    }

    @Test fun submitShowsTheCheckedAnswer() {
        val vm = newVm()
        vm.onInput("Rojo, rosa")
        vm.submit()
        val reviewing = assertIs<StudyState.Reviewing>(vm.state.value)
        assertEquals(AutoCheck.PARTIAL, reviewing.check.result)
        assertTrue(reviewing.phrase in listOf("Partially right.", "Some correct!", "Half right!", "Almost!", "Close!"))
    }

    @Test fun threeCorrectRatingsFinishTheDay() {
        val vm = newVm()
        repeat(2) { vm.answer("rojo, roja", correct = true) }
        assertIs<StudyState.Answering>(vm.state.value)
        vm.answer("rojo, roja", correct = true)
        assertEquals(StudyState.Done, vm.state.value)
    }

    @Test fun streakSurvivesLeavingTheScreen() {
        repeat(2) { newVm().answer("rojo, roja", correct = true) }
        val vm = newVm()
        vm.answer("rojo, roja", correct = true)
        assertEquals(StudyState.Done, vm.state.value)
    }

    @Test fun selfRatingWinsOverTheAutomaticCheck() {
        val vm = newVm()
        repeat(3) { vm.answer("wrong", correct = true) }
        assertEquals(StudyState.Done, vm.state.value)
    }

    @Test fun topicComesBackOnItsReviewDate() {
        val vm = newVm()
        repeat(3) { vm.answer("rojo, roja", correct = true) }
        clock.now = LocalDateTime(2026, 1, 18, 9, 0)
        assertIs<StudyState.Answering>(newVm().state.value)
    }

    @Test fun removingTheOnlyTopicFinishes() {
        val vm = newVm()
        vm.removeCurrentTopic()
        assertEquals(StudyState.Done, vm.state.value)
        assertEquals(emptyList(), container.storage.courses.get(course.id)!!.topics)
    }

    @Test fun inputIsIgnoredWhileReviewing() {
        val vm = newVm()
        vm.onInput("rojo, roja")
        vm.submit()
        vm.onInput("changed")
        assertEquals("rojo, roja", assertIs<StudyState.Reviewing>(vm.state.value).input)
    }
}
