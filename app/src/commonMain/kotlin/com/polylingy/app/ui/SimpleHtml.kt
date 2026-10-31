package com.polylingy.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

private val Tag = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)[^>]*>""")
private val Entity = Regex("""&(#\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);""")
private val Whitespace = Regex("""\s+""")
private val NamedEntities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")

private val Bold = SpanStyle(fontWeight = FontWeight.Bold)
private val InlineStyles = mapOf(
    "b" to Bold, "strong" to Bold, "h1" to Bold, "h2" to Bold, "h3" to Bold, "h4" to Bold,
    "i" to SpanStyle(fontStyle = FontStyle.Italic),
    "em" to SpanStyle(fontStyle = FontStyle.Italic),
    "u" to SpanStyle(textDecoration = TextDecoration.Underline),
    "code" to SpanStyle(fontFamily = FontFamily.Monospace),
)
private val BlockTags = setOf("p", "div", "h1", "h2", "h3", "h4", "ul", "ol", "li")

/**
 * Renders a small HTML subset — b/strong, i/em, u, code, br, p/div, h1–h4, ul/ol/li and
 * entities. Compose Multiplatform has no HTML renderer; other tags are dropped, keeping their text.
 */
fun htmlToAnnotatedString(html: String): AnnotatedString {
    val result = buildAnnotatedString {
        val openStyles = mutableListOf<String>()
        var atLineStart = true

        fun text(raw: String) {
            var text = decodeEntities(raw.replace(Whitespace, " "))
            if (atLineStart) text = text.trimStart()
            if (text.isNotEmpty()) {
                append(text)
                atLineStart = false
            }
        }

        fun lineBreak(force: Boolean) {
            if (force || !atLineStart) {
                append('\n')
                atLineStart = true
            }
        }

        var last = 0
        for (match in Tag.findAll(html)) {
            text(html.substring(last, match.range.first))
            last = match.range.last + 1
            val closing = match.groupValues[1] == "/"
            val name = match.groupValues[2].lowercase()

            when {
                name == "br" -> lineBreak(force = true)
                name in BlockTags -> lineBreak(force = false)
            }
            if (!closing && name == "li") text("• ")

            val style = InlineStyles[name] ?: continue
            if (!closing) {
                pushStyle(style)
                openStyles += name
            } else if (name in openStyles) {
                // Close everything opened after this tag too, so mismatched tags can't leak styles.
                while (openStyles.isNotEmpty()) {
                    pop()
                    if (openStyles.removeLast() == name) break
                }
            }
        }
        text(html.substring(last))
        repeat(openStyles.size) { pop() }
    }
    return result.subSequence(0, result.text.trimEnd('\n').length)
}

private fun decodeEntities(text: String): String = Entity.replace(text) { m ->
    val code = m.groupValues[1]
    when {
        code.startsWith("#x") -> code.drop(2).toIntOrNull(16)?.let { Char(it).toString() }
        code.startsWith("#") -> code.drop(1).toIntOrNull()?.let { Char(it).toString() }
        else -> NamedEntities[code.lowercase()]
    } ?: m.value
}
