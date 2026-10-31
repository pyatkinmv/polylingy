package com.polylingy.app

import com.polylingy.client.LocalStorage
import com.polylingy.core.content.CourseService
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.ports.Clock
import com.polylingy.core.study.StudyService
import kotlinx.datetime.TimeZone
import kotlin.random.Random

/** Opening and saving course files; each platform shows its own file dialogs. */
interface CourseFiles {
    /** Lets the user pick a course file and returns its contents, or null if cancelled. */
    fun open(): String?

    /** Lets the user choose where to save [content]; returns false if cancelled. */
    fun save(suggestedName: String, content: String): Boolean
}

val SystemClock = Clock { ZonedInstant(kotlin.time.Clock.System.now(), TimeZone.currentSystemDefault()) }

/** Builds every long-lived object once; screens get what they need from here. */
class AppContainer(
    val storage: LocalStorage,
    val files: CourseFiles,
    val clock: Clock = SystemClock,
    /** Picks topics, exercises and result phrases; fixed in tests for repeatable runs. */
    val random: Random = Random.Default,
) {
    val courseService = CourseService(storage.courses, clock)
    val studyService = StudyService(storage.courses, storage.journal, clock, storage.deviceId, random = random)
}
