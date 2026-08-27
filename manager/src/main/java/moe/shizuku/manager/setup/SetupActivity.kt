package moe.shizuku.manager.setup

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.app.AppActivity
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.databinding.ItemSetupLanguageBinding
import moe.shizuku.manager.databinding.SetupActivityBinding
import moe.shizuku.manager.utils.EnvironmentUtils
import rikka.core.util.ResourceUtils
import rikka.material.app.LocaleDelegate
import rikka.shizuku.manager.ShizukuLocales
import java.util.Locale

/**
 * First-launch setup wizard: language and appearance.
 *
 * Shown from [moe.shizuku.manager.home.HomeActivity] until the user finishes
 * or skips it (persisted via [ShizukuSettings.SETUP_COMPLETED]). All choices
 * are written to the same preferences the Settings page uses, so everything
 * configured here is reflected there and vice versa.
 *
 * Both the language and the night mode changes recreate this activity; the
 * current step survives via [onSaveInstanceState].
 *
 * Step changes animate with the same Material motion feel as the rest of the
 * app (fade + slide on the standard fast-out-slow-in easing). After a
 * [recreate] the current step is restored without replaying animations.
 */
class SetupActivity : AppActivity() {

    companion object {
        private const val KEY_STEP = "setup_step"
        private const val KEY_DISCLAIMER_AGREED = "setup_disclaimer_agreed"
        private const val STEP_WELCOME = 0
        private const val STEP_DISCLAIMER = 1
        private const val STEP_LANGUAGE = 2
        private const val STEP_MODE = 3
        private const val STEP_START_METHOD = 4
        private const val STEP_COUNT = 5

        private const val STEP_ANIM_DURATION = 300L
        private const val ENTRANCE_STAGGER = 90L
    }

    private lateinit var binding: SetupActivityBinding
    private var step = STEP_WELCOME
    private var stepAnimating = false
    private var restoredFromRecreate = false
    private var languageAdapter: LanguageAdapter? = null
    private lateinit var materialInterpolator: Interpolator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        materialInterpolator = AnimationUtils.loadInterpolator(
            this, android.R.interpolator.fast_out_slow_in
        )
        restoredFromRecreate = savedInstanceState != null

        binding = SetupActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.languageList.layoutManager = LinearLayoutManager(this)
        languageAdapter = LanguageAdapter().also { binding.languageList.adapter = it }

        setupDisclaimerPage()
        setupModePage()
        setupStartMethodPage()

        step = savedInstanceState?.getInt(KEY_STEP, STEP_WELCOME) ?: STEP_WELCOME
        if (savedInstanceState != null) {
            binding.disclaimerAgree.isChecked =
                savedInstanceState.getBoolean(KEY_DISCLAIMER_AGREED, false)
        }
        showStep(step, animate = !restoredFromRecreate)

