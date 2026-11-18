package com.polylingy.client

import com.polylingy.content.Image
import com.polylingy.content.Problem
import com.polylingy.core.content.Course
import com.polylingy.core.content.CourseFormatException
import com.polylingy.core.content.CourseJson
import com.polylingy.core.content.CourseService
import com.polylingy.core.content.media
import com.polylingy.core.ports.Clock
import com.polylingy.core.ports.CourseRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/** An imported course and what to tell the user about it: unknown content of a newer format, failed downloads. */
data class ImportResult(val course: Course, val warnings: List<Problem>)

/** Pictures of courses, kept locally. */
interface ImageStore {
    fun put(courseId: Uuid, src: String, bytes: ByteArray)

    /** The stored bytes of a picture, or null if it isn't stored (e.g. its download failed). */
    fun get(courseId: Uuid, src: String): ByteArray?
    fun deleteCourse(courseId: Uuid)
}

/** Downloads a picture; throws if it can't. */
fun interface ImageFetcher {
    fun fetch(url: String): ByteArray
}

/** The course file kinds: `course.json` alone, or a zip with `course.json` and `images/`. */
interface CourseArchives {
    fun read(fileName: String, bytes: ByteArray): CourseSource
    fun zip(json: String, files: Map<String, ByteArray>): ByteArray
}

/** A course file as picked by the user: its JSON and, from a zip, the files next to it (`images/…`). */
class CourseSource(val fileName: String, val json: String, val files: Map<String, ByteArray> = emptyMap())

/**
 * Reads and checks a course file exactly as import does, without saving anything: the format, the content, and that
 * every picture of the archive is in it. Throws [CourseFormatException] listing every problem; on success the course
 * and its warnings (unknown content of a newer format).
 */
fun CourseSource.check(now: kotlin.time.Instant): ImportResult {
    val read = CourseJson.read(json, now)
    val missing = read.course.media().filter { (_, src) -> !src.isUrl && src !in files }.map { (at, src) ->
        Problem(at, if (files.isEmpty()) "\"$src\" needs a zip archive with that file, or an https:// URL" else "the archive has no file \"$src\"")
    }
    if (missing.isNotEmpty()) throw CourseFormatException(missing)
    return ImportResult(read.course, read.warnings)
}

/** A course written for the user to save: a `.json`, or a `.zip` when it has pictures or sounds of its own. */
class CourseExport(val fileName: String, val bytes: ByteArray)

