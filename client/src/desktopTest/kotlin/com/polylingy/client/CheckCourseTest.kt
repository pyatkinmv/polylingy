package com.polylingy.client

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckCourseTest {
    private val dir = createTempDirectory("check-course").toFile()

    private fun check(vararg paths: String): Pair<Int, String> {
        val bytes = ByteArrayOutputStream()
        val code = CheckCourse.run(paths.toList(), PrintStream(bytes, true, Charsets.UTF_8))
        return code to bytes.toString(Charsets.UTF_8)
    }

    private fun file(name: String, text: String) = File(dir, name).apply { writeText(text) }.path

    @Test fun aGoodCourseIsOk() {
        val (code, out) = check(file("ok.json", """{ "formatVersion": 5, "name": "Ok", "topics": [ { "title": "T", "exercises": [
            { "type": "fix", "items": [ { "text": "I {{go => went}} home." } ] } ] } ] }"""))
        assertEquals(0, code)
        assertTrue(out.contains("OK — \"Ok\", 0 units, 1 topics, 1 exercises"), out)
    }

    @Test fun everyProblemIsPrintedWithItsPath() {
        val (code, out) = check(file("bad.json", """{ "formatVersion": 5, "name": "Bad", "topics": [ { "title": "T", "exercises": [
            { "type": "expand", "aside": { "image": { "src": "images/a.png", "alt": "A" } },
              "items": [ { "text": "{{always late}}", "keys": [["late", "always"]] } ] } ] } ] }"""))
        assertEquals(1, code)
        assertTrue(out.contains("topics[0].exercises[0].items[0].keys: the model answer \"always late\" doesn't pass them: Wrong order: late before always"), out)
    }

    @Test fun missingPicturesAndFilesAreProblems() {
        val (code, out) = check(file("pic.json", """{ "formatVersion": 5, "name": "Pic", "topics": [ { "title": "T", "exercises": [
            { "type": "gaps", "aside": { "image": { "src": "images/a.png", "alt": "A" } }, "items": [ { "text": "{{a}}" } ] } ] } ] }"""), File(dir, "none.json").path)
        assertEquals(1, code)
        assertTrue(out.contains("topics[0].exercises[0].aside.image.src: \"images/a.png\" needs a zip archive with that file, or an https:// URL"), out)
        assertTrue(out.contains("none.json: no such file"), out)
    }

    @Test fun withoutAFileItSaysHowToUseIt() {
        val (code, out) = check()
        assertEquals(2, code)
        assertTrue(out.startsWith("Usage:"), out)
    }
}
