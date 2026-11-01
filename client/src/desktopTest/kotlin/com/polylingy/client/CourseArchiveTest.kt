package com.polylingy.client

import com.polylingy.core.content.CourseFormatException
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CourseArchiveTest {
    @Test fun plainJsonIsReadAsText() {
        val source = CourseArchive.read("a.json", "﻿{ \"x\": 1 }".toByteArray())
        assertEquals("a.json", source.fileName)
        assertEquals("{ \"x\": 1 }", source.json)
        assertEquals(emptyMap(), source.files)
    }

    @Test fun aZipGivesCourseJsonAndTheOtherFiles() {
        val zip = CourseArchive.zip("{}", mapOf("images/b.png" to byteArrayOf(2), "images/a.png" to byteArrayOf(1)))
        val source = CourseArchive.read("a.zip", zip)
        assertEquals("{}", source.json)
        assertEquals(setOf("images/a.png", "images/b.png"), source.files.keys)
        assertContentEquals(byteArrayOf(1), source.files["images/a.png"])
    }

    @Test fun directoriesAndBackslashesInAZip() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("images/"))
            zip.putNextEntry(ZipEntry("course.json")); zip.write("{}".toByteArray())
            zip.putNextEntry(ZipEntry("images\\c.png")); zip.write(byteArrayOf(3))
        }
        assertEquals(setOf("images/c.png"), CourseArchive.read("a.zip", out.toByteArray()).files.keys)
    }

    @Test fun aZipWithoutCourseJsonIsAnError() {
        val zip = CourseArchive.zip("{}", emptyMap()).let { bytes ->
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("other.json")); it.write(bytes) }
            out.toByteArray()
        }
        val problem = assertFailsWith<CourseFormatException> { CourseArchive.read("a.zip", zip) }.problems.single()
        assertEquals("$: the archive has no course.json at its root", problem.toString())
    }

    @Test fun aBrokenZipIsAnError() {
        val broken = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4) + ByteArray(30) { 0x7f }
        val problem = assertFailsWith<CourseFormatException> { CourseArchive.read("a.zip", broken) }.problems.single()
        assertEquals(true, problem.message.startsWith("not a readable zip archive"), problem.message)
    }
}
