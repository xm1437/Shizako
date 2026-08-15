package moe.shizuku.manager.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash log collector.
 *
 * Installs an [Thread.UncaughtExceptionHandler] that writes every uncaught
 * exception (the cause of most "crashes to launcher") to a plain text file
 * before the process dies, so users can review and share the log from
 * Settings -> Crash logs.
 */
object CrashLog {

    private const val TAG = "CrashLog"
    private const val DIR_NAME = "crash"
    private const val FILE_PREFIX = "crash-"
    private const val FILE_SUFFIX = ".txt"
    private const val MAX_FILES = 20

    private lateinit var appContext: Context
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        appContext = context.applicationContext

        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(thread, throwable)
            } catch (e: Throwable) {
                // Never let the logging itself crash the process
                Log.e(TAG, "Failed to write crash log", e)
            }
            // Chain to the system handler so the process still dies normally
            previousHandler?.uncaughtException(thread, throwable)
        }

        // Never let the logging itself crash the app (e.g. storage not ready in direct boot)
        runCatching { cleanup() }
    }

    /** Directory holding crash files, prefers external app dir, falls back to internal. */
    fun dir(): File {
        val external = appContext.getExternalFilesDir(DIR_NAME)
        return if (external != null) {
            external.apply { mkdirs() }
        } else {
            File(appContext.filesDir, DIR_NAME).apply { mkdirs() }
        }
    }

    /** All crash files, newest first. */
    fun files(): List<File> {
        return dir().listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) }
            ?.sortedByDescending { it.name }
            ?: emptyList()
    }

    fun hasFiles(): Boolean = files().isNotEmpty()

    fun deleteAll() {
        files().forEach { it.delete() }
    }

    private fun cleanup() {
        val files = files()
        if (files.size > MAX_FILES) {
            files.drop(MAX_FILES).forEach { it.delete() }
        }
    }

    private fun write(thread: Thread, throwable: Throwable) {
        val time = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(time))
        val file = File(dir(), "$FILE_PREFIX$stamp$FILE_SUFFIX")

        file.writeText(buildString {
            append(formatHeader(time, thread))
            append('\n')
            append(stackTraceOf(throwable))
        })

        Log.e(TAG, "Crash log saved to ${file.absolutePath}")
    }

    private fun formatHeader(time: Long, thread: Thread): String {
        val display = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(time))
        return buildString {
            appendLine("Time: $display")
            appendLine("Process: ${appContext.packageName} (pid ${android.os.Process.myPid()})")
            appendLine("Thread: ${thread.name}")
            try {
                val pm = appContext.packageManager
                val info = pm.getPackageInfo(appContext.packageName, 0)
                appendLine("Version: ${info.versionName} (${if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode})")
            } catch (e: Exception) {
                // ignore
            }
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.BRAND} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine()
            appendLine("---------------- stack trace ----------------")
        }
    }

    fun stackTraceOf(throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
