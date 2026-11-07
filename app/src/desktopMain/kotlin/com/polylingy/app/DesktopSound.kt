package com.polylingy.app

import io.github.oshai.kotlinlogging.KotlinLogging
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.math.abs

private val log = KotlinLogging.logger {}

/**
 * Plays course sounds on the desktop, so that a press of play is heard at once:
 * - a sound (.mp3 through JLayer's decoder, .wav through Java's own) is decoded to plain samples once and kept;
 * - the silence a speech synthesizer leaves at the start is cut off;
 * - the sound output is opened once per sample format and kept open — opening it is what takes Windows a noticeable
 *   moment; [warmUp] opens the usual ones before the first press.
 * A new sound stops the one still playing, so sounds never overlap. Playing happens on one background thread. As the
 * samples are in memory, a recording can be paused, resumed and rewound to any second ([seekTo]) at once; [state] says where it is.
 */
object DesktopSound : SoundPlayer {
    /** A decoded sound: 16-bit little-endian samples. */
    internal class Pcm(val format: AudioFormat, val bytes: ByteArray) {
        val bytesPerSecond: Float get() = format.sampleRate * format.frameSize
        val seconds: Float get() = bytes.size / bytesPerSecond
    }

    /** One sound being played, with the place in it; changed by the playing thread and by the controls. */
    private class Track(val key: String, val pcm: Pcm) {
        @Volatile var at = 0
        @Volatile var paused = false
        @Volatile var finished = false

        /** A byte to go to at the next chunk, or -1. */
        @Volatile var jumpTo = -1
    }

    private val decoded = LinkedHashMap<Int, Pcm>(64, 0.75f, true)
    private var decodedBytes = 0L
    /** Kept by what a format says: [AudioFormat] has no equals, so as a key it never found the line it had opened. */
    private data class LineKey(val rate: Float, val bits: Int, val channels: Int)
    private val lines = mutableMapOf<LineKey, SourceDataLine>()
    private val stateFlow = MutableStateFlow(SoundState())
    override val state: StateFlow<SoundState> get() = stateFlow

    /** Bumped by every play: the thread writing an older sound stops at its next chunk. */
    @Volatile private var generation = 0
    @Volatile private var current: Track? = null

    /** Opens the sound output for the usual formats of course sounds (speech at 24 kHz mono, recordings at 44.1 kHz stereo). */
    fun warmUp() {
        thread(name = "sound-warm-up", isDaemon = true) {
            for (format in listOf(AudioFormat(24000f, 16, 1, true, false), AudioFormat(44100f, 16, 2, true, false))) {
                runCatching { synchronized(lines) { line(format) } }.onFailure { log.warn(it) { "The sound output can't be opened" } }
            }
        }
    }

    override fun play(bytes: ByteArray) = play("", bytes)

    override fun play(key: String, bytes: ByteArray) {
        val number = synchronized(this) { ++generation }
        thread(name = "sound", isDaemon = true) {
            try {
                val pcm = cached(bytes)
                if (number != generation) return@thread
                val track = Track(key, pcm).also { current = it }
                run(number, track)
            } catch (e: Exception) {
                log.warn(e) { "A sound can't be played" }
            }
        }
    }

    override fun pause() {
        val track = current ?: return
        if (track.finished) return
        track.paused = true
        publish(track, playing = false)
    }

    override fun resume() {
        val track = current ?: return
        if (track.finished) {
            // Pressed again after the end: from the start, on a new thread, as the old one is done.
            track.at = 0
            track.finished = false
            track.paused = false
            val number = synchronized(this) { ++generation }
            thread(name = "sound", isDaemon = true) {
                try { run(number, track) } catch (e: Exception) { log.warn(e) { "A sound can't be played" } }
            }
        } else {
            track.paused = false
            publish(track, playing = true)
        }
    }

    override fun seekTo(seconds: Float) {
        val track = current ?: return
        val at = (seconds * track.pcm.bytesPerSecond).toInt().coerceIn(0, track.pcm.bytes.size)
        val aligned = at / track.pcm.format.frameSize * track.pcm.format.frameSize
        if (track.finished) track.at = aligned else track.jumpTo = aligned
        publish(track, playing = !track.paused && !track.finished, at = aligned)
    }

    override fun stop() {
        synchronized(this) { ++generation }
        current = null
        stateFlow.value = SoundState()
    }

    /** Writes [track] to the sound output chunk by chunk until its end, a newer sound or [stop]. */
    private fun run(number: Int, track: Track) {
        val pcm = track.pcm
        val line = synchronized(lines) { line(pcm.format) }
        synchronized(line) {
            if (number != generation) return
            line.flush()
            publish(track, playing = !track.paused)
            var lastPublished = System.nanoTime()
            while (number == generation) {
                if (track.paused) {
                    Thread.sleep(PAUSED_POLL_MS)
                    continue
                }
                val jump = track.jumpTo
                if (jump >= 0) {
                    track.at = jump
                    track.jumpTo = -1
                    line.flush()
                }
                if (track.at >= pcm.bytes.size) break
                val chunk = minOf(CHUNK, pcm.bytes.size - track.at)
                line.write(pcm.bytes, track.at, chunk)
                track.at += chunk
                val now = System.nanoTime()
                if (now - lastPublished >= PUBLISH_NS) {
                    publish(track, playing = true)
                    lastPublished = now
                }
            }
            if (number == generation) {
                track.finished = true
                publish(track, playing = false, at = pcm.bytes.size)
            } else line.flush()
        }
    }

