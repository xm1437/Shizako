package moe.shizuku.manager.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import moe.shizuku.manager.R
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val GITHUB_API = "https://api.github.com/repos/xm1437/Shizako/releases/latest"
    private const val CHANNEL_ID = "shizako_update"
    private const val NOTIFY_ID = 1001

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

    data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String,
        val apkUrl: String,
        val apkSize: Long
    )

    fun checkForUpdate(context: Context, onResult: (ReleaseInfo?) -> Unit) {
        scope.launch {
            try {
                val info = fetchLatestRelease()
                if (info != null) {
                    val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0)
                    val currentLabel = currentVersion.versionName ?: ""
                    if (info.tagName != currentLabel) {
                        withContext(Dispatchers.Main) { onResult(info) }
                    } else {
                        withContext(Dispatchers.Main) { onResult(null) }
                    }
                } else {
                    withContext(Dispatchers.Main) { onResult(null) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.update_check_failed, e.message), Toast.LENGTH_SHORT).show()
                    onResult(null)
                }
            }
        }
    }

    fun checkAndNotify(context: Context) {
        checkForUpdate(context) { info ->
            if (info != null) {
                showUpdateNotification(context, info)
            }
        }
    }

    fun downloadAndInstall(context: Context, info: ReleaseInfo) {
        if (downloadJob?.isActive == true) return

        // Register notification channel
        NotificationManagerCompat.from(context).createNotificationChannel(
            android.app.NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                android.app.NotificationManager.IMPORTANCE_LOW
            )
        )

        downloadJob = scope.launch {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.update_downloading, Toast.LENGTH_SHORT).show()
            }
            try {
                val apkFile = downloadApk(context, info.apkUrl, info.tagName)
                withContext(Dispatchers.Main) { installApk(context, apkFile) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.update_download_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val conn = URL(GITHUB_API).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000

        if (conn.responseCode != 200) return null

        val json = JSONObject(conn.inputStream.bufferedReader().readText())
        conn.disconnect()

        val assets = json.getJSONArray("assets")
        var apkUrl = ""
        var apkSize = 0L
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            if (name.endsWith(".apk") && name.contains("release")) {
                apkUrl = asset.getString("browser_download_url")
                apkSize = asset.getLong("size")
                break
            }
        }
        // Fallback to first APK asset
        if (apkUrl.isEmpty() && assets.length() > 0) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    apkSize = asset.getLong("size")
                    break
                }
            }
        }

        if (apkUrl.isEmpty()) return null

        return ReleaseInfo(
            tagName = json.getString("tag_name"),
            name = json.optString("name", json.getString("tag_name")),
            body = json.optString("body", ""),
            apkUrl = apkUrl,
            apkSize = apkSize
        )
    }

    private fun downloadApk(context: Context, url: String, tag: String): File {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        val apkFile = File(dir, "shizako-$tag.apk")

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/octet-stream")
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000

        val total = conn.contentLengthLong
        val input = conn.inputStream
        val output = FileOutputStream(apkFile)

        val buffer = ByteArray(8192)
        var downloaded = 0L
        var bytesRead: Int
        var lastProgress = 0L

        val handler = Handler(Looper.getMainLooper())

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            downloaded += bytesRead
            if (total > 0 && downloaded - lastProgress > total / 20) {
                lastProgress = downloaded
                val pct = (downloaded * 100 / total).toInt()
                handler.post {
                    Toast.makeText(context, context.getString(R.string.update_downloading_progress, pct), Toast.LENGTH_SHORT).show()
                }
            }
        }
        output.close()
        input.close()
        conn.disconnect()

        return apkFile
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update_file_provider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.update_install_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun showUpdateNotification(context: Context, info: ReleaseInfo) {
        val installIntent = Intent(context, context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.className?.let {
            Class.forName(it)
        } ?: return).apply {
            putExtra("check_update", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_system_icon)
            .setContentTitle(context.getString(R.string.update_available_title))
            .setContentText(context.getString(R.string.update_available_text, info.tagName))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.update_available_text, info.tagName)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification)
        } catch (_: SecurityException) { }
    }
}