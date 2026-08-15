package moe.shizuku.manager.activation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-click activation for apps that normally require `adb shell` commands
 * from a computer (Brevent, Stopapp, Ice Box, Island, or any custom command).
 *
 * Commands run through the local Shizaku server via IShizukuService#newProcess;
 * the manager uid is exempted from the server permission enforcement, so no
 * extra authorization dialog is needed.
 */
class OneClickActivationActivity : AppBarActivity() {

    private class TargetRow(
        val target: ActivationTarget,
        val view: View,
        val status: TextView,
        val button: Button
    )

    private val running = AtomicBoolean(false)
    private val rows = mutableListOf<TargetRow>()

    private lateinit var banner: TextView
    private lateinit var targetsContainer: LinearLayout
    private lateinit var outputView: TextView
    private lateinit var customInput: EditText
    private lateinit var customRun: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activation)

        banner = findViewById(R.id.service_banner)
        targetsContainer = findViewById(R.id.targets_container)
        outputView = findViewById(R.id.output_view)
        customInput = findViewById(R.id.custom_command)
        customRun = findViewById(R.id.custom_run)

        customRun.setOnClickListener {
            val command = customInput.text?.toString()?.trim().orEmpty()
            if (command.isEmpty()) {
                Toast.makeText(this, R.string.activation_custom_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runCommand(getString(R.string.activation_custom_title), command, null)
        }
        findViewById<Button>(R.id.output_copy).setOnClickListener { copyOutput() }

        buildRows()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun buildRows() {
        val inflater = LayoutInflater.from(this)
        for (target in ActivationTargets.ALL) {
            val view = inflater.inflate(R.layout.item_activation_target, targetsContainer, false)
            view.findViewById<TextView>(R.id.target_label).text = target.label
            val notes = view.findViewById<TextView>(R.id.target_notes)
            notes.text = when (target.notesRes) {
                0 -> getString(R.string.activation_note_brevent)
                else -> getString(R.string.activation_note_device_owner)
            }
            val row = TargetRow(
                target,
                view,
                view.findViewById(R.id.target_status),
                view.findViewById<Button>(R.id.target_activate)
            )
            row.button.setOnClickListener {
                runCommand(target.label, target.command, target)
            }
            rows.add(row)
            targetsContainer.addView(view)
        }
    }

    /** Re-checks service status, installation and activation state of every target. */
    private fun refreshState() {
        val serviceRunning = Shizuku.pingBinder()
        val uid = if (serviceRunning) {
            try { Shizuku.getUid() } catch (e: Throwable) { -1 }
        } else -1

        banner.text = if (serviceRunning) {
            getString(R.string.activation_service_running, uid)
        } else {
            getString(R.string.activation_service_not_running)
        }

        for (row in rows) {
            val installed = isInstalled(row.target.packageName)
            when {
                !installed -> {
                    row.status.text = getString(R.string.activation_status_not_installed, row.target.packageName)
                    row.button.isEnabled = serviceRunning
                    row.button.setText(R.string.activation_activate)
                }
                row.target.detection == ActivationTarget.Detection.DEVICE_OWNER && serviceRunning -> {
                    row.status.setText(R.string.activation_status_checking)
                    row.button.isEnabled = false
                }
                else -> {
                    row.status.text = getString(R.string.activation_status_installed, row.target.packageName)
                    row.button.isEnabled = serviceRunning
                    row.button.setText(R.string.activation_activate)
                }
            }
        }

        if (!serviceRunning) return

        // Device owner checks go through the server, do them off the main thread.
        Thread {
            val owners = rows.associate { row ->
                row.target to (row.target.detection == ActivationTarget.Detection.DEVICE_OWNER &&
                        ActivationRunner.isDeviceOwner(row.target.packageName))
            }
            runOnUiThread {
                for (row in rows) {
                    if (row.target.detection != ActivationTarget.Detection.DEVICE_OWNER) continue
                    val owner = owners[row.target] == true
                    if (owner) {
                        row.status.text = getString(R.string.activation_status_activated, row.target.packageName)
                        row.button.isEnabled = false
                        row.button.setText(R.string.activation_activated)
                    } else {
                        row.status.text = getString(R.string.activation_status_not_activated, row.target.packageName)
                        row.button.isEnabled = !running.get()
                        row.button.setText(R.string.activation_activate)
                    }
                }
            }
        }.start()
    }

    private fun isInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun runCommand(label: String, command: String, target: ActivationTarget?) {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, R.string.activation_service_not_running, Toast.LENGTH_SHORT).show()
            refreshState()
            return
        }
        if (!running.compareAndSet(false, true)) return

        setButtonsEnabled(false)
        appendOutput("$label\n$ $command\n")

        Thread {
            val result = ActivationRunner.run(command)
            runOnUiThread {
                running.set(false)
                appendOutput(result.output.ifEmpty { getString(R.string.activation_output_empty) })
                appendOutput("")

                when {
                    result.error != null -> {
                        appendOutput("[error] ${result.error.message}")
                        Toast.makeText(this, R.string.activation_failed, Toast.LENGTH_SHORT).show()
                    }
                    result.timedOut -> {
                        Toast.makeText(this, R.string.activation_timeout, Toast.LENGTH_LONG).show()
                    }
                    result.success -> {
                        Toast.makeText(this, R.string.activation_success, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        appendOutput("[exit ${result.exitCode}]")
                        Toast.makeText(
                            this,
                            getString(R.string.activation_failed_with_code, result.exitCode),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                refreshState()
            }
        }.start()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        for (row in rows) row.button.isEnabled = enabled
        customRun.isEnabled = enabled
    }

    private fun appendOutput(text: String) {
        if (outputView.text.toString() == getString(R.string.activation_output_empty)) {
            outputView.text = text
        } else {
            outputView.append(if (text.isEmpty()) "\n" else text + "\n")
        }
    }

    private fun copyOutput() {
        val text = outputView.text.toString()
        if (text.isEmpty()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("activation", text))
        Toast.makeText(this, R.string.activation_copied, Toast.LENGTH_SHORT).show()
    }
}
