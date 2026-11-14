package com.polylingy.content

import com.polylingy.content.InlineNode.Annotated
import com.polylingy.content.InlineNode.Blank
import com.polylingy.content.InlineNode.Bold
import com.polylingy.content.InlineNode.Italic
import com.polylingy.content.InlineNode.LineBreak
import com.polylingy.content.InlineNode.Marked
import com.polylingy.content.InlineNode.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InlineTest {
    private fun nodes(text: String): List<InlineNode> = Inline(text).also { assertEquals(emptyList(), it.problems, text) }.nodes

    @Test fun plainText() = assertEquals(listOf(Text("Hello, world.")), nodes("Hello, world."))

    @Test fun emptyText() = assertEquals(emptyList(), nodes(""))

    @Test fun boldAndItalic() = assertEquals(
        listOf(Text("He"), Bold(listOf(Text("’s cycling"))), Text(" to work "), Italic(listOf(Text("now"))), Text(".")),
        nodes("He**’s cycling** to work *now*."),
    )

    @Test fun nestedMarks() = assertEquals(
        listOf(Bold(listOf(Text("a "), Italic(listOf(Text("b"))), Text(" c")))),
        nodes("**a *b* c**"),
    )

    @Test fun highlightsHaveAToneOrAmberByDefault() {
        assertEquals(listOf(Text("a "), Marked(listOf(Text("b")), MarkTone.FIRST), Text(" c")), nodes("a ==b== c"))
        assertEquals(listOf(Marked(listOf(Text("root")), MarkTone.SECOND), Marked(listOf(Text("ing")), MarkTone.THIRD)), nodes("=={2}root===={3}ing=="))
        assertEquals(listOf(Marked(listOf(Text("a "), Bold(listOf(Text("b")))), MarkTone.FIFTH)), nodes("=={5}a **b**=="))
        assertEquals(listOf(Marked(listOf(Text("x")), MarkTone.FIRST)), nodes("=={1}x=="))
        assertEquals("root ing", Inline("=={2}root== ==ing==").plainText())
    }

    @Test fun aHighlightMayHoldAnAnnotationAndBlanks() {
        assertTrue(Inline("==[你好](nǐ hǎo)==").hasAnnotations)
        assertEquals(1, Inline("==a {{b}}==").blanks.size)
    }

    @Test fun aBrokenHighlightIsAProblemAndPlainText() {
        assertTrue(Inline("a == b").problems.isNotEmpty())
        assertTrue(Inline("=={x}a==").problems.isNotEmpty(), "x is no tone")
        assertTrue(Inline("=={g}a==").problems.isNotEmpty(), "tones are numbers, not colours")
        assertTrue(Inline("=={6}a==").problems.isNotEmpty(), "there are five tones")
        assertEquals(emptyList(), Inline("a \\== b").problems)
        assertEquals("a == b", Inline("a \\== b").plainText())
    }

    @Test fun annotatedWords() {
        assertEquals(listOf(Annotated("你好", "nǐ hǎo"), Text("！"), Bold(listOf(Annotated("老师", "lǎoshī")))), nodes("[你好](nǐ hǎo)！**[老师](lǎoshī)**"))
        val inline = Inline("[我](wǒ) [是](shì) [学生](xuésheng)。")
        assertTrue(inline.hasAnnotations)
        assertEquals("我 是 学生。", inline.plainText())
        assertTrue(inline.blanks.isEmpty())
    }

    @Test fun aBracketWithoutAnAnnotationIsPlainText() {
        for (text in listOf("[sic]", "a [b] (c)", "[](x)", "[a]()", "[a](b", "\\[a](b)")) {
            val inline = Inline(text)
            assertEquals(emptyList(), inline.problems, text)
            assertTrue(!inline.hasAnnotations, text)
        }
        assertEquals("[a](b)", Inline("\\[a](b)").plainText())
        assertEquals(listOf(Text("x "), Annotated("a", "b")), nodes("x [a](b)"))
    }

    @Test fun blanksWithVariantsAreTrimmedAndNumbered() {
        val inline = Inline("I {{ ’m working | am working }} and {{sing}}.")
        assertEquals(emptyList(), inline.problems)
        assertEquals(listOf(Blank(0, listOf("’m working", "am working")), Blank(1, listOf("sing"))), inline.blanks)
        assertEquals("’m working", inline.blanks[0].answer)
        assertEquals(listOf(Text("I "), inline.blanks[0], Text(" and "), inline.blanks[1], Text(".")), inline.nodes)
    }

    @Test fun blankInsideBoldIsFound() {
        val inline = Inline("**{{Am}} I** working?")
        assertEquals(listOf(Blank(0, listOf("Am"))), inline.blanks)
        assertEquals(listOf(Bold(listOf(Blank(0, listOf("Am")), Text(" I"))), Text(" working?")), inline.nodes)
    }

    @Test fun blankInsideItalicIsFound() = assertEquals(1, Inline("*{{x}}*").blanks.size)

    @Test fun lineBreaks() = assertEquals(listOf(Text("a"), LineBreak, Bold(listOf(Text("b")))), nodes("a\n**b**"))

    @Test fun escapesMakeMarksLiteral() = assertEquals(listOf(Text("5 * 3 = {15} | \\")), nodes("""5 \* 3 = \{15\} \| \\"""))

    @Test fun backslashBeforeOtherCharsStays() = assertEquals(listOf(Text("""a\b""")), nodes("""a\b"""))

    private fun problems(text: String) = Inline(text).problems

    @Test fun unclosedBoldIsReportedAndShownAsText() {
        val inline = Inline("a **b")
        assertEquals(listOf("** at 3 is not closed"), inline.problems)
        assertEquals(listOf(Text("a "), Text("**"), Text("b")), inline.nodes)
    }

    @Test fun unclosedItalic() = assertEquals(listOf("* at 1 is not closed"), problems("*a"))

    @Test fun unclosedBlank() {
        val inline = Inline("I {{am working")
        assertEquals(listOf("{{ at 3 is not closed"), inline.problems)
        assertEquals(emptyList(), inline.blanks)
        assertEquals(listOf(Text("I {{am working")), inline.nodes)
    }

    @Test fun strayClosingBraces() = assertEquals(listOf("}} without {{ at 2"), problems("a}}"))

    @Test fun emptyAnswer() {
        assertEquals(listOf("{{…}} at 1 has an empty answer"), problems("{{}}"))
        assertEquals(listOf("{{…}} at 1 has an empty answer"), problems("{{a|}}"))
        assertEquals(listOf(Blank(0, listOf("a"))), Inline("{{a|}}").blanks)
    }

    @Test fun nestedBlank() = assertEquals(listOf("{{ at 1 has another {{ inside"), problems("{{a {{b}}"))

    @Test fun crossedMarks() = assertTrue(problems("**a *b** c*").any { "another mark open inside" in it })

    @Test fun plainTextDropsMarkup() =
        assertEquals("He’s cycling now.\nI am working", Inline("He**’s cycling** *now*.\nI {{am working|’m working}}").plainText())

    @Test fun equalityIsByText() {
        assertEquals(Inline("a **b**"), Inline("a **b**"))
        assertEquals(Inline("a").hashCode(), Inline("a").hashCode())
        assertNotEquals(Inline("a"), Inline("b"))
        assertNotEquals<Any>(Inline("a"), "a")
        assertEquals("a **b**", Inline("a **b**").toString())
    }
}
