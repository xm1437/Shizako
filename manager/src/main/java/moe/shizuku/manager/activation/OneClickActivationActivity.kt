package moe.shizuku.manager.activation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicBoolean

class OneClickActivationActivity : AppBarActivity() {

    private class TargetRow(
        val target: ActivationTarget,
        val view: View,
        val button: Button,
        val statusView: TextView
    ) {
        @Volatile
        var installed: Boolean = true

        @Volatile
        var activated: Boolean? = null
    }

    private enum class OutputKind { PLAIN, COMMAND, SUCCESS, ERROR }

    private val running = AtomicBoolean(false)
    private val statusChecking = AtomicBoolean(false)
    private val rows = mutableListOf<TargetRow>()

    /** The button that started the running command, or null. */
    private var activeButton: Button? = null

    private lateinit var scrollView: NestedScrollView
    private lateinit var targetsContainer: LinearLayout
    private lateinit var outputView: TextView
    private lateinit var customInput: EditText
    private lateinit var customRun: Button
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var runningIndicator: View

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { onServiceStatusChanged(true) }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { onServiceStatusChanged(false) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Same back affordance as every other sub-page: the standard up arrow
        // in the top-left corner, handled by AppActivity.onSupportNavigateUp().
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(R.layout.activity_activation)

        scrollView = findViewById(R.id.scroll)
        targetsContainer = findViewById(R.id.targets_container)
        outputView = findViewById(R.id.output_view)
        customInput = findViewById(R.id.custom_command)
        customRun = findViewById(R.id.custom_run)
        statusDot = findViewById(R.id.status_dot)
        statusText = findViewById(R.id.status_text)
        runningIndicator = findViewById(R.id.running_indicator)

        customRun.setOnClickListener {
            val command = customInput.text?.toString()?.trim().orEmpty()
            if (command.isEmpty()) {
                Toast.makeText(this, R.string.activation_custom_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runCommand(getString(R.string.activation_custom_title), command, null, customRun)
        }
        findViewById<Button>(R.id.output_copy).setOnClickListener { copyOutput() }

        buildRows()

        // Live service status: sticky listener fires immediately when the
        // service is already running, then on every restart/stop.
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        refreshTargetStatuses()
    }

    override fun onResume() {
        super.onResume()
        onServiceStatusChanged(Shizuku.pingBinder())
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    private fun onServiceStatusChanged(serviceRunning: Boolean) {
        statusDot.background?.setTint(
            getColor(if (serviceRunning) R.color.activation_status_active else R.color.activation_status_inactive)
        )
        statusText.text = getString(
            if (serviceRunning) R.string.activation_service_running
            else R.string.activation_service_not_running
        )
        refreshButtonsEnabled()
        if (serviceRunning) {
            refreshTargetStatuses()
        }
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
                runCommand(target.label, target.command, target, button)
            }
            val row = TargetRow(
                target, view, button,
                view.findViewById(R.id.target_status)
            )
            rows.add(row)
            targetsContainer.addView(view)
        }
    }

    /**
     * Refreshes the per-target status (installed / activated). The local
     * package check is instant but can be filtered by package visibility on
     * Android 11+, so the server-side check refines it when possible.
     */
    private fun refreshTargetStatuses() {
        for (row in rows) {
            row.installed = isLocallyInstalled(row.target.packageName)
            updateRowStatus(row)
        }
        refreshButtonsEnabled()

        if (!Shizuku.pingBinder()) return
        if (!statusChecking.compareAndSet(false, true)) return

        Thread {
            for (row in rows) {
                val target = row.target
                try {
                    if (!row.installed) {
                        row.installed = ActivationRunner.isPackageInstalled(target.packageName)
                    }
                    if (row.installed && target.detection == ActivationTarget.Detection.DEVICE_OWNER) {
                        row.activated = ActivationRunner.isDeviceOwner(target.packageName)
                    }
                } catch (e: Throwable) {
                    // keep whatever was resolved so far
                }
            }
            runOnUiThread {
                statusChecking.set(false)
                for (row in rows) updateRowStatus(row)
                refreshButtonsEnabled()
            }
        }.start()
    }

    private fun isLocallyInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun updateRowStatus(row: TargetRow) {
        val res = when {
            !row.installed -> R.string.activation_status_not_installed
            row.activated == true -> R.string.activation_status_activated
            row.activated == false -> R.string.activation_status_not_activated
            else -> R.string.activation_status_installed
        }
        // The translated strings are "<app> · <status>"; strip the app name
        // prefix so only the status shows on the status line.
        val full = getString(res, row.target.label)
        val status = full.removePrefix(row.target.label)
            .trim(' ', '·', '・', '-', '—', ':')

        val text = SpannableString(status)
        val colorRes = when {
            !row.installed -> R.color.activation_status_inactive
            row.activated == true -> R.color.activation_status_active
            else -> 0
        }
        if (colorRes != 0) {
            text.setSpan(
                ForegroundColorSpan(getColor(colorRes)),
                0, text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        row.statusView.text = text
    }

    private fun runCommand(label: String, command: String, target: ActivationTarget?, clickedButton: Button) {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, R.string.activation_service_not_running, Toast.LENGTH_SHORT).show()
            return
        }
        if (!running.compareAndSet(false, true)) return

        activeButton = clickedButton
        refreshButtonsEnabled()
        runningIndicator.visibility = View.VISIBLE
        appendOutput(label, OutputKind.PLAIN)
        appendOutput("\$ $command", OutputKind.COMMAND)

        Thread {
            val result = ActivationRunner.run(command)
            runOnUiThread {
                running.set(false)
                appendOutput(result.output.ifEmpty { getString(R.string.activation_output_empty) })
                appendOutput("")

                when {
                    result.error != null -> {
                        appendOutput("[error] ${result.error.message}", OutputKind.ERROR)
                        Toast.makeText(this, R.string.activation_failed, Toast.LENGTH_SHORT).show()
                    }
                    result.timedOut -> {
                        Toast.makeText(this, R.string.activation_timeout, Toast.LENGTH_LONG).show()
                    }
                    result.success -> {
                        appendOutput("✓ ${getString(R.string.activation_success)}", OutputKind.SUCCESS)
                        Toast.makeText(this, R.string.activation_success, Toast.LENGTH_SHORT).show()
                        // The command may have just changed the device owner
                        // state, re-check the target statuses.
                        refreshTargetStatuses()
                    }
                    else -> {
                        appendOutput("[exit ${result.exitCode}]", OutputKind.ERROR)
                        Toast.makeText(
                            this,
                            getString(R.string.activation_failed_with_code, result.exitCode),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                activeButton = null
                runningIndicator.visibility = View.GONE
                refreshButtonsEnabled()
            }
        }.start()
    }

    private fun refreshButtonsEnabled() {
        val busy = running.get()
        val serviceRunning = Shizuku.pingBinder()
        for (row in rows) {
            row.button.setText(
                if (busy && row.button === activeButton) R.string.activation_running
                else R.string.activation_activate
            )
            row.button.isEnabled = !busy && serviceRunning && row.installed
        }
        customRun.setText(
            if (busy && customRun === activeButton) R.string.activation_running
            else R.string.activation_run
        )
        customRun.isEnabled = !busy && serviceRunning
    }

    private fun resolveThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun appendOutput(text: String, kind: OutputKind = OutputKind.PLAIN) {
        if (outputView.text.toString() == getString(R.string.activation_output_empty)) {
            outputView.text = ""
        }
        if (text.isEmpty()) {
            outputView.append("\n")
            return
        }

        val line = SpannableString(text + "\n")
        val color = when (kind) {
            OutputKind.COMMAND -> resolveThemeColor(android.R.attr.textColorSecondary)
            OutputKind.SUCCESS -> getColor(R.color.activation_output_success)
            OutputKind.ERROR -> getColor(R.color.activation_output_error)
            OutputKind.PLAIN -> 0
        }
        if (color != 0) {
            line.setSpan(
                ForegroundColorSpan(color),
                0, line.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        outputView.append(line)

        // Keep the newest output visible like a terminal would.
        scrollView.post {
            scrollView.smoothScrollTo(0, scrollView.getChildAt(0).bottom)
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
