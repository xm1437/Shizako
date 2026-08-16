package moe.shizuku.manager.activation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

class OneClickActivationActivity : AppBarActivity() {

    private class TargetRow(
        val target: ActivationTarget,
        val view: View,
        val button: Button
    )

    private val running = AtomicBoolean(false)
    private val rows = mutableListOf<TargetRow>()

    private lateinit var targetsContainer: LinearLayout
    private lateinit var outputView: TextView
    private lateinit var customInput: EditText
    private lateinit var customRun: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_activation)

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
        val serviceRunning = Shizuku.pingBinder()
        for (row in rows) {
            row.button.isEnabled = serviceRunning
        }
        customRun.isEnabled = serviceRunning
    }

    private fun buildRows() {
        val inflater = LayoutInflater.from(this)
        for (target in ActivationTargets.ALL) {
            val view = inflater.inflate(R.layout.item_activation_target, targetsContainer, false)
            view.findViewById<TextView>(R.id.target_label).text = target.label
            view.findViewById<TextView>(R.id.target_notes).text = when (target.notesRes) {
                0 -> getString(R.string.activation_note_brevent)
                else -> getString(R.string.activation_note_device_owner)
            }
            val button = view.findViewById<Button>(R.id.target_activate)
            button.setText(R.string.activation_activate)
            button.setOnClickListener {
                runCommand(target.label, target.command, target)
            }
            rows.add(TargetRow(target, view, button))
            targetsContainer.addView(view)
        }
    }

    private fun runCommand(label: String, command: String, target: ActivationTarget?) {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, R.string.activation_service_not_running, Toast.LENGTH_SHORT).show()
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
                setButtonsEnabled(true)
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