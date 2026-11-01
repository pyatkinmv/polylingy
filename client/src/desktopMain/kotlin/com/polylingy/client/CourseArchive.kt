package com.polylingy.client

import com.polylingy.content.Path
import com.polylingy.content.Problem
import com.polylingy.core.content.CourseFormatException
import com.polylingy.core.content.CourseJson
import com.polylingy.core.content.images
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.io.IOException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The two kinds of course file: `course.json` alone (pictures only by `https://` URL), or a zip with
 * `course.json` at its root and the course's pictures in `images/`.
 */
object CourseArchive : CourseArchives {
    const val COURSE_JSON = "course.json"

    override fun read(fileName: String, bytes: ByteArray): CourseSource =
        if (bytes.isZip()) readZip(fileName, bytes) else CourseSource(fileName, text(bytes))

    /** A zip of [json] as `course.json` and [files] under their own paths. */
    override fun zip(json: String, files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(COURSE_JSON))
            zip.write(json.toByteArray())
            for ((name, data) in files.toSortedMap()) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
            }
        }
        return out.toByteArray()
    }

    private fun readZip(fileName: String, bytes: ByteArray): CourseSource {
        val entries = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entries[it.name.replace('\\', '/')] = zip.readBytes() }
            }
        } catch (e: IOException) {
            throw CourseFormatException(listOf(Problem(Path.ROOT, "not a readable zip archive: ${e.message}")))
        }
        val json = entries.remove(COURSE_JSON)
            ?: throw CourseFormatException(listOf(Problem(Path.ROOT, "the archive has no $COURSE_JSON at its root")))
        return CourseSource(fileName, text(json), entries)
    }

    private fun ByteArray.isZip() = size >= 4 && this[0] == 'P'.code.toByte() && this[1] == 'K'.code.toByte() && this[2] == 3.toByte() && this[3] == 4.toByte()

    /** UTF-8, without the byte order mark some editors write. */
    private fun text(bytes: ByteArray) = bytes.decodeToString().removePrefix("﻿")
}

/** The sample course bundled with the app: `sample/course.json` and its pictures. */
fun sampleCourse(): CourseSource {
    fun resource(name: String) = requireNotNull(CourseArchive::class.java.getResource("/sample/$name")) { "sample/$name is not bundled" }.readBytes()
    val json = resource(CourseArchive.COURSE_JSON).decodeToString()
    val srcs = CourseJson.read(json, kotlin.time.Clock.System.now()).course.images().map { it.second.src }.filterNot { it.startsWith("https://") }
    return CourseSource("sample course", json, srcs.distinct().associateWith(::resource))
}
