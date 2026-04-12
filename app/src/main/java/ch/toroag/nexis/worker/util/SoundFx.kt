package ch.toroag.nexis.worker.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.exp

/**
 * Procedural UI audio for NeXiS.
 * Generates all sounds in-memory — no audio files needed.
 * Tuned for a subtle Aperture Science feel: clean, electronic, clinical.
 */
object SoundFx {

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private data class Note(
        val freqStart: Float,
        val freqEnd:   Float,
        val ms:        Int,
        val volume:    Float = 1f,
    )

    private fun render(notes: List<Note>, sampleRate: Int = 22050): ShortArray {
        val total = notes.sumOf { sampleRate * it.ms / 1000 }
        val buf   = ShortArray(total)
        var pos   = 0
        var phase = 0.0
        for (note in notes) {
            val n   = sampleRate * note.ms / 1000
            val vol = note.volume * 11000f
            for (i in 0 until n) {
                val ratio = i.toDouble() / n
                val freq  = note.freqStart + (note.freqEnd - note.freqStart) * ratio
                // Exponential decay envelope — fast attack, natural tail-off
                val env = when {
                    i < n * 0.05  -> ratio / 0.05          // 5% attack
                    else          -> exp(-(ratio - 0.05) * 4.5)  // decay
                }
                phase += 2 * PI * freq / sampleRate
                // Fundamental + 2nd harmonic for a slightly rich tone
                val sample = sin(phase) * 0.82 + sin(2 * phase) * 0.18
                buf[pos++] = (sample * env * vol).toInt().toShort()
            }
        }
        return buf
    }

    private fun play(buf: ShortArray, sampleRate: Int = 22050) {
        Thread {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                val track = AudioTrack(
                    attrs,
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    maxOf(minBuf, buf.size * 2),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE,
                )
                track.play()
                track.write(buf, 0, buf.size)
                Thread.sleep(buf.size * 1000L / sampleRate + 60)
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }.also { it.isDaemon = true }.start()
    }

    // ── Public sounds ──────────────────────────────────────────────────────────

    /** Mic activate — two rising electronic pings (scanner locking on) */
    fun micActivate() = play(render(listOf(
        Note(880f,  1120f, 65,  0.55f),
        Note(1f,    1f,    22,  0f),     // gap
        Note(1340f, 1760f, 75,  0.60f),
    )))

    /** Mic deactivate — descending close tone (system powering down) */
    fun micDeactivate() = play(render(listOf(
        Note(1100f, 580f, 120, 0.50f),
    )))

    /** Send message — brief upward tick */
    fun send() = play(render(listOf(
        Note(1200f, 1600f, 55, 0.38f),
    )))

    /** Generic button tap — very subtle low click */
    fun tap() = play(render(listOf(
        Note(420f, 380f, 40, 0.28f),
    )))

    /** Connection established — ascending three-tone confirmation */
    fun connected() = play(render(listOf(
        Note(660f,  880f,  60, 0.45f),
        Note(1f,    1f,    15, 0f),
        Note(880f,  1100f, 60, 0.45f),
        Note(1f,    1f,    15, 0f),
        Note(1100f, 1320f, 80, 0.50f),
    )))

    /** Error / disconnect — descending two-tone warning */
    fun error() = play(render(listOf(
        Note(600f, 420f, 90, 0.42f),
        Note(1f,   1f,   20, 0f),
        Note(380f, 260f, 100, 0.38f),
    )))
}
