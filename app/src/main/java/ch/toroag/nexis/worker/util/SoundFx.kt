package ch.toroag.nexis.worker.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Procedural audio for NeXiS UI events.
 * Generates short electronic tones via AudioTrack — no audio files needed.
 * Tuned for an Aperture Science-style feel: clean, clinical, electronic.
 */
object SoundFx {

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private data class Note(val freqStart: Float, val freqEnd: Float, val durationMs: Int)

    private fun render(sampleRate: Int = 22050, notes: List<Note>): ShortArray {
        val totalSamples = notes.sumOf { sampleRate * it.durationMs / 1000 }
        val buf = ShortArray(totalSamples)
        var pos = 0
        var phase = 0.0
        for (note in notes) {
            val n = sampleRate * note.durationMs / 1000
            for (i in 0 until n) {
                val ratio = i.toDouble() / n
                val freq = note.freqStart + (note.freqEnd - note.freqStart) * ratio
                // Envelope: short attack, sustain, longer release
                val env = when {
                    i < n * 0.04 -> ratio / 0.04
                    i > n * 0.65 -> 1.0 - (ratio - 0.65) / 0.35
                    else         -> 1.0
                }
                phase += 2 * PI * freq / sampleRate
                // Fundamental + subtle 2nd harmonic for richness
                val sample = sin(phase) * 0.85 + sin(2 * phase) * 0.15
                buf[pos++] = (sample * env * 13000).toInt().toShort()
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
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(buf.size * 2, minBuf))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, buf.size)
                track.play()
                Thread.sleep(buf.size * 1000L / sampleRate + 80)
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }.start()
    }

    /**
     * Mic activated — two rising electronic pings.
     * Like a portal scanner locking on.
     */
    fun micActivate() = play(render(notes = listOf(
        Note(920f,  1180f, 65),   // rising first ping
        Note(1f,    1f,    25),   // brief silence gap
        Note(1380f, 1760f, 75),   // rising second ping, higher
    )))

    /**
     * Mic deactivated — single descending close tone.
     * Like a turret powering down.
     */
    fun micDeactivate() = play(render(notes = listOf(
        Note(1100f, 620f, 130),
    )))
}
