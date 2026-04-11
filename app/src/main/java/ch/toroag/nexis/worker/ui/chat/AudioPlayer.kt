package ch.toroag.nexis.worker.ui.chat

import android.media.MediaPlayer
import ch.toroag.nexis.worker.data.NexisApiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.resume

class AudioPlayer(
    private val api:     NexisApiService,
    private val baseUrl: String,
    private val token:   String,
    private val cacheDir: File,
) {
    private val queue  = LinkedBlockingQueue<Int>()
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var current: MediaPlayer? = null

    init {
        scope.launch { processQueue() }
    }

    fun enqueue(chunkId: Int) { queue.offer(chunkId) }

    fun stop() {
        current?.runCatching { stop(); release() }
        current = null
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private suspend fun processQueue() {
        while (true) {
            val id  = withContext(Dispatchers.IO) { queue.take() }
            val wav = api.fetchAudioChunk(baseUrl, token, id) ?: continue
            playWav(wav, id)
        }
    }

    private suspend fun playWav(wav: ByteArray, id: Int) {
        val tmpFile = File(cacheDir, "nexis_audio_$id.wav")
        tmpFile.writeBytes(wav)
        suspendCancellableCoroutine<Unit> { cont ->
            val player = MediaPlayer()
            current = player
            try {
                player.setDataSource(tmpFile.absolutePath)
                player.prepare()
                player.setOnCompletionListener {
                    tmpFile.delete()
                    player.release()
                    current = null
                    if (cont.isActive) cont.resume(Unit)
                }
                player.setOnErrorListener { _, _, _ ->
                    tmpFile.delete()
                    player.release()
                    current = null
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                player.start()
            } catch (e: Exception) {
                tmpFile.delete()
                runCatching { player.release() }
                current = null
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation {
                tmpFile.delete()
                runCatching { player.stop(); player.release() }
                current = null
            }
        }
    }
}
