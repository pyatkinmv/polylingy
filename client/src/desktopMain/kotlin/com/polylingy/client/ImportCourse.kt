package com.polylingy.client

import com.polylingy.core.content.CourseFormatException
import com.polylingy.core.content.CourseService
import com.polylingy.core.journal.ZonedInstant
import com.polylingy.core.ports.Clock
import kotlinx.datetime.TimeZone
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess
import kotlin.uuid.Uuid

/**
 * `./gradlew :client:importCourse -Pcourse=path/to/course.zip [-Pdelete=id1,id2]`: imports a course file into the real
 * database (`<Documents>/polylingy`) exactly as the Import button does; the courses with the ids of `-Pdelete` are
 * removed first, with their pictures and sounds. Close the app first: the database is one file.
 * For whoever makes a course with Claude and wants it in the app without the file dialog.
 */
object ImportCourse {
    private const val DELETE = "--delete="
    private val clock = Clock { ZonedInstant(kotlin.time.Clock.System.now(), TimeZone.currentSystemDefault()) }

    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args.toList(), DesktopStorage.defaultDataDir().toFile(), PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8)))
    }

    /** Imports the files in [paths] into the data folder [dataDir]; prints the outcome; 0 when all of them were imported. */
    fun run(args: List<String>, dataDir: File, out: PrintStream): Int {
        val delete = args.filter { it.startsWith(DELETE) }.flatMap { it.removePrefix(DELETE).split(",") }.filter { it.isNotBlank() }
        val paths = args.filterNot { it.startsWith(DELETE) }
        if (paths.isEmpty()) {
            out.println("Usage: ./gradlew :client:importCourse -Pcourse=path/to/course.zip [-Pdelete=id1,id2]")
            return 2
        }
        val storage = DesktopStorage.open(dataDir.toPath())
        try {
            val service = CourseService(storage.courses, storage.courseSettings, clock)
            val library = CourseLibrary(storage.courses, service, FileImageStore(dataDir.toPath().resolve("images")), HttpImageFetcher(), CourseArchive, clock)
            for (id in delete) {
                library.delete(Uuid.parse(id))
                out.println("removed course $id")
            }
            var failed = 0
            for (path in paths) {
                val file = File(path)
                if (!file.isFile) {
                    out.println("$path: no such file")
                    failed++
                    continue
                }
                try {
                    val (course, warnings) = library.import(file.name, file.readBytes())
                    out.println("$path: imported \"${course.name}\" (${course.id}), ${course.allTopics.size} topics")
                    warnings.forEach { out.println("  warning: $it") }
                } catch (e: CourseFormatException) {
                    out.println("$path: ${e.problems.size} problem(s)")
                    e.problems.forEach { out.println("  $it") }
                    failed++
                }
            }
            return if (failed == 0) 0 else 1
        } finally {
            storage.close()
        }
    }
}
