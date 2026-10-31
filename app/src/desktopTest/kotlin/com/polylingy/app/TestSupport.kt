package com.polylingy.app

import com.polylingy.client.DesktopStorage
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.ports.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

val TestZone = TimeZone.of("Europe/Moscow")

class MovableClock(var now: LocalDateTime = LocalDateTime(2026, 1, 15, 12, 0)) : Clock {
    override fun now() = ZonedInstant(now.toInstant(TestZone), TestZone)
}

class NoFiles : CourseFiles {
    override fun open(): String? = null
    override fun save(suggestedName: String, content: String) = false
}

fun testContainer(clock: Clock = MovableClock()) = AppContainer(DesktopStorage.inMemory(), NoFiles(), clock)
