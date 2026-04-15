package ch.toroag.nexis.desktop.util

import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

/**
 * Captures microphone audio into a WAV-formatted ByteArray.
 *
 * Usage:
 *   val recorder = MicRecorder()
 *   recorder.start()
 *   // ... user speaks ...
 *   val wav = recorder.stopAndGetWav()   // send to /api/stt/transcribe
 */
class MicRecorder {

    private val format = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        16000f,    // 16 kHz — what Whisper expects
        16,        // 16-bit samples
        1,         // mono
        2,         // frame size = 2 bytes
        16000f,    // frame rate
        false,     // little-endian
    )

    private var line:   TargetDataLine? = null
    private var thread: Thread?         = null
    private val pcm     = ByteArrayOutputStream()
    private var running = false

    val isRecording: Boolean get() = running

    fun start(): Boolean {
        return try {
            val info = DataLine.Info(TargetDataLine::class.java, format)
            if (!AudioSystem.isLineSupported(info)) return false
            line = (AudioSystem.getLine(info) as TargetDataLine).also {
                it.open(format)
                it.start()
            }
            pcm.reset()
            running = true
            thread = Thread {
                val buf = ByteArray(1024)
                while (running) {
                    val n = line!!.read(buf, 0, buf.size)
                    if (n > 0) pcm.write(buf, 0, n)
                }
            }.also { it.isDaemon = true; it.start() }
            true
        } catch (_: Exception) { false }
    }

    /** Stop recording and return a complete WAV file as bytes. */
    fun stopAndGetWav(): ByteArray {
        running = false
        thread?.join(500)
        line?.stop()
        line?.close()
        line = null
        thread = null
        return buildWav(pcm.toByteArray())
    }

    private fun buildWav(pcmData: ByteArray): ByteArray {
        val out       = ByteArrayOutputStream()
        val dataLen   = pcmData.size
        val totalLen  = dataLen + 36

        fun le32(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte(),
            (v shr 16 and 0xFF).toByte(), (v shr 24 and 0xFF).toByte())
        fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte())

        out.write("RIFF".toByteArray())
        out.write(le32(totalLen))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(le32(16))               // chunk size
        out.write(le16(1))                // PCM
        out.write(le16(1))                // channels
        out.write(le32(16000))            // sample rate
        out.write(le32(16000 * 2))        // byte rate
        out.write(le16(2))                // block align
        out.write(le16(16))               // bits per sample
        out.write("data".toByteArray())
        out.write(le32(dataLen))
        out.write(pcmData)
        return out.toByteArray()
    }
}
