package com.polylingy.client

import com.polylingy.core.content.CourseFormatException
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.system.exitProcess
import kotlin.time.Clock

/**
 * `./gradlew :client:checkCourse -Pcourse=path/to/course.json` (or a .zip): checks a course file exactly as import
 * does, without the app and without saving anything, and prints every problem with its path. For whoever writes a
 * course by hand or with Claude: a file this passes imports.
 */
object CheckCourse {
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(run(args.toList(), PrintStream(FileOutputStream(FileDescriptor.out), true, Charsets.UTF_8)))
    }

    /** Checks the files in [paths]; prints what it found to [out]; 0 when all of them are fine. */
    fun run(paths: List<String>, out: PrintStream): Int {
        if (paths.isEmpty()) {
            out.println("Usage: ./gradlew :client:checkCourse -Pcourse=path/to/course.json (or .zip)")
            return 2
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
                val (course, warnings) = CourseArchive.read(file.name, file.readBytes()).check(Clock.System.now())
                out.println("$path: OK — \"${course.name}\", ${course.units.size} units, ${course.allTopics.size} topics, " +
                    "${course.allTopics.sumOf { it.exercises.size }} exercises")
                warnings.forEach { out.println("  warning: $it") }
            } catch (e: CourseFormatException) {
                out.println("$path: ${e.problems.size} problem(s)")
                e.problems.forEach { out.println("  $it") }
                failed++
            }
        }
        return if (failed == 0) 0 else 1
    }
}
