package com.genaro.radiomp3.ui.widgets

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import com.genaro.radiomp3.R

/**
 * Feedback Banner Widget
 * Displays real-time audio feedback messages with auto-dismiss
 * Positioned at top of player, shows:
 * - Buffering progress
 * - Format info
 * - Resampling warnings
 * - Errors
 */
class FeedbackBanner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val txtMessage: TextView
    private val imgIcon: ImageView
    private var autoDismissMillis: Long = 3000
    private var currentDismissAnimator: ObjectAnimator? = null

    enum class FeedbackType(val colorBg: Int, val colorText: Int, val icon: Int) {
        INFO(
            colorBg = 0xFF1E90FF.toInt(),     // Dodger Blue
            colorText = 0xFFFFFFFF.toInt(),    // White
            icon = android.R.drawable.ic_menu_info_details
        ),
        WARNING(
            colorBg = 0xFFFF9800.toInt(),     // Orange
            colorText = 0xFF000000.toInt(),    // Black
            icon = android.R.drawable.ic_dialog_alert
        ),
        ERROR(
            colorBg = 0xFFE53935.toInt(),     // Red
            colorText = 0xFFFFFFFF.toInt(),    // White
            icon = android.R.drawable.ic_dialog_alert
        ),
        SUCCESS(
            colorBg = 0xFF4CAF50.toInt(),     // Green
            colorText = 0xFFFFFFFF.toInt(),    // White
            icon = android.R.drawable.ic_menu_close_clear_cancel
        )
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.widget_feedback_banner, this, true)
        txtMessage = findViewById(R.id.txtFeedbackMessage)
        imgIcon = findViewById(R.id.imgFeedbackIcon)

        // Set elevation for shadow
        elevation = 8f
    }

    /**
     * Show feedback message with auto-dismiss
     */
    fun showFeedback(
        message: String,
        type: FeedbackType = FeedbackType.INFO,
        autoDismissMs: Long = 3000
    ) {
        autoDismissMillis = autoDismissMs

        // Cancel any pending dismiss
        currentDismissAnimator?.cancel()

        // Set content
        txtMessage.text = message
        imgIcon.setImageResource(type.icon)
        imgIcon.setColorFilter(type.colorText)

        // Set colors
        setBackgroundColor(type.colorBg)
        txtMessage.setTextColor(type.colorText)

        // Ensure visible
        alpha = 1f
        visibility = VISIBLE

        android.util.Log.d(
            "FeedbackBanner",
            "Showing ${type.name}: $message"
        )

        // Auto-dismiss if autoDismissMs > 0
        if (autoDismissMs > 0) {
            scheduleAutoDismiss(autoDismissMs)
        }
    }

    /**
     * Dismiss with fade animation
     */
    fun dismiss() {
        if (visibility != VISIBLE) return

        currentDismissAnimator?.cancel()

        val fadeOut = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f).apply {
            duration = 300
            doOnEnd {
                visibility = GONE
            }
        }
        fadeOut.start()
        currentDismissAnimator = fadeOut
    }

    /**
     * Auto-dismiss after delay
     */
    private fun scheduleAutoDismiss(delayMs: Long) {
        postDelayed({
            if (visibility == VISIBLE) {
                dismiss()
            }
        }, delayMs)
    }

    /**
     * Show buffering message with animated dots
     */
    fun showBuffering(currentPercent: Int = 0, downloadSpeed: String? = null) {
        val message = when {
            downloadSpeed != null -> "⏳ Buffering... $currentPercent% ($downloadSpeed)"
            else -> "⏳ Buffering... $currentPercent%"
        }
        showFeedback(message, FeedbackType.INFO, autoDismissMs = 0) // No auto-dismiss for buffering
    }

    /**
     * Show resampling warning
     */
    fun showResamplingWarning(inputHz: Int, outputHz: Int) {
        val message = "⚠️ Non bit-perfect: Risamplato da $inputHz → $outputHz kHz"
        showFeedback(message, FeedbackType.WARNING)
    }

    /**
     * Show format info
     */
    fun showFormatInfo(format: String, bitDepth: Int, sampleRate: Int) {
        val message = "✅ Riproducendo $format $bitDepth-bit/$sampleRate kHz"
        showFeedback(message, FeedbackType.SUCCESS)
    }

    /**
     * Show error
     */
    fun showError(errorMessage: String) {
        val message = "❌ $errorMessage"
        showFeedback(message, FeedbackType.ERROR)
    }

    /**
     * Show metadata loading
     */
    fun showLoading(what: String = "Caricamento") {
        val message = "⏳ $what..."
        showFeedback(message, FeedbackType.INFO, autoDismissMs = 0)
    }

    /**
     * Show USB audio device info
     */
    fun showUSBDeviceInfo(deviceName: String, maxHz: Int) {
        val message = "🎚️ USB DAC: $deviceName ($maxHz kHz)"
        showFeedback(message, FeedbackType.INFO)
    }
}
