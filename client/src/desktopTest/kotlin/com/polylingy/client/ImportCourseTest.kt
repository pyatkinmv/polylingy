package com.polylingy.client

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportCourseTest {
    private val files = createTempDirectory("import-course-files").toFile()
    private val data = createTempDirectory("import-course-data").toFile()

    private fun run(vararg args: String): Pair<Int, String> {
        val bytes = ByteArrayOutputStream()
        val code = ImportCourse.run(args.toList(), data, PrintStream(bytes, true, Charsets.UTF_8))
        return code to bytes.toString(Charsets.UTF_8)
    }

    private fun file(name: String, text: String) = File(files, name).apply { writeText(text) }.path

    private val good = """{ "formatVersion": 6, "name": "Imported", "topics": [ { "title": "T", "exercises": [
        { "type": "fix", "items": [ { "text": "I {{go => went}} home." } ] } ] } ] }"""

    @Test fun aGoodCourseIsImportedAndCanBeReplaced() {
        val (code, out) = run(file("ok.json", good))
        assertEquals(0, code, out)
        assertTrue(out.contains("imported \"Imported\""), out)
        val id = Regex("\\(([0-9a-f-]{36})\\)").find(out)!!.groupValues[1]
        val (code2, out2) = run("--delete=$id", file("ok.json", good))
        assertEquals(0, code2, out2)
        assertTrue(out2.contains("removed course $id"), out2)
    }

    @Test fun everyProblemIsPrintedAndAMissingFileFails() {
        val (code, out) = run(
            file("bad.json", """{ "formatVersion": 6, "name": "Bad", "topics": [ { "title": "T", "exercises": [
                { "type": "expand", "items": [ { "text": "{{always late}}", "keys": [["late", "always"]] } ] } ] } ] }"""),
            File(files, "none.json").path,
        )
        assertEquals(1, code)
        assertTrue(out.contains("problem(s)"), out)
        assertTrue(out.contains("none.json: no such file"), out)
    }

    @Test fun withoutAFileItSaysHowToUseIt() {
        val (code, out) = run()
        assertEquals(2, code)
        assertTrue(out.startsWith("Usage:"), out)
    }
}
