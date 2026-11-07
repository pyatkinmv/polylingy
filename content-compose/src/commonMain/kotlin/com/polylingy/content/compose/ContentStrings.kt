package com.polylingy.content.compose

import androidx.compose.runtime.staticCompositionLocalOf
import com.polylingy.content.Block

/**
 * The app's own words that content views draw around a course's text: captions of remarks, "See also", and so on.
 * They belong to the language of the app, not of the course (a Russian reader of an English course sees "Совет"),
 * so a course never carries them. The app provides its set through [LocalContentStrings]; [EnglishContentStrings]
 * is the default, so a view drawn without an app around it still reads.
 */
interface ContentStrings {
    fun remark(kind: Block.Tip.Kind): String
    val seeAlso: String
    /** The name of a link whose target has no title. */
    val theory: String
    /** The line under a video card; [site] is the host of its address, like "youtu.be". */
    fun opensInBrowser(site: String): String
}

object EnglishContentStrings : ContentStrings {
    override fun remark(kind: Block.Tip.Kind) = when (kind) {
        Block.Tip.Kind.TIP -> "Tip"
        Block.Tip.Kind.NOTE -> "Note"
        Block.Tip.Kind.RULE -> "Rule"
        Block.Tip.Kind.WARNING -> "Warning"
    }
    override val seeAlso = "See also"
    override val theory = "Theory"
    override fun opensInBrowser(site: String) = "Opens in the browser · $site"
}

object RussianContentStrings : ContentStrings {
    override fun remark(kind: Block.Tip.Kind) = when (kind) {
        Block.Tip.Kind.TIP -> "Совет"
        Block.Tip.Kind.NOTE -> "Примечание"
        Block.Tip.Kind.RULE -> "Правило"
        Block.Tip.Kind.WARNING -> "Осторожно"
    }
    override val seeAlso = "См. также"
    override val theory = "Теория"
    override fun opensInBrowser(site: String) = "Откроется в браузере · $site"
}

val LocalContentStrings = staticCompositionLocalOf<ContentStrings> { EnglishContentStrings }
