package com.polylingy.core.rules

import com.polylingy.content.ItemResult
import com.polylingy.content.ItemResult.CORRECT
import com.polylingy.content.ItemResult.TYPO
import com.polylingy.content.ItemResult.WRONG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextMatcherTest {
    private data class Case(val input: String, val variants: List<String>, val expected: ItemResult)

    private fun case(input: String, right: String, expected: ItemResult) =
        Case(input, right.split('|'), expected)

    private val cases = listOf(
        // exact and variants
        case("is working", "is working", CORRECT),
        case("’s working", "is working|’s working", CORRECT),
        case("localisation", "localization|localisation", CORRECT),
        // case, spaces, final period
        case("Is Working", "is working", CORRECT),
        case("  is   working ", "is working", CORRECT),
        case("is working.", "is working", CORRECT),
        case("is working .", "is working", CORRECT),
        case("Hola!", "hola", WRONG),
        case("is working?", "is working", WRONG),
        // apostrophes: every kind is the same, but the apostrophe itself matters
        case("don't", "don’t", CORRECT),
        case("don‘t", "don't", CORRECT),
        case("donʼt", "don't", CORRECT),
        case("don`t", "don't", CORRECT),
        case("dont", "don't", WRONG),
        // a typo: one substitution or one swap of neighbours, in a word of 4+ letters
        case("is wrking", "is working", WRONG),
        case("is workimg", "is working", TYPO),
        case("is wroking", "is working", TYPO),
        case("is owrking", "is working", TYPO),
        case("is workign", "is working", TYPO),
        case("Is Wroking.", "is working", TYPO),
        // missing or extra letters are not typos: they are mistakes in endings
        case("work", "works", WRONG),
        case("works", "work", WRONG),
        case("worked", "work", WRONG),
        case("wering", "wearing", WRONG),
        // short words: no typos at all
        case("an", "a", WRONG),
        case("the", "tha", WRONG),
        case("than", "then", TYPO),
        case("is", "it", WRONG),
        // at most one typo per item
        case("is wroking hrad", "is working hard", WRONG),
        case("si working", "is working", WRONG),
        case("is wrokign", "is working", WRONG),
        // word count must match
        case("is working now", "is working", WRONG),
        case("working", "is working", WRONG),
        // typos switched off
        // nothing typed
        case("", "is working", WRONG),
        case("   ", "a", WRONG),
    )

    @Test fun table() {
        for ((input, variants, expected) in cases) {
            assertEquals(expected, TextMatcher.match(input, variants).result, "\"$input\" vs $variants")
        }
    }

    @Test fun reportsTheVariantItMatched() {
        assertEquals(TextMatch(CORRECT, "’s working"), TextMatcher.match("'s working", listOf("is working", "’s working")))
        assertEquals(TextMatch(TYPO, "is working"), TextMatcher.match("is wroking", listOf("’s working", "is working")))
        assertEquals(TextMatch(WRONG, "’s working"), TextMatcher.match("was working", listOf("’s working", "is working")))
    }

    @Test fun normalization() {
        assertEquals("don't do it", TextMatcher.normalize("  Don’t   DO it. "))
        assertEquals("", TextMatcher.normalize(" . "))
    }

    @Test fun anItemNeedsARightAnswer() {
        assertFailsWith<IllegalArgumentException> { TextMatcher.match("x", emptyList()) }
    }
}
