package com.polylingy.client

import com.polylingy.content.Exercise
import com.polylingy.core.content.CourseFormatException
import com.polylingy.core.content.CourseService
import com.polylingy.core.content.GradingSpec
import com.polylingy.core.content.PickerSpec
import com.polylingy.core.content.images
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.ports.Clock
import com.sun.net.httpserver.HttpServer
import kotlinx.datetime.TimeZone
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CourseLibraryTest {
    private val storage = DesktopStorage.inMemory()
    private val imageDir = Files.createTempDirectory("polylingy-images")
    private val clock = Clock { ZonedInstant(Instant.parse("2026-11-01T10:00:00Z"), TimeZone.UTC) }
    private val downloads = mutableMapOf<String, ByteArray>()
    private val library = CourseLibrary(
        storage.courses,
        CourseService(storage.courses, storage.courseSettings, clock),
        FileImageStore(imageDir),
        { url -> downloads[url] ?: error("404") },
        CourseArchive,
        clock,
    )

    @AfterTest fun cleanUp() {
        storage.close()
        imageDir.toFile().deleteRecursively()
    }

    private val png = byteArrayOf(1, 2, 3)

    private fun course(picture: String, topics: String = "") = """
        { "formatVersion": 1, "name": "Pictures",
          "topics": [ { "title": "T", "exercises": [ { "type": "pictures",
            "items": [ { "image": { "src": "$picture", "alt": "A cat" }, "text": "It {{’s sleeping}}." } ] } ] } $topics ] }
    """

    private fun problems(source: CourseSource) = assertFailsWith<CourseFormatException> { library.import(source) }.problems.map { it.toString() }

    /** The example courses in `examples/` are what a person tries first: they must always import cleanly. */
    @Test fun theExampleCoursesImportWithoutWarnings() {
        val examples = java.io.File("../examples")
        for (file in listOf("english-grammar.zip", "irregular-verbs.json")) {
            val (course, warnings) = library.import(file, examples.resolve(file).readBytes())
            assertEquals(emptyList(), warnings, file)
            assertTrue(course.allTopics.size >= 8, "$file has a real number of topics")
        }
        val grammar = storage.courses.all().first { it.name.startsWith("English Grammar") }
        assertEquals(4, grammar.units.size)
        assertTrue(grammar.images().all { library.image(grammar.id, it.second.src) != null }, "every picture is stored")
    }

    @Test fun aZipWithItsPicturesIsImportedAndThePicturesStored() {
        val zip = CourseArchive.zip(course("images/cat.png"), mapOf("images/cat.png" to png, "images/unused.png" to png))
        val (course, warnings) = library.import("cat.zip", zip)
        assertEquals(emptyList(), warnings)
        assertContentEquals(png, library.image(course.id, "images/cat.png"))
        assertNull(library.image(course.id, "images/unused.png"))
        assertEquals(course, storage.courses.get(course.id))
    }

    @Test fun aPictureMissingFromTheArchiveIsAnError() {
        val zip = CourseArchive.zip(course("images/cat.png"), mapOf("images/dog.png" to png))
        assertEquals(listOf("topics[0].exercises[0].items[0].image.src: the archive has no file \"images/cat.png\""), problems(CourseArchive.read("cat.zip", zip)))
        assertEquals(emptyList(), storage.courses.all())
    }

    @Test fun aRelativePictureInAPlainJsonFileIsAnError() = assertEquals(
        listOf("topics[0].exercises[0].items[0].image.src: \"images/cat.png\" needs a zip archive with that file, or an https:// URL"),
        problems(CourseSource("cat.json", course("images/cat.png"))),
    )

    @Test fun pictureUrlsAreDownloaded() {
        downloads["https://img.example/cat.png"] = png
        val (course, warnings) = library.import(CourseSource("cat.json", course("https://img.example/cat.png")))
        assertEquals(emptyList(), warnings)
        assertContentEquals(png, library.image(course.id, "https://img.example/cat.png"))
    }

    @Test fun aFailedDownloadIsAWarningNotAnError() {
        val (course, warnings) = library.import(CourseSource("cat.json", course("https://img.example/missing.png")))
        assertEquals(
            listOf("topics[0].exercises[0].items[0].image.src: couldn't download the picture (404); its description is shown instead"),
            warnings.map { it.toString() },
        )
        assertNull(library.image(course.id, "https://img.example/missing.png"))
        assertEquals(course, storage.courses.get(course.id))
    }

    @Test fun anUnreadableFileIsReportedLikeAnyOtherProblem() =
        assertEquals("$: the archive has no course.json at its root",
            assertFailsWith<CourseFormatException> { library.import("x.zip", CourseArchive.zip("{}", emptyMap()).let { stripCourseJson(it) }) }.problems.single().toString())

    private fun stripCourseJson(zip: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { it.putNextEntry(java.util.zip.ZipEntry("images/a.png")); it.write(png) }
        return out.toByteArray()
    }

    @Test fun formatErrorsAreReportedAndNothingIsSaved() {
        assertEquals(listOf("formatVersion: missing; this app writes 2"), problems(CourseSource("x.json", """{ "name": "X" }""")))
        assertEquals(emptyList(), storage.courses.all())
    }

    @Test fun aCourseWithoutOwnPicturesIsExportedAsJson() {
        downloads["https://img.example/cat.png"] = png
        val course = library.import(CourseSource("cat.json", course("https://img.example/cat.png"))).course
        val export = library.export(course.id)
        assertEquals("pictures.json", export.fileName)
        val again = library.import(export.fileName, export.bytes).course
        assertEquals("Pictures (2)", again.name)
        assertEquals(course.allTopics.map { it.title }, again.allTopics.map { it.title })
    }

    @Test fun settingsStartFromTheFileGoOutWithTheExportAndLeaveWithTheCourse() {
        val text = """{ "formatVersion": 1, "name": "Old", "settings": { "allowTypos": false, "newPerDay": 5 },
            "topics": [ { "title": "T", "exercises": [ { "type": "gaps", "items": [ { "text": "{{a}}" } ] } ] } ] }"""
        val course = library.import(CourseSource("old.json", text)).course
        val settings = storage.courseSettings.get(course.id)!!
        assertEquals(GradingSpec.AllCorrect(typos = false), settings.grading)
        assertEquals(PickerSpec.Conveyor(newPerDay = 5), settings.picker)

        storage.courseSettings.save(course.id, settings.copy(picker = PickerSpec.Alternate(newPerDay = 7)), Instant.parse("2026-11-02T10:00:00Z"))
        val exported = library.export(course.id).bytes.decodeToString()
        assertTrue("\"formatVersion\": 2" in exported, exported)
        assertTrue("\"alternate\"" in exported && "\"newPerDay\": 7" in exported, "the file carries the current settings")
        assertTrue("\"settings\"" !in exported)
        val again = library.import("old.json", exported.encodeToByteArray()).course
        assertEquals(PickerSpec.Alternate(newPerDay = 7), storage.courseSettings.get(again.id)!!.picker)

        library.delete(course.id)
        assertNull(storage.courseSettings.get(course.id))
    }

    @Test fun aCourseWithOwnPicturesIsExportedAsAZipThatImportsAgain() {
        val course = library.import("cat.zip", CourseArchive.zip(course("images/cat.png"), mapOf("images/cat.png" to png))).course
        val export = library.export(course.id)
        assertEquals("pictures.zip", export.fileName)
        val again = library.import(export.fileName, export.bytes).course
        assertContentEquals(png, library.image(again.id, "images/cat.png"))
        assertIs<Exercise.Pictures>(again.topics[0].exercises[0])
    }

    @Test fun aStudyOnlyCourseCantBeExported() {
        val newer = """{ "formatVersion": 3, "name": "Future", "topics": [ { "title": "T", "exercises": [ { "type": "dictation" } ] } ] }"""
        val (course, warnings) = library.import(CourseSource("future.json", newer))
        assertEquals(listOf("topics[0].exercises[0]: unknown exercise type \"dictation\""), warnings.map { it.toString() })
        assertFailsWith<IllegalArgumentException> { library.export(course.id) }
    }

    @Test fun exportNeedsTheCourseAndItsPictures() {
        assertFailsWith<IllegalArgumentException> { library.export(kotlin.uuid.Uuid.random()) }
        val course = library.import("cat.zip", CourseArchive.zip(course("images/cat.png"), mapOf("images/cat.png" to png))).course
        imageDir.resolve(course.id.toString()).toFile().deleteRecursively()
        assertEquals(
            "Picture images/cat.png of course ${course.id} is missing",
            assertFailsWith<IllegalArgumentException> { library.export(course.id) }.message,
        )
    }

    @Test fun deleteRemovesTheCourseAndItsPictures() {
        val course = library.import("cat.zip", CourseArchive.zip(course("images/cat.png"), mapOf("images/cat.png" to png))).course
        library.delete(course.id)
        assertNull(storage.courses.get(course.id))
        assertNull(library.image(course.id, "images/cat.png"))
        assertTrue(!imageDir.resolve(course.id.toString()).toFile().exists())
    }

    @Test fun aFailureWhileStoringPicturesUndoesTheImport() {
        Files.writeString(imageDir.resolve("blocker"), "")
        val blocked = CourseLibrary(storage.courses, CourseService(storage.courses, storage.courseSettings, clock), FileImageStore(imageDir.resolve("blocker")), { png }, CourseArchive, clock)
        val zip = CourseArchive.zip(course("images/cat.png"), mapOf("images/cat.png" to png))
        assertFailsWith<Exception> { blocked.import("cat.zip", zip) }
        assertEquals(emptyList(), storage.courses.all())
    }

    @Test fun theSampleCourseIsValidAndAddedOnlyOnce() {
        library.seedSample(sampleCourse())
        library.seedSample(sampleCourse())
        val course = storage.courses.all().single()
        assertEquals("English Grammar Sampler", course.name)
        assertEquals(
            setOf("table", "gaps", "pictures", "match", "order", "choice"),
            course.allTopics.flatMap { it.exercises }.map { it::class.simpleName!!.lowercase() }.toSet(),
        )
        for (src in listOf("images/cycling.png", "images/cooking.png", "images/swimming.png")) {
            assertTrue(library.image(course.id, src)!!.isNotEmpty(), src)
        }
    }

    @Test fun httpFetcherDownloadsAndRejectsErrors() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/cat.png") { it.sendResponseHeaders(200, 3); it.responseBody.use { body -> body.write(png) } }
        server.createContext("/big.png") { it.sendResponseHeaders(200, 20); it.responseBody.use { body -> body.write(ByteArray(20)) } }
        server.createContext("/missing.png") { it.sendResponseHeaders(404, -1); it.close() }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val fetcher = HttpImageFetcher(maxBytes = 10)
            assertContentEquals(png, fetcher.fetch("$base/cat.png"))
            assertEquals("HTTP 404", assertFailsWith<IllegalStateException> { fetcher.fetch("$base/missing.png") }.message)
            assertEquals("larger than 0 MB", assertFailsWith<IllegalStateException> { fetcher.fetch("$base/big.png") }.message)
        } finally {
            server.stop(0)
        }
    }
}
