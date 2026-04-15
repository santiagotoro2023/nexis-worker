package ch.toroag.nexis.worker.alarm

import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates the NeXiS alarm tone as raw 16-bit PCM (44100 Hz, mono).
 *
 * Pattern: descending minor sequence — eerie, deliberate, distinctly not a cheerful alarm.
 *   C5 (523 Hz)  400ms  — high, attention-getting
 *   G4 (392 Hz)  400ms  — mid, settling
 *   Eb4 (311 Hz) 700ms  — low minor third, unsettling, lingers
 *   silence       350ms
 *   repeat
 */
object AlarmToneGenerator {

    private const val SAMPLE_RATE = 44100

    // One full sequence (pre-rendered for efficiency)
    val sequence: ShortArray by lazy { buildSequence() }
    val sampleRate: Int get() = SAMPLE_RATE

    private fun buildSequence(): ShortArray {
        val notes = listOf(
            Pair(523.25, 400),   // C5
            Pair(392.00, 400),   // G4
            Pair(311.13, 700),   // Eb4
            Pair(0.0,    350),   // silence
        )
        val totalSamples = notes.sumOf { (_, ms) -> ms * SAMPLE_RATE / 1000 }
        val buf = ShortArray(totalSamples)
        var offset = 0
        for ((freq, durationMs) in notes) {
            val n = durationMs * SAMPLE_RATE / 1000
            for (i in 0 until n) {
                val t = i.toDouble() / SAMPLE_RATE
                val amp = 0.75 * Short.MAX_VALUE
                // Envelope: fade in first 8%, fade out last 15%
                val env = when {
                    freq == 0.0         -> 0.0
                    i < n * 0.08        -> i / (n * 0.08)
                    i > n * 0.85        -> (n - i) / (n * 0.15)
                    else                -> 1.0
                }
                buf[offset + i] = (amp * env * sin(2.0 * PI * freq * t)).toInt().toShort()
            }
            offset += n
        }
        return buf
    }
}
