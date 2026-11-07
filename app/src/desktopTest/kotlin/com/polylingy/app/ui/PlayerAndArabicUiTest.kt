package com.polylingy.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.polylingy.content.Content
import com.polylingy.content.ContentJson
import com.polylingy.content.Exercise
import com.polylingy.content.ItemCheck
import com.polylingy.content.compose.AudioPlayer
import com.polylingy.content.compose.ContentColors
import com.polylingy.content.compose.ContentTheme
import com.polylingy.content.compose.ContentView
import com.polylingy.content.compose.CourseContent
import com.polylingy.content.compose.ExerciseView
import com.polylingy.content.compose.Playback
import com.polylingy.content.emptyResponse
import com.polylingy.core.rules.Checking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The docked player of long recordings, the `track` and `video` blocks, and a script that runs from right to left. */
class PlayerAndArabicUiTest {
    private val accent = "#1784BE"

    /** A player that only remembers what it was told and lets the test say what it is doing. */
    private class FakePlayer(private val length: Float) : AudioPlayer {
        val calls = mutableListOf<String>()
        private val state = MutableStateFlow(Playback.Idle)
        override val playback: StateFlow<Playback> get() = state
        override fun play(src: String) { calls += "play $src"; state.value = Playback(src, true, 0f, length) }
        override fun pause() { calls += "pause"; state.value = state.value.copy(playing = false) }
        override fun resume() { calls += "resume"; state.value = state.value.copy(playing = true) }
        override fun seekTo(seconds: Float) { calls += "seek ${seconds.toInt()}"; state.value = state.value.copy(positionSeconds = seconds) }
        override fun stop() { calls += "stop"; state.value = Playback.Idle }
        fun at(seconds: Float) { state.value = state.value.copy(positionSeconds = seconds) }
    }

    private val theory = ContentJson.decode(Content.serializer(), """
        { "format": "blocks", "blocks": [
          { "type": "section", "title": "Vowels", "blocks": [
            { "type": "text", "text": "A dash above a letter is a short a: [بَ](ba)." },
            { "type": "track", "title": "Track 1:02 · ba, bi, bu", "audio": "audio/t1-02.mp3" },
            { "type": "video", "title": "Video: Greetings", "url": "https://www.youtube.com/watch?v=abc123",
              "note": "Watch it, then match the six greetings." },
            { "type": "text", "text": "Short sounds, like a word: " },
            { "type": "table", "rows": [ [{ "audio": "audio/w-bayt.mp3" }, "[بَيْت](bayt)", "house"] ] } ] } ] }
    """)

