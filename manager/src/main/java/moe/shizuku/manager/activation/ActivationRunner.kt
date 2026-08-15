package moe.shizuku.manager.activation

import android.os.ParcelFileDescriptor
import android.text.TextUtils
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Runs activation commands on the device through the running Shizaku server.
 *
 * The manager app is exempted from the server's permission enforcement
 * (checkCallerManagerPermission in the server matches the manager uid), so no
 * user-facing authorization is needed for these calls.
 */
object ActivationRunner {

    class Result(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean,
        val error: Throwable? = null
    ) {
        val success: Boolean
            get() = error == null && !timedOut && exitCode == 0
    }

    private fun service(): IShizukuService? {
        val binder = Shizuku.getBinder() ?: return null
        return IShizukuService.Stub.asInterface(binder)
    }

    /**
     * Executes `command` with `sh -c` on the server (shell uid for adb start,
     * root uid for root start) and collects stdout + stderr.
     *
     * @param timeoutSeconds the process is destroyed after this long; commands
     * like the Brevent bootstrap may keep running, in which case the output
     * gathered so far is still returned with [Result.timedOut].
     */
    fun run(command: String, timeoutSeconds: Int = 90): Result {
        val service = service() ?: return Result(-1, "", false, IllegalStateException("service not running"))

        return try {
            val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = StringBuilder()

            val stdoutThread = readStreamAsync(process.outputStream, output)
            val stderrThread = readStreamAsync(process.errorStream, output)

            var timedOut = false
            var exit = -1
            val exited = try {
                // waitForTimeout returns whether the process exited within the timeout
                process.waitForTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS.name)
            } catch (e: Exception) {
                // waitForTimeout unavailable on very old servers; fall back
                try {
                    process.waitFor()
                    true
                } catch (e2: Exception) {
                    false
                }
            }
            if (exited) {
                exit = try {
                    process.exitValue()
                } catch (e: Exception) {
                    -1
                }
            } else {
                timedOut = true
                process.destroy()
            }

            stdoutThread.join(3000)
            stderrThread.join(3000)

            Result(exit, output.toString().trim(), timedOut)
        } catch (e: Throwable) {
            Result(-1, "", false, e)
        }
    }

    private fun readStreamAsync(fd: ParcelFileDescriptor?, output: StringBuilder): Thread {
        val thread = Thread {
            fd ?: return@Thread
            try {
                BufferedReader(InputStreamReader(FileInputStream(fd.fileDescriptor))).use { reader ->
                    val local = StringBuilder()
                    var line: String?
                    while (true) {
                        line = reader.readLine() ?: break
                        local.append(line).append('\n')
                    }
                    synchronized(output) {
                        output.append(local)
                    }
                }
            } catch (e: Throwable) {
                // stream closed with the process
            } finally {
                try {
                    fd.close()
                } catch (e: Throwable) {
                }
            }
        }
        thread.isDaemon = true
        thread.start()
        return thread
    }

    /**
     * Whether [packageName] currently holds the device owner role, detected
     * from `dumpsys device_policy`.
     */
    fun isDeviceOwner(packageName: String): Boolean {
        if (!Shizuku.pingBinder()) return false
        if (TextUtils.isEmpty(packageName)) return false
        val result = run("dumpsys device_policy", timeoutSeconds = 20)
        if (result.error != null) return false
        // dumpsys lists owners like:
        //   Device Owner: web1n.stopapp/admin component or the package itself
        return result.output.split("\n").any {
            val l = it.trim()
            (l.startsWith("Device Owner") || l.startsWith("Profile Owner")) && l.contains(packageName)
        }
    }
}