/** Courses as files: import (JSON or zip, with their pictures and sounds), export, and removal with their files. */
class CourseLibrary(
    private val courses: CourseRepository,
    private val service: CourseService,
    private val images: ImageStore,
    private val fetcher: ImageFetcher,
    private val archives: CourseArchives,
    private val clock: Clock,
) {
    /** Reads a picked file — JSON or zip — and imports it, see [import]. */
    fun import(fileName: String, bytes: ByteArray): ImportResult = try {
        import(archives.read(fileName, bytes))
    } catch (e: CourseFormatException) {
        log.info { "Couldn't read $fileName: ${e.problems.joinToString("; ")}" }
        throw e
    }

    /**
     * Checks the whole file — format, content, and that every picture of the archive is in it — and imports it.
     * Throws [CourseFormatException] listing every problem; nothing is saved then.
     */
    fun import(source: CourseSource): ImportResult {
        log.info { "Importing ${source.fileName} (${source.files.size} files besides course.json)" }
        try {
            val read = source.check(clock.now().instant)
            val media = read.course.media()

            val course = service.import(read.course)
            val warnings = read.warnings.toMutableList()
            try {
                for ((at, src) in media.distinctBy { it.second }) {
                    if (src.isUrl) download(course.id, src)?.let { warnings += Problem(at, it) }
                    else images.put(course.id, src, source.files.getValue(src))
                }
            } catch (e: Exception) {
                service.delete(course.id)
                images.deleteCourse(course.id)
                throw e
            }
            log.info { "Imported ${source.fileName} as course ${course.id} \"${course.name}\": ${course.units.size} units, ${course.allTopics.size} topics, ${media.size} pictures and sounds, ${warnings.size} warnings" }
            warnings.forEach { log.warn { "Import of ${source.fileName}: $it" } }
            return ImportResult(course, warnings)
        } catch (e: CourseFormatException) {
            log.info { "Couldn't import ${source.fileName}: ${e.problems.size} problems" }
            e.problems.forEach { log.info { "  $it" } }
            throw e
        }
    }

    /** Reads a picked file — JSON or zip — and updates the course [courseId] with it, see [update]. */
    fun update(courseId: Uuid, fileName: String, bytes: ByteArray): ImportResult = try {
        update(courseId, archives.read(fileName, bytes))
    } catch (e: CourseFormatException) {
        log.info { "Couldn't read $fileName: ${e.problems.joinToString("; ")}" }
        throw e
    }

    /**
     * Checks the whole file like [import], then replaces the content of the course with the same id by it, keeping its progress
     * ([CourseService.update]): [CourseFormatException] for a bad file, [CourseUpdateException] when the file is another course
     * or not newer. Pictures and sounds of the file are added; nothing is changed when something fails before the update itself.
     */
    fun update(courseId: Uuid, source: CourseSource): ImportResult {
        log.info { "Updating course $courseId from ${source.fileName} (${source.files.size} files besides course.json)" }
        val read = source.check(clock.now().instant)
        service.checkUpdate(courseId, read.course)
        val media = read.course.media()
        val warnings = read.warnings.toMutableList()
        for ((at, src) in media.distinctBy { it.second }) {
            if (src.isUrl) download(courseId, src)?.let { warnings += Problem(at, it) }
            else images.put(courseId, src, source.files.getValue(src))
        }
        val course = service.update(courseId, read.course)
        warnings.forEach { log.warn { "Update from ${source.fileName}: $it" } }
        return ImportResult(course, warnings)
    }

    /** The course as a file: a zip when it has pictures or sounds of its own, otherwise plain JSON. A study-only course can't be exported. */
    fun export(courseId: Uuid): CourseExport {
        val course = requireNotNull(courses.get(courseId)) { "No course $courseId" }
        require(!course.studyOnly) { "This course was made with a newer version of Polylingy; update the app to export it" }
        val json = CourseJson.write(service.forExport(course))
        val own = course.media().map { it.second }.filterNot { it.isUrl }.distinct()
        val export = if (own.isEmpty()) {
            CourseExport("${slug(course.name)}.json", json.toByteArray())
        } else {
            val files = own.associateWith { src -> requireNotNull(images.get(course.id, src)) { "Picture $src of course $courseId is missing" } }
            CourseExport("${slug(course.name)}.zip", archives.zip(json, files))
        }
        log.info { "Exported course $courseId as ${export.fileName} (${export.bytes.size} bytes)" }
        return export
    }

    fun delete(courseId: Uuid) {
        service.delete(courseId)
        images.deleteCourse(courseId)
    }

    /** The bytes of a course picture, for display; null if it isn't stored. */
    fun image(courseId: Uuid, src: String): ByteArray? = images.get(courseId, src)

    /** Returns an error to show instead of the picture, or null when it was stored. */
    private fun download(courseId: Uuid, url: String): String? = try {
        images.put(courseId, url, fetcher.fetch(url))
        log.info { "Downloaded $url for course $courseId" }
        null
    } catch (e: Exception) {
        log.warn(e) { "Couldn't download $url for course $courseId" }
        val why = e.message ?: e::class.simpleName
        if (url.isSound) "couldn't download the sound ($why); it stays silent" else "couldn't download the picture ($why); its description is shown instead"
    }

    private fun slug(name: String) = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "_").trim('_').ifEmpty { "course" }

    /** Imports the bundled sample course on first launch, when there are no courses at all. */
    fun seedSample(sample: CourseSource) {
        if (courses.all().isNotEmpty()) return
        import(sample)
        log.info { "Added the sample course" }
    }
}

private val String.isUrl get() = startsWith("https://")
private val String.isSound get() = substringAfterLast('.').lowercase() in setOf("mp3", "wav")
