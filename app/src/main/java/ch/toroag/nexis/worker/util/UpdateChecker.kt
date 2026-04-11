package ch.toroag.nexis.worker.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    data class Release(val tag: String, val versionTs: Long, val apkUrl: String, val apkSize: Long)

    /** True if this app is allowed to install APKs silently (Android 8+). */
    fun hasInstallPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.packageManager.canRequestPackageInstalls()
        else true   // pre-Oreo: always allowed if permission declared

    /**
     * Opens the system screen where the user can grant "Install unknown apps"
     * for this specific app. Only needed once.
     */
    fun openInstallPermissionSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                   Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Checks GitHub for a newer release.
     * Returns null if up-to-date, network fails, or no APK asset found.
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
        if (ts <= BuildConfig.VERSION_TIMESTAMP) return null
        val assets  = json.getJSONArray("assets")
        val asset   = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
        Release(
            tag       = tag,
            versionTs = ts,
            apkUrl    = asset.getString("browser_download_url"),
            apkSize   = asset.optLong("size", -1),
        )
    }.getOrNull()

    /**
     * Downloads the APK and reports progress (0–100).
     * Returns the downloaded file, or null on error.
     */
    fun downloadApk(
        context:         Context,
        release:         Release,
        onProgress:      (Int) -> Unit,
    ): File? = runCatching {
        val dir  = File(context.cacheDir, "apk").also { it.mkdirs() }
        val file = File(dir, "nexis-worker-${release.tag}.apk")

        val req  = Request.Builder().url(release.apkUrl).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) return null

        val totalBytes = if (release.apkSize > 0) release.apkSize
                         else resp.body!!.contentLength()
        var downloaded = 0L

        file.outputStream().use { out ->
            resp.body!!.byteStream().use { inp ->
                val buf = ByteArray(8192)
                var n: Int
                while (inp.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n)
                    downloaded += n
                    if (totalBytes > 0) {
                        onProgress((downloaded * 100 / totalBytes).toInt().coerceIn(0, 99))
                    }
                }
            }
        }
        onProgress(100)
        file
    }.getOrNull()

    /**
     * Installs an APK silently using PackageInstaller.
     *
     * Requires [hasInstallPermission] = true.
     * On success, Android automatically launches the new version — no user tap needed.
     * The result is broadcast to [InstallResultReceiver].
     */
    fun installSilently(context: Context, apkFile: File) {
        val installer = context.packageManager.packageInstaller
        val params    = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)

        val sessionId = installer.createSession(params)
        val session   = installer.openSession(sessionId)

        // Write APK bytes into the installer session
        session.openWrite("package", 0, apkFile.length()).use { out ->
            apkFile.inputStream().use { it.copyTo(out) }
            session.fsync(out)
        }

        // The install result is delivered to InstallResultReceiver
        val resultIntent = Intent(context, InstallResultReceiver::class.java).apply {
            action = InstallResultReceiver.ACTION
        }
        val pi = PendingIntent.getBroadcast(
            context, sessionId, resultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        session.commit(pi.intentSender)
        session.close()
    }
}
