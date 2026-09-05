package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.starter.ServiceStartHelper
import rikka.shizuku.Shizuku

/**
 * Broadcast receiver for automation apps (Tasker, MacroDroid, etc.).
 *
 * Supported actions:
 *   com.churan.shizako.action.START        — start with the last-used launch mode
 *   com.churan.shizako.action.STOP         — stop the Shizuku service
 *   com.churan.shizako.action.START_ROOT   — start with root
 *   com.churan.shizako.action.START_ADB    — start with ADB (wireless debugging)
 *
 * All actions are exported so Tasker can send intents to them directly.
 */
class TaskerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START = "com.churan.shizako.action.START"
        const val ACTION_STOP = "com.churan.shizako.action.STOP"
        const val ACTION_START_ROOT = "com.churan.shizako.action.START_ROOT"
        const val ACTION_START_ADB = "com.churan.shizako.action.START_ADB"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                ACTION_START -> {
                    if (Shizuku.pingBinder()) {
                        showToast(context, R.string.tasker_already_running)
                        return
                    }
                    when (ShizukuSettings.getLastLaunchMode()) {
                        ShizukuSettings.LaunchMethod.ROOT -> {
                            ServiceStartHelper.startRoot { ok ->
                                if (ok) showToast(context, R.string.tasker_started_root)
                                else showToast(context, R.string.tasker_start_failed)
                            }
                        }
                        ShizukuSettings.LaunchMethod.ADB -> {
                            if (ServiceStartHelper.canAdbAutoStart(context)) {
                                ServiceStartHelper.startAdb(context)
                                showToast(context, R.string.tasker_started_adb)
                            } else {
                                showToast(context, R.string.tasker_adb_unavailable)
                            }
                        }
                        else -> showToast(context, R.string.tasker_no_launch_mode)
                    }
                }
                ACTION_STOP -> {
                    if (!Shizuku.pingBinder()) {
                        showToast(context, R.string.tasker_not_running)
                        return
                    }
                    try {
                        Shizuku.exit()
                        showToast(context, R.string.tasker_stopped)
                    } catch (e: Exception) {
                        showToast(context, R.string.tasker_stop_failed)
                    }
                }
                ACTION_START_ROOT -> {
                    if (Shizuku.pingBinder()) {
                        showToast(context, R.string.tasker_already_running)
                        return
                    }
                    ServiceStartHelper.startRoot { ok ->
                        if (ok) showToast(context, R.string.tasker_started_root)
                        else showToast(context, R.string.tasker_start_failed)
                    }
                }
                ACTION_START_ADB -> {
                    if (Shizuku.pingBinder()) {
                        showToast(context, R.string.tasker_already_running)
                        return
                    }
                    if (ServiceStartHelper.canAdbAutoStart(context)) {
                        ServiceStartHelper.startAdb(context)
                        showToast(context, R.string.tasker_started_adb)
                    } else {
                        showToast(context, R.string.tasker_adb_unavailable)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(AppConstants.TAG, "TaskerReceiver: ${e.message}", e)
        }
    }

    private fun showToast(context: Context, resId: Int) {
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
    }
}