package com.polylingy.app

import com.polylingy.client.CourseArchive
import com.polylingy.client.DesktopStorage
import com.polylingy.client.ImageStore
import com.polylingy.client.LocalStorage
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.ports.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.uuid.Uuid

val TestZone = TimeZone.of("Europe/Moscow")

class MovableClock(var now: LocalDateTime = LocalDateTime(2026, 1, 15, 12, 0)) : Clock {
    override fun now() = ZonedInstant(now.toInstant(TestZone), TestZone)
}

class NoFiles : CourseFiles {
    override fun open(): PickedFile? = null
    override fun save(suggestedName: String, bytes: ByteArray) = false
}

class MemoryImageStore : ImageStore {
    private val images = mutableMapOf<Pair<Uuid, String>, ByteArray>()
    override fun put(courseId: Uuid, src: String, bytes: ByteArray) { images[courseId to src] = bytes }
    override fun get(courseId: Uuid, src: String) = images[courseId to src]
    override fun deleteCourse(courseId: Uuid) { images.keys.removeAll { it.first == courseId } }
}

/** Downloads always fail: tests never touch the network. */
fun testContainer(
    clock: Clock = MovableClock(),
    files: CourseFiles = NoFiles(),
    storage: LocalStorage = DesktopStorage.inMemory(),
) = AppContainer(storage, files, MemoryImageStore(), { error("offline") }, CourseArchive, SkiaImageDecoder, clock, Random(7))