    private fun page(player: AudioPlayer, uri: UriHandler = object : UriHandler { override fun openUri(uri: String) {} }, block: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width = 832, height = 700) {
            setContent {
                MaterialTheme {
                    CourseContent(player, annotations = true) {
                        ContentTheme(ContentColors.forAccent(accent)) {
                            CompositionLocalProvider(LocalUriHandler provides uri) {
                                Column(Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 760.dp)) {
                                    ContentView(theory)
                                }
                            }
                        }
                    }
                }
            }
            block()
        }

    @Test fun aLongRecordingOpensTheDockAndItsControlsWork() {
        val player = FakePlayer(length = 29f)
        page(player) {
            assertTrue(onAllNodesDescribed("Player").isEmpty(), "nothing is playing: no dock")
            screenshot("player-idle")
            onNodeWithContentDescription("Track").assertIsDisplayed()
            onAllNodesWithContentDescription("Play")[0].performClick()
            waitForIdle()
            assertEquals(listOf("play audio/t1-02.mp3"), player.calls)
            onNodeWithContentDescription("Player").assertIsDisplayed()
            assertEquals(2, onAllNodesWithText("Track 1:02 · ba, bi, bu").fetchSemanticsNodes().size, "the title is in the card and in the dock")
            onNodeWithText("0:00 / 0:29").assertIsDisplayed()
            player.at(12f)
            waitForIdle()
            onNodeWithText("0:12 / 0:29").assertIsDisplayed()
            screenshot("player-docked")

            // The play button in the dock pauses; the same button starts it again.
            val pause = onAllNodesWithContentDescription("Pause")
            assertEquals(2, pause.fetchSemanticsNodes().size, "the button of the track and the one of the dock")
            pause[1].performClick()
            waitForIdle()
            assertEquals("pause", player.calls.last())
            screenshot("player-paused")
            onAllNodesWithContentDescription("Play")[0].performClick()
            waitForIdle()
            assertEquals("resume", player.calls.last(), "the button of a track that is loaded resumes it, it doesn't start over")
            onAllNodesWithContentDescription("Pause")[0].performClick()
            waitForIdle()
            assertEquals("pause", player.calls.last(), "and pauses it while it plays")

            // A click on the bar goes to that place.
            onNodeWithContentDescription("Position").performTouchInput { click(centerRight.copy(x = width * 0.75f)) }
            waitForIdle()
            assertTrue(player.calls.last().startsWith("seek "), player.calls.toString())
            val to = player.calls.last().removePrefix("seek ").toInt()
            assertTrue(to in 18..26, "the bar was clicked at three quarters of 29 s, not at $to")

            onNodeWithContentDescription("Close player").performClick()
            waitForIdle()
            assertEquals("stop", player.calls.last())
            assertTrue(onAllNodesDescribed("Player").isEmpty(), "closed")
        }
    }

    @Test fun aShortSoundNeverOpensTheDock() {
        val player = FakePlayer(length = 2f)
        page(player) {
            onAllNodesWithContentDescription("Play")[0].performClick()
            waitForIdle()
            assertEquals(listOf("play audio/t1-02.mp3"), player.calls)
            assertTrue(onAllNodesDescribed("Player").isEmpty(), "a sound of two seconds is a word: no dock")
        }
    }

    @Test fun theVideoOpensItsAddressInTheBrowser() {
        val opened = mutableListOf<String>()
        page(FakePlayer(29f), uri = object : UriHandler { override fun openUri(uri: String) { opened += uri } }) {
            onNodeWithContentDescription("Video").assertIsDisplayed()
            onNodeWithText("Video: Greetings").performClick()
            assertEquals(listOf("https://www.youtube.com/watch?v=abc123"), opened)
        }
    }

    private fun ComposeUiTest.onAllNodesDescribed(description: String) =
        onAllNodesWithContentDescription(description).fetchSemanticsNodes()

    // Arabic: letters join, vowels sit above and below, the line runs from right to left.

    private val arabic = ContentJson.decode(Content.serializer(), """
        { "format": "blocks", "blocks": [
          { "type": "section", "title": "Letters and greetings", "blocks": [
            { "type": "text", "text": "The letter [ب](bā’) with a dash above is [بَ](ba), below it is [بِ](bi), with a comma [بُ](bu)." },
            { "type": "table", "head": ["Letter", "Name", "Sounds like"], "rows": [
              ["ب", "bā’", "b in bat"], ["ت", "tā’", "t in tap"], ["ث", "thā’", "th in thin"] ] },
            { "type": "table", "head": ["Hello", "Arabic"], "rows": [
              ["Hello", "[أهلاً وسهلاً](ahlan wa sahlan)"], ["Good morning", "[صَباح الخَير](ṣabāḥ al-khayr)"] ] },
            { "type": "dialogue", "lines": [
              { "who": "Anna", "text": "[صَباح الخَير](ṣabāḥ al-khayr)", "translation": "Good morning." },
              { "who": "Hashim", "text": "[صَباح النُّور](ṣabāḥ an-nūr)", "translation": "Good morning (reply)." } ] },
            { "type": "examples", "items": [
              { "text": "[بَيْت](bayt)", "translation": "house" }, { "text": "[بِنْت](bint)", "translation": "girl" } ] } ] } ] }
    """)

    private fun exercise(json: String) = ContentJson.decode(Exercise.serializer(), json)

    private fun lookAt(name: String, theory: Content? = null, exercise: Exercise? = null, block: ComposeUiTest.() -> Unit = {}) =
        runDesktopComposeUiTest(width = 832, height = 900) {
            setContent {
                var response by remember { mutableStateOf(exercise?.emptyResponse()) }
                var checks by remember { mutableStateOf<List<ItemCheck>?>(null) }
                val check = { checks = Checking.check(exercise!!, response!!) }
                MaterialTheme {
                    ContentTheme(ContentColors.forAccent(accent)) {
                        Column(Modifier.fillMaxSize().background(Color(0xFFFAF6EC)).padding(24.dp).widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            theory?.let { ContentView(it) }
                            exercise?.let {
                                ExerciseView(it, response!!, checks, { r -> response = r }, onSubmit = check, seed = 3)
                                if (checks == null) Button(onClick = check) { Text("Check") }
                            }
                        }
                    }
                }
            }
            screenshot(name)
            block()
        }

    @Test fun arabicTheory() = lookAt("arabic-theory", theory = arabic)

    @Test fun arabicOrderOfLetters() = lookAt("arabic-order", exercise = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "order", "instruction": "Put the letters in order to make the word: house.",
          "items": [ { "answer": "ب ي ت" }, { "answer": "أنا اسمي دينا", "note": "My name is Dina." } ] }"""))

    @Test fun arabicChoiceOfAVowel() = lookAt("arabic-choice", exercise = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "choice", "instruction": "Which vowel? You hear bu.",
          "items": [ { "text": "{{بُ|بَ|بِ}}" }, { "text": "ن → {{نِ|نَ|نُ}}" } ] }"""))

    @Test fun arabicGaps() = lookAt("arabic-gaps", exercise = exercise("""
        { "id": "6f1c2b8e-3d4a-4f5b-9c6d-7e8f9a0b1c2d", "type": "gaps", "instruction": "Type the missing word.",
          "items": [ { "text": "أنا {{دينا}} وأنتِ؟" }, { "text": "ahlan = {{أهلاً}}" } ] }"""))
}
