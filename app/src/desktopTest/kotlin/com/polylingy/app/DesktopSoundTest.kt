package com.polylingy.app

import java.io.File
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

class DesktopSoundTest {
    private val mp3 = ZipFile(File("../examples/chinese-1.zip")).use { zip ->
        zip.getInputStream(zip.getEntry("audio/l1-w-02.mp3")).readBytes()
    }

    @Test fun anMp3IsDecodedQuicklyAndStartsWithoutThePauseOfSynthesis() {
        DesktopSound.decode(mp3) // the decoder's own classes load once
        lateinit var pcm: DesktopSound.Pcm
        val took = measureTime { pcm = DesktopSound.decode(mp3) }
        assertTrue(took.inWholeMilliseconds < 100, "decoding took $took")
        assertEquals(24000f, pcm.format.sampleRate)
        assertEquals(1, pcm.format.channels)
        val leadSamples = (24000 * 0.03).toInt()
        val firstLoud = (0 until pcm.bytes.size / 2).first { i -> abs((pcm.bytes[2 * i].toInt() and 0xFF) or (pcm.bytes[2 * i + 1].toInt() shl 8)) >= 400 }
        assertTrue(firstLoud <= leadSamples, "the first audible sample is at ${firstLoud * 1000 / 24000} ms")
    }
}
