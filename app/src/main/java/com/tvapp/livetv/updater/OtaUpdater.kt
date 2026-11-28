package com.tvapp.livetv.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tvapp.livetv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class ReleaseInfo(
    val versionName: String,
    val apkDownloadUrl: String,
    val releaseNotes: String
)

object OtaUpdater {
    private val client = OkHttpClient()

    suspend fun checkForUpdate(
        owner: String = BuildConfig.GITHUB_OWNER,
        repo: String = BuildConfig.GITHUB_REPO,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): ReleaseInfo? = withContext(Dispatchers.IO) {
        if (owner == "YOUR_GITHUB_USERNAME") return@withContext null

        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body?.string().orEmpty())
            val tagName = json.optString("tag_name").removePrefix("v").trim()
            if (tagName.isBlank() || !isNewerVersion(tagName, currentVersion)) {
                return@withContext null
            }

            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            var fallbackApkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    val downloadUrl = asset.optString("browser_download_url")
                    if (!name.contains("debug", ignoreCase = true)) {
                        apkUrl = downloadUrl
                        break
                    } else if (fallbackApkUrl == null) {
                        fallbackApkUrl = downloadUrl
                    }
                }
            }
            val resolvedUrl = apkUrl ?: fallbackApkUrl ?: return@withContext null

            ReleaseInfo(
                versionName = tagName,
                apkDownloadUrl = resolvedUrl,
                releaseNotes = json.optString("body", "New update available.")
            )
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "update.apk")

        val request = Request.Builder().url(apkUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("APK download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty APK response")
            val totalBytes = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            val pct = ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            withContext(Dispatchers.Main) { onProgress(pct) }
                        }
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        }
    }

    fun canInstallApk(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val lParts = local.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
