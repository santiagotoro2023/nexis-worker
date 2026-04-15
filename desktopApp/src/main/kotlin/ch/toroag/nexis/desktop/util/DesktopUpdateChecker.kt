package ch.toroag.nexis.desktop.util

import ch.toroag.nexis.desktop.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object DesktopUpdateChecker {

    private const val REPO = "santiagotoro2023/nexis-worker"
    private const val API  = "https://api.github.com/repos/$REPO/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    data class Release(val tag: String, val versionTs: Long, val debUrl: String, val debSize: Long)

    /**
     * Checks GitHub for a newer release that has a .deb asset.
     * Returns null if up-to-date, the network is unreachable, or no .deb found.
     */
    fun checkForUpdate(): Release? = runCatching {
        val req  = Request.Builder().url(API)
            .addHeader("Accept", "application/vnd.github+json")
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) return null
        val json = JSONObject(resp.body!!.string())
        val tag  = json.getString("tag_name")           // e.g. "v1744999200"
        val ts   = tag.trimStart('v').toLongOrNull() ?: return null
        if (BuildConfig.VERSION_TIMESTAMP > 0L && ts <= BuildConfig.VERSION_TIMESTAMP) return null
        val assets = json.getJSONArray("assets")
        val asset  = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".deb") } ?: return null
        Release(
            tag       = tag,
            versionTs = ts,
            debUrl    = asset.getString("browser_download_url"),
            debSize   = asset.optLong("size", -1),
        )
    }.getOrNull()

    /**
     * Downloads the .deb and reports progress (0–100).
     * Returns the downloaded file, or null on error.
     */
    fun downloadDeb(
        release:    Release,
        onProgress: (Int) -> Unit,
    ): File? = runCatching {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "nexis-updates").also { it.mkdirs() }
        val file   = File(tmpDir, "nexis-worker-desktop-${release.tag}.deb")

        val req  = Request.Builder().url(release.debUrl).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) return null

        val total = if (release.debSize > 0) release.debSize else resp.body!!.contentLength()
        var done  = 0L

        file.outputStream().use { out ->
            resp.body!!.byteStream().use { inp ->
                val buf = ByteArray(8192)
                var n: Int
                while (inp.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done * 100 / total).toInt().coerceIn(0, 99))
                }
            }
        }
        onProgress(100)
        file
    }.getOrNull()

    /**
     * Installs the .deb using pkexec (PolicyKit) — prompts the user for their
     * password via the desktop auth dialog. Blocks until dpkg finishes.
     *
     * Returns true on success (exit code 0).
     */
    fun installDeb(debFile: File): Boolean {
        return try {
            val proc = ProcessBuilder("pkexec", "dpkg", "-i", debFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val exitCode = proc.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            // Fallback: try gksudo / kdesudo if pkexec not found
            runCatching {
                val proc = ProcessBuilder("gksudo", "dpkg", "-i", debFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                proc.waitFor() == 0
            }.getOrDefault(false)
        }
    }
}
