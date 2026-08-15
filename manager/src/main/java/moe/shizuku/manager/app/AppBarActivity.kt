package moe.shizuku.manager.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.AppBarLayout
import moe.shizuku.manager.R
import moe.shizuku.manager.utils.FrostDrawable
import rikka.core.ktx.unsafeLazy

abstract class AppBarActivity : AppActivity() {

    private val rootView: ViewGroup by unsafeLazy {
        findViewById<ViewGroup>(R.id.root)
    }

    private val toolbarContainer: AppBarLayout by unsafeLazy {
        findViewById<AppBarLayout>(R.id.toolbar_container)
    }

    private val toolbar: Toolbar by unsafeLazy {
        findViewById<Toolbar>(R.id.toolbar)
    }

    companion object {

        /** Decoded once per process; ~2.5MB at 540x1138. */
        private var frostBitmap: android.graphics.Bitmap? = null

        // Slightly stronger than the window background's #B3 white so the bar reads as frosted glass.
        private const val FROST_SCRIM = 0xC8FFFFFF.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.setContentView(getLayoutId())

        setSupportActionBar(toolbar)
        applyFrostBackground()
    }

    private fun applyFrostBackground() {
        val bmp = frostBitmap ?: BitmapFactory.decodeResource(
            resources, R.drawable.bg_wallpaper_blur
        )?.also { frostBitmap = it } ?: return

        val frost = FrostDrawable(bmp, FROST_SCRIM)
        toolbarContainer.background = frost

        // The wallpaper is stretched to the whole window; keep the blurred copy
        // aligned with it as the window (root view) size changes.
        rootView.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            frost.windowWidth = right - left
            frost.windowHeight = bottom - top
        }
        frost.windowWidth = rootView.width
        frost.windowHeight = rootView.height
    }

    @LayoutRes
    open fun getLayoutId(): Int {
        return R.layout.appbar_activity
    }

    override fun setContentView(layoutResID: Int) {
        layoutInflater.inflate(layoutResID, rootView, true)
        rootView.bringChildToFront(toolbarContainer)
    }

    override fun setContentView(view: View?) {
        setContentView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        rootView.addView(view, 0, params)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onApplyTranslucentSystemBars() {
        super.onApplyTranslucentSystemBars()
        window?.statusBarColor = Color.TRANSPARENT
    }
}

abstract class AppBarFragmentActivity : AppBarActivity() {

    override fun getLayoutId(): Int {
        return R.layout.appbar_fragment_activity
    }
}
