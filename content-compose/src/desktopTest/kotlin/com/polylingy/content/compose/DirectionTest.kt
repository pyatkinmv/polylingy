package com.polylingy.content.compose

import com.polylingy.content.Inline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import androidx.compose.ui.unit.em
import kotlin.test.assertTrue

class DirectionTest {
    @Test fun aTextRunsRightToLeftWhenMostOfItsLettersDo() {
        assertTrue("أنا دينا".runsRightToLeft())
        assertTrue(Inline("أنا {{دينا}} وأنتِ؟").runsRightToLeft())
        assertFalse(Inline("Hello").runsRightToLeft())
        assertFalse(Inline("").runsRightToLeft())
        assertFalse(Inline("12 + 3 = ?").runsRightToLeft(), "no letters: the layout around decides")
    }

    @Test fun aRussianSentenceWithAnArabicWordStaysLeftToRight() {
        assertFalse(Inline("«Добро пожаловать!» по-арабски — [أهلاً](ahlan).").runsRightToLeft())
        assertFalse(Inline("Буквы ب ت ث одной формы").runsRightToLeft())
    }

    @Test fun aPromptInRussianWithAnArabicPhraseAndArabicOptionsRunsLeftToRight() {
        // "Good morning" in Russian, the phrase and the options in Arabic: more Arabic letters than Russian, but still Russian text.
        assertFalse(Inline("«Доброе утро» — [صَباح الخَير](ṣabāḥ al-khayr).\nОтвет: {{صَباح النُّور|مَساء النُّور}}").runsRightToLeft())
        assertTrue(Inline("صَباح الخَير").runsRightToLeft())
    }

    @Test fun notesOverWordsAndWrongOptionsDoNotCount() {
        assertTrue(Inline("[أنا](ana) [دينا](dina)").runsRightToLeft(), "the Latin notes over the words are not read")
        assertTrue(Inline("{{بَ|ba|bi|bu}}").runsRightToLeft(), "only the right answer of a blank counts")
        assertFalse(Inline("ب = {{tā’|bā’|thā’}}").runsRightToLeft(), "a letter and its Latin name: the name is read")
    }

    @Test fun lettersOfRightToLeftScriptsAreRecognised() {
        assertEquals(listOf(true, true, true, false, false), listOf('ب', 'ا', 'א', 'a', 'я').map { isRightToLeft(it) })
    }

    @Test fun everyLineOfEnlargedTextIsEnlargedTheSame() {
        // A line break left plain between two enlarged runs made the second line 1.4 x 1.4 times as big.
        val text = Inline("ب\nب").annotated()
        assertTrue(text.spanStyles.all { it.item.fontSize == RightToLeftScale.em }, text.spanStyles.toString())
        assertTrue(text.spanStyles.any { it.start <= 1 && it.end > 1 }, "the line break itself is inside a span")
    }
}
