package com.polylingy.content

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A text with a little markup: `**bold**`, `*italic*`, blanks `{{answer|other variant}}`, annotations
 * `[base](annotation)` — small text over a word, like how to read it — and line breaks.
 * `\` makes the next `*`, `{`, `}`, `|`, `[`, `]` or `\` literal; a `[` not followed by `…](…)` is plain text. Malformed markup doesn't throw: it is shown as plain
 * text and reported in [problems], so import can list every mistake at once.
 */
@Serializable(with = InlineSerializer::class)
class Inline(val text: String) {
    val nodes: List<InlineNode>
    val problems: List<String>

    /** All blanks in reading order; [InlineNode.Blank.index] is the position in this list. */
    val blanks: List<InlineNode.Blank>

    /** Whether some word has an annotation over it. */
    val hasAnnotations: Boolean get() = text.contains("](") && nodes.any(::annotated)

    private fun annotated(node: InlineNode): Boolean = when (node) {
        is InlineNode.Annotated -> true
        is InlineNode.Bold -> node.children.any(::annotated)
        is InlineNode.Italic -> node.children.any(::annotated)
        is InlineNode.Text, is InlineNode.Blank, InlineNode.LineBreak -> false
    }

    init {
        val parsed = InlineParser(text).parse()
        nodes = parsed.first
        problems = parsed.second
        blanks = buildList { collectBlanks(nodes) }
    }

    private fun MutableList<InlineNode.Blank>.collectBlanks(nodes: List<InlineNode>) {
        for (node in nodes) when (node) {
            is InlineNode.Blank -> add(node)
            is InlineNode.Bold -> collectBlanks(node.children)
            is InlineNode.Italic -> collectBlanks(node.children)
            is InlineNode.Text, is InlineNode.Annotated, InlineNode.LineBreak -> {}
        }
    }

    /** The text without markup; a blank shows as its answer, an annotated word as the word. For places that can't show formatting. */
    fun plainText(): String = buildString { plain(nodes) }

    private fun StringBuilder.plain(nodes: List<InlineNode>) {
        for (node in nodes) when (node) {
            is InlineNode.Text -> append(node.text)
            is InlineNode.Bold -> plain(node.children)
            is InlineNode.Italic -> plain(node.children)
            is InlineNode.Blank -> append(node.answer)
            is InlineNode.Annotated -> append(node.base)
            InlineNode.LineBreak -> append('\n')
        }
    }

    override fun equals(other: Any?): Boolean = other is Inline && other.text == text
    override fun hashCode(): Int = text.hashCode()
    override fun toString(): String = text
}

sealed interface InlineNode {
    data class Text(val text: String) : InlineNode
    data class Bold(val children: List<InlineNode>) : InlineNode
    data class Italic(val children: List<InlineNode>) : InlineNode

    /** A blank to fill in; [variants] are all accepted answers, the first one is shown as the right answer. */
    data class Blank(val index: Int, val variants: List<String>) : InlineNode {
        val answer: String get() = variants.first()
    }

    /** A word with small text over it: `[老师](lǎoshī)`. */
    data class Annotated(val base: String, val annotation: String) : InlineNode

    data object LineBreak : InlineNode
}

object InlineSerializer : KSerializer<Inline> {
    override val descriptor = PrimitiveSerialDescriptor("com.polylingy.content.Inline", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Inline = Inline(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: Inline) = encoder.encodeString(value.text)
}

private class InlineParser(private val s: String) {
    private val problems = mutableListOf<String>()
    private var blankCount = 0

    /** An open `**` or `*` (or the root, [mark] == null) and what has been read inside it. */
    private class Frame(val mark: String?, val start: Int) {
        val children = mutableListOf<InlineNode>()
    }

    private val stack = ArrayDeque<Frame>().apply { addLast(Frame(null, 0)) }
    private val text = StringBuilder()

    fun parse(): Pair<List<InlineNode>, List<String>> {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE -> { text.append(s[i + 1]); i += 2 }
                s.startsWith("{{", i) -> i = blank(i)
                c == '[' -> i = annotated(i)
                s.startsWith("}}", i) -> { problems += "}} without {{ at ${i + 1}"; text.append("}}"); i += 2 }
                s.startsWith("**", i) -> { toggle("**", i); i += 2 }
                c == '*' -> { toggle("*", i); i += 1 }
                c == '\n' -> { flush(); stack.last().children += InlineNode.LineBreak; i += 1 }
                else -> { text.append(c); i += 1 }
            }
        }
        flush()
        while (stack.size > 1) {
            val open = stack.removeLast()
            problems += "${open.mark} at ${open.start + 1} is not closed"
            stack.last().children += InlineNode.Text(open.mark!!)
            stack.last().children += open.children
        }
        return stack.single().children.toList() to problems.toList()
    }

    private fun flush() {
        if (text.isEmpty()) return
        val children = stack.last().children
        val last = children.lastOrNull()
        if (last is InlineNode.Text) children[children.lastIndex] = InlineNode.Text(last.text + text)
        else children += InlineNode.Text(text.toString())
        text.clear()
    }

    private fun toggle(mark: String, at: Int) {
        when {
            stack.last().mark == mark -> {
                flush()
                val open = stack.removeLast()
                stack.last().children += if (mark == "**") InlineNode.Bold(open.children) else InlineNode.Italic(open.children)
            }
            stack.any { it.mark == mark } -> {
                problems += "$mark at ${at + 1} closes a $mark that has another mark open inside it"
                text.append(mark)
            }
            else -> {
                flush()
                stack.addLast(Frame(mark, at))
            }
        }
    }

    /** Reads `[base](annotation)` starting at [at]; anything else is a plain `[`. Returns the index after what it read. */
    private fun annotated(at: Int): Int {
        val close = s.indexOf("](", at + 1)
        val end = if (close < 0) -1 else s.indexOf(')', close + 2)
        val base = if (end < 0) "" else s.substring(at + 1, close)
        val annotation = if (end < 0) "" else s.substring(close + 2, end)
        if (end < 0 || base.isBlank() || annotation.isBlank() || base.any { it in "[]{}\n" } || annotation.any { it in "[](\n" }) {
            text.append('[')
            return at + 1
        }
        flush()
        stack.last().children += InlineNode.Annotated(base.trim(), annotation.trim())
        return end + 1
    }

    /** Reads `{{…}}` starting at [at]; returns the index after it. */
    private fun blank(at: Int): Int {
        val end = s.indexOf("}}", at + 2)
        if (end < 0) {
            problems += "{{ at ${at + 1} is not closed"
            text.append(s, at, s.length)
            return s.length
        }
        val body = s.substring(at + 2, end)
        if ("{{" in body) problems += "{{ at ${at + 1} has another {{ inside"
        val variants = body.split('|').map { it.trim() }
        if (variants.any { it.isEmpty() }) problems += "{{…}} at ${at + 1} has an empty answer"
        flush()
        stack.last().children += InlineNode.Blank(blankCount++, variants.filter { it.isNotEmpty() }.ifEmpty { listOf("") })
        return end + 2
    }

    private companion object {
        const val ESCAPABLE = "\\*{}|[]"
    }
}
