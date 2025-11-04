package com.genaro.radiomp3.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.genaro.radiomp3.data.Prefs

abstract class BaseActivity : AppCompatActivity() {

    private var lastUIModeWasAutoHide: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set window colors to match app background FIRST
        window.statusBarColor = android.graphics.Color.parseColor("#000000")
        window.navigationBarColor = android.graphics.Color.parseColor("#000000")

        // Allow layout to extend under system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Apply or remove immersive mode based on preference
        applySystemUIMode()
    }

    protected fun setupImmersiveMode(tapAreaView: View) {
        // Immersive mode already applied in onCreate, this is just for reference
        // Keeping this method for backward compatibility
    }

    private fun applySystemUIMode() {
        val currentAutoHide = Prefs.autoHide(this)

        // Only apply changes if preference changed (avoid stuttering during transitions)
        if (lastUIModeWasAutoHide != currentAutoHide) {
            if (currentAutoHide) {
                hideSystemUI()
            } else {
                showSystemUI()
            }
            lastUIModeWasAutoHide = currentAutoHide
        }
    }

    private fun hideSystemUI() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun showSystemUI() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        // Re-apply system UI mode when returning to activity
        applySystemUIMode()
    }
}