        binding.skipButton.setOnClickListener { finishSetup() }
        binding.backButton.setOnClickListener { navigateBack() }
        binding.nextButton.setOnClickListener { navigateForward() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_STEP, step)
        outState.putBoolean(KEY_DISCLAIMER_AGREED, binding.disclaimerAgree.isChecked)
    }

    private fun navigateBack() {
        if (stepAnimating || step <= STEP_WELCOME) return
        showStep(step - 1, animate = true)
    }

    private fun navigateForward() {
        if (stepAnimating) return
        if (step < STEP_START_METHOD) {
            showStep(step + 1, animate = true)
        } else {
            finishSetup()
        }
    }

    private fun pageView(step: Int): View = when (step) {
        STEP_WELCOME -> binding.pageWelcome
        STEP_DISCLAIMER -> binding.pageDisclaimer
        STEP_LANGUAGE -> binding.pageLanguage
        STEP_MODE -> binding.pageMode
        else -> binding.pageStartMethod
    }

    private val stepPages: List<View>
        get() = listOf(
            binding.pageWelcome, binding.pageDisclaimer, binding.pageLanguage,
            binding.pageMode, binding.pageStartMethod
        )

    /**
     * Switches to [newStep]. When [animate] is true the outgoing page slides
     * out while the incoming one slides in from the opposite side (direction
     * follows the navigation), matching the app-wide Material motion style.
     */
    private fun showStep(newStep: Int, animate: Boolean) {
        val forward = newStep >= step
        step = newStep

        val incoming = pageView(newStep)
        val outgoing = stepPages.firstOrNull { it !== incoming && it.isVisible }

        // The pages live stacked in a FrameLayout; only the active one is
        // VISIBLE, the rest INVISIBLE, so the weighted container stays put.
        stepPages.forEach {
            if (it !== incoming) it.visibility = View.INVISIBLE
        }

        if (!animate || outgoing == null) {
            incoming.visibility = View.VISIBLE
            incoming.alpha = 1f
            incoming.translationX = 0f
            updateChrome()
            if (newStep == STEP_WELCOME && !restoredFromRecreate) playWelcomeEntrance()
            return
        }

        val distance = dp(32f).toFloat()
        val sign = if (forward) 1 else -1

        stepAnimating = true

        outgoing.animate()
            .alpha(0f)
            .translationX(-distance * sign)
            .setDuration(STEP_ANIM_DURATION)
            .setInterpolator(materialInterpolator)
            .withEndAction {
                outgoing.translationX = 0f
                stepAnimating = false
            }

        incoming.visibility = View.VISIBLE
        incoming.alpha = 0f
        incoming.translationX = distance * sign
        incoming.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(STEP_ANIM_DURATION)
            .setInterpolator(materialInterpolator)

        updateChrome(animate)
    }

    /** Footer buttons reflect the current step, fading the back button in/out. */
    private fun updateChrome(animate: Boolean = false) {
        val showBack = step > STEP_WELCOME
        val showSkip = step < STEP_START_METHOD

        if (binding.backButton.isVisible != showBack) {
            if (animate) {
                binding.backButton.animate().alpha(if (showBack) 1f else 0f)
                    .setDuration(STEP_ANIM_DURATION)
                    .setInterpolator(materialInterpolator)
                    .withEndAction { binding.backButton.isVisible = showBack }
            } else {
                binding.backButton.isVisible = showBack
            }
        }
        binding.skipButton.isVisible = showSkip
        binding.nextButton.isEnabled = isNextEnabled()
        binding.nextButton.setText(
            if (step == STEP_START_METHOD) R.string.setup_get_started else R.string.setup_next
        )
    }

    /** The disclaimer step requires the agreement checkbox before continuing. */
    private fun isNextEnabled(): Boolean =
        step != STEP_DISCLAIMER || binding.disclaimerAgree.isChecked

    private fun pagesVisibleFirst(): List<View> =
        stepPages.filter { it.isVisible }

    /** Icon, title and subtitle rise into place one after another. */
    private fun playWelcomeEntrance() {
        val views = listOf(binding.welcomeIcon, binding.welcomeTitle, binding.welcomeSubtitle)
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = dp(24f).toFloat()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(STEP_ANIM_DURATION + 100L)
                .setStartDelay(index * ENTRANCE_STAGGER)
                .setInterpolator(materialInterpolator)
        }
    }

    private fun dp(value: Float): Int =
        (resources.displayMetrics.density * value).toInt()

    private fun finishSetup() {
        ShizukuSettings.setSetupCompleted(true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setupDisclaimerPage() {
        binding.disclaimerAgree.setOnCheckedChangeListener { _, _ ->
            // Gate the Next button (and its label) behind the agreement.
            binding.nextButton.isEnabled = isNextEnabled()
        }
    }

    /**
     * The activation-method step mirrors the start cards on the home page.
     * The choice is stored so the home page shows that method first; it is
     * purely a preference, the other methods remain available there.
     */
    private fun setupStartMethodPage() {
        // Preselect the most sensible default for this device when the user
        // has not chosen anything yet.
        var preferred = ShizukuSettings.getPreferredStartMethod()
        if (preferred == ShizukuSettings.StartMethod.UNSET) {
            preferred = when {
                EnvironmentUtils.isRooted() -> ShizukuSettings.StartMethod.ROOT
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    ShizukuSettings.StartMethod.WIRELESS_ADB
                else -> ShizukuSettings.StartMethod.COMPUTER_ADB
            }
        }

        val radios = mapOf(
            ShizukuSettings.StartMethod.WIRELESS_ADB to binding.methodWadbRadio,
            ShizukuSettings.StartMethod.ROOT to binding.methodRootRadio,
            ShizukuSettings.StartMethod.COMPUTER_ADB to binding.methodAdbRadio
        )
        radios[preferred]?.isChecked = true

        fun select(method: Int) {
            radios.entries.forEach { (m, radio) -> radio.isChecked = m == method }
            ShizukuSettings.setPreferredStartMethod(method)
        }

        binding.methodWadb.setOnClickListener { select(ShizukuSettings.StartMethod.WIRELESS_ADB) }
        binding.methodRoot.setOnClickListener { select(ShizukuSettings.StartMethod.ROOT) }
        binding.methodAdb.setOnClickListener { select(ShizukuSettings.StartMethod.COMPUTER_ADB) }
    }

    private fun onLanguageSelected(tag: String) {
        ShizukuSettings.getPreferences().edit()
            .putString(ShizukuSettings.LANGUAGE, tag)
            .apply()

        val locale: Locale = if ("SYSTEM" == tag) {
            LocaleDelegate.systemLocale
        } else {
            Locale.forLanguageTag(tag)
        }
        LocaleDelegate.defaultLocale = locale
        recreate()
    }

    private fun setupModePage() {
        val prefs = ShizukuSettings.getPreferences()

        val nightGroup = binding.nightModeGroup
        nightGroup.check(
            when (ShizukuSettings.getNightMode()) {
                AppCompatDelegate.MODE_NIGHT_NO -> R.id.mode_light
                AppCompatDelegate.MODE_NIGHT_YES -> R.id.mode_dark
                else -> R.id.mode_follow_system
            }
        )
        nightGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.mode_light -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.mode_dark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt(ShizukuSettings.NIGHT_MODE, mode).apply()

            // Changing the default night mode recreates this activity when the
            // configuration actually changes; restore the checked state from
            // the preference in onCreate (this method) then.
            AppCompatDelegate.setDefaultNightMode(mode)
            updateBlackNightVisibility()
        }
        updateBlackNightVisibility()

        binding.rowBlackNight.setOnClickListener { binding.switchBlackNight.toggle() }
        binding.switchBlackNight.isChecked = ThemeHelper.isBlackNightTheme(this)
        binding.switchBlackNight.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(ThemeHelper.KEY_BLACK_NIGHT_THEME, checked).apply()
            if (ResourceUtils.isNightMode(resources.configuration)) {
                recreate()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding.rowSystemColor.setOnClickListener { binding.switchSystemColor.toggle() }
            binding.switchSystemColor.isChecked = ThemeHelper.isUsingSystemColor()
            binding.switchSystemColor.setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(ThemeHelper.KEY_USE_SYSTEM_COLOR, checked).apply()
                recreate()
            }
        } else {
            binding.rowSystemColor.isVisible = false
        }
    }

    private fun updateBlackNightVisibility() {
        binding.rowBlackNight.isVisible =
            ShizukuSettings.getNightMode() != AppCompatDelegate.MODE_NIGHT_NO
    }

    private inner class LanguageAdapter :
        RecyclerView.Adapter<LanguageAdapter.Holder>() {

        private val tags = ShizukuLocales.LOCALES
        private val displayTags = ShizukuLocales.DISPLAY_LOCALES
        private var selected: String =
            ShizukuSettings.getPreferences().getString(ShizukuSettings.LANGUAGE, null)
                ?: "SYSTEM"

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemSetupLanguageBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun getItemCount() = tags.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val binding = holder.binding
            val tag = tags[position]
            binding.radio.isChecked = tag == selected

            val currentLocale = ShizukuSettings.getLocale()
            val title: CharSequence
            val summary: CharSequence
            if (position == 0) {
                // Same string the Settings language preference shows for the
                // "system" entry, so wording stays consistent with user edits.
                title = binding.root.context.getString(R.string.follow_system)
                summary = LocaleDelegate.systemLocale.getDisplayName(currentLocale)
            } else {
                val locale = Locale.forLanguageTag(displayTags[position])
                title = if (!TextUtils.isEmpty(locale.script)) {
                    locale.getDisplayScript(locale)
                } else {
                    locale.getDisplayName(locale)
                }
                summary = if (!TextUtils.isEmpty(locale.script)) {
                    locale.getDisplayScript(currentLocale)
                } else {
                    locale.getDisplayName(currentLocale)
                }
            }
            binding.title.text = title
            binding.summary.text = summary
            binding.summary.isVisible = !TextUtils.isEmpty(summary) && summary != title

            binding.root.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || tag == selected) return@setOnClickListener

                val oldIndex = tags.indexOf(selected)
                selected = tag
                if (oldIndex >= 0) notifyItemChanged(oldIndex)
                notifyItemChanged(pos)
                onLanguageSelected(tag)
            }
        }

        inner class Holder(val binding: ItemSetupLanguageBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