    private fun publish(track: Track, playing: Boolean, at: Int = track.at) {
        if (current !== track) return
        stateFlow.value = SoundState(track.key, playing, at / track.pcm.bytesPerSecond, track.pcm.seconds)
    }

    private fun line(format: AudioFormat): SourceDataLine =
        lines.getOrPut(LineKey(format.sampleRate, format.sampleSizeInBits, format.channels)) { open(format) }

    /**
     * Opens the sound output for [format]. The default output with a short buffer first (a new sound replaces the old one
     * without the old one's tail); when the system refuses, the default buffer, then every other output in turn: a busy
     * or just-changed device (headphones plugged in, another program holding it) must not leave the app silent for good.
     */
    private fun open(format: AudioFormat): SourceDataLine {
        val bufferBytes = (format.sampleRate * format.frameSize * BUFFER_SECONDS).toInt() / format.frameSize * format.frameSize
        val attempts = buildList<() -> SourceDataLine> {
            add { AudioSystem.getSourceDataLine(format).apply { open(format, bufferBytes) } }
            add { AudioSystem.getSourceDataLine(format).apply { open(format) } }
            for (info in AudioSystem.getMixerInfo()) add {
                AudioSystem.getMixer(info).let { mixer ->
                    (mixer.getLine(javax.sound.sampled.DataLine.Info(SourceDataLine::class.java, format)) as SourceDataLine).apply { open(format) }
                }
            }
        }
        var first: Exception? = null
        for ((i, attempt) in attempts.withIndex()) {
            try {
                return attempt().also {
                    it.start()
                    if (i > 0) log.warn { "The default sound output refused $format; playing through attempt $i instead: ${first?.message}" }
                }
            } catch (e: Exception) {
                if (first == null) first = e
            }
        }
        log.warn { "Sound outputs: ${AudioSystem.getMixerInfo().joinToString { it.name }}" }
        throw first ?: IllegalStateException("no sound output")
    }

    /** The decoded sound of [bytes]; the least recently played are forgotten when the kept samples pass [MAX_KEPT_BYTES]. */
    private fun cached(bytes: ByteArray): Pcm = synchronized(decoded) {
        val key = bytes.contentHashCode() xor bytes.size
        decoded[key] ?: decode(bytes).also { pcm ->
            decoded[key] = pcm
            decodedBytes += pcm.bytes.size
            val oldest = decoded.entries.iterator()
            while (decodedBytes > MAX_KEPT_BYTES && decoded.size > 1 && oldest.hasNext()) {
                val entry = oldest.next()
                if (entry.value === pcm) break
                decodedBytes -= entry.value.bytes.size
                oldest.remove()
            }
        }
    }

    internal fun decode(bytes: ByteArray): Pcm = trimmed(if (bytes.isWav()) wav(bytes) else mp3(bytes))

    private fun mp3(bytes: ByteArray): Pcm {
        val stream = Bitstream(ByteArrayInputStream(bytes))
        val decoder = Decoder()
        val out = ByteArrayOutputStream(bytes.size * 12)
        var rate = 24000
        var channels = 1
        try {
            while (true) {
                val header = stream.readFrame() ?: break
                val samples = decoder.decodeFrame(header, stream) as SampleBuffer
                rate = samples.sampleFrequency
                channels = samples.channelCount
                val buffer = samples.buffer
                for (i in 0 until samples.bufferLength) {
                    val s = buffer[i].toInt()
                    out.write(s and 0xFF)
                    out.write(s shr 8 and 0xFF)
                }
                stream.closeFrame()
            }
        } finally {
            stream.close()
        }
        return Pcm(AudioFormat(rate.toFloat(), 16, channels, true, false), out.toByteArray())
    }

    private fun wav(bytes: ByteArray): Pcm {
        val source = AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
        val f = source.format
        val target = AudioFormat(f.sampleRate, 16, f.channels, true, false)
        return Pcm(target, AudioSystem.getAudioInputStream(target, source).readAllBytes())
    }

    /** The sound from just before its first audible sample: speech synthesis starts with a pause. */
    private fun trimmed(pcm: Pcm): Pcm {
        val frame = pcm.format.frameSize
        val b = pcm.bytes
        var first = 0
        while (first + 1 < b.size && abs((b[first].toInt() and 0xFF) or (b[first + 1].toInt() shl 8)) < SILENCE) first += 2
        val lead = (pcm.format.sampleRate * frame * LEAD_SECONDS).toInt()
        val start = (first - lead).coerceAtLeast(0) / frame * frame
        return if (start == 0) pcm else Pcm(pcm.format, b.copyOfRange(start, b.size))
    }

    private fun ByteArray.isWav() = size > 12 && decodeToString(0, 4) == "RIFF" && decodeToString(8, 12) == "WAVE"

    /** About 150 MB of samples: a 2.5-minute stereo recording is 27 MB. */
    private const val MAX_KEPT_BYTES = 150L * 1024 * 1024
    private const val CHUNK = 2048
    private const val BUFFER_SECONDS = 0.08f
    private const val PAUSED_POLL_MS = 15L
    private const val PUBLISH_NS = 50_000_000L
    private const val SILENCE = 400
    private const val LEAD_SECONDS = 0.02f
}
