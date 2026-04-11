package ch.toroag.nexis.worker.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ch.toroag.nexis.worker.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val REPO = "santiagotoro2023/nexis-worker"
    private const val API  = "https://api.github.com/repos/$REPO/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class Release(val tag: String, val versionTs: Long, val apkUrl: String)

    /** Returns a Release if newer than the running build, else null. */
    fun checkForUpdate(): Release? = runCatching {
        val req  = Request.Builder().url(API).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) return null
        val json = JSONObject(resp.body!!.string())
        val tag  = json.getString("tag_name")              // e.g. "v1744999200"
        val ts   = tag.trimStart('v').toLongOrNull() ?: return null
        if (ts <= BuildConfig.VERSION_TIMESTAMP) return null
        // Find APK asset URL
        val assets = json.getJSONArray("assets")
        val apkUrl = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?.getString("browser_download_url") ?: return null
        Release(tag, ts, apkUrl)
    }.getOrNull()

    /** Downloads the APK and triggers the Android install prompt. */
    fun downloadAndInstall(context: Context, release: Release) {
        val apkDir  = File(context.cacheDir, "apk").also { it.mkdirs() }
        val apkFile = File(apkDir, "nexis-worker-${release.tag}.apk")

        // Download
        val req  = Request.Builder().url(release.apkUrl).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) return
        apkFile.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }

        // Trigger install
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
