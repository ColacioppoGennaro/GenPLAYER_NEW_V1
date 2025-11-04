package com.genaro.radiomp3.ui.vu

import android.content.res.Configuration
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.genaro.radiomp3.R
import kotlin.math.max
import kotlin.math.min

/**
 * Controller VU Meter panel con drag verticale (portrait) e fullscreen (landscape)
 */
class VuMeterPanelController(
    private val panelView: View,
    private val parentView: ViewGroup,
    private val onClose: () -> Unit,
    private val onOptions: () -> Unit
) {

    private var initialY = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var dragEnabled = false
    private var buttonsVisible = false

    init {
        val closeBtn = panelView.findViewById<TextView>(R.id.btnVuClose)
        val optionsBtn = panelView.findViewById<TextView>(R.id.btnVuOptions)
        val moveBtn = panelView.findViewById<TextView>(R.id.btnVuMove)
        val buttonContainer = panelView.findViewById<ViewGroup>(R.id.buttonContainer)

        // Hide buttons by default
        buttonContainer.visibility = View.GONE

        closeBtn.setOnClickListener {
            android.util.Log.d("VU_DEBUG", "Close button clicked")
            onClose()
        }

        optionsBtn.setOnClickListener {
            android.util.Log.d("VU_DEBUG", "Options button clicked")
            onOptions()
        }

        moveBtn.setOnClickListener {
            android.util.Log.d("VU_DEBUG", "Move button clicked - enabling drag mode")
            dragEnabled = true
            buttonContainer.visibility = View.GONE
            buttonsVisible = false

            // Toast per feedback
            android.widget.Toast.makeText(
                panelView.context,
                "Trascina il pannello su/giù",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        // Click normale - toggle buttons
        panelView.setOnClickListener {
            android.util.Log.d("VU_DEBUG", "Panel clicked - toggling buttons")
            toggleButtons()
        }

        // Touch per gestire il drag quando è abilitato
        panelView.setOnTouchListener { _, event ->
            if (dragEnabled) {
                handleDragTouch(event)
            } else {
                false // Lascia passare i touch events ai listener sopra
            }
        }
    }

    private fun toggleButtons() {
        val buttonContainer = panelView.findViewById<ViewGroup>(R.id.buttonContainer)
        buttonsVisible = !buttonsVisible
        buttonContainer.visibility = if (buttonsVisible) View.VISIBLE else View.GONE
    }

    private fun handleDragTouch(event: MotionEvent): Boolean {
        // Only drag in portrait
        val orientation = panelView.context.resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (dragEnabled) {
                    isDragging = true
                    initialY = panelView.translationY
                    initialTouchY = event.rawY
                    android.util.Log.d("VU_DEBUG", "Drag started - initialY=$initialY, initialTouchY=$initialTouchY")
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaY = event.rawY - initialTouchY
                    val newY = initialY + deltaY

                    // Consenti movimento sia SU che GIÙ
                    val minY = -(panelView.height * 0.8f)  // Può andare su (nascondersi quasi completamente)
                    val maxY = parentView.height - panelView.height / 3f  // Può andare giù
                    panelView.translationY = min(maxY, max(minY, newY))
                    android.util.Log.d("VU_DEBUG", "Dragging - deltaY=$deltaY, newY=$newY, translationY=${panelView.translationY}")
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    dragEnabled = false  // Reset drag mode after release

                    android.util.Log.d("VU_DEBUG", "Drag ended - translationY=${panelView.translationY}")

                    // Close if dragged beyond thresholds, otherwise stay where released
                    if (panelView.translationY < -(panelView.height / 3)) {
                        // Chiudi se trascinato SU più di 1/3 dell'altezza
                        android.util.Log.d("VU_DEBUG", "Dragged UP - closing panel")
                        hide()
                        onClose()
                    } else if (panelView.translationY > panelView.height / 2) {
                        // Chiudi se trascinato GIÙ più di metà
                        android.util.Log.d("VU_DEBUG", "Dragged DOWN - closing panel")
                        hide()
                        onClose()
                    } else {
                        // RIMANI dove sei stato lasciato - NON tornare a 0
                        android.util.Log.d("VU_DEBUG", "Panel released at translationY=${panelView.translationY} - staying in place")
                        // Nessuna animazione - il panel resta dove è
                    }
                    return true
                }
            }
        }
        return false
    }

    fun show() {
        val orientation = panelView.context.resources.configuration.orientation
        val vuMeterView = panelView.findViewById<View>(R.id.vuMeterView)

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Fullscreen in landscape - modify container too
            val containerParams = parentView.layoutParams as android.widget.RelativeLayout.LayoutParams
            containerParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            containerParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            parentView.layoutParams = containerParams

            val params = panelView.layoutParams as FrameLayout.LayoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            panelView.layoutParams = params

            // VU meter view to match_parent in landscape
            val vuParams = vuMeterView.layoutParams as FrameLayout.LayoutParams
            vuParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            vuMeterView.layoutParams = vuParams
        } else {
            // Normal height in portrait - restore container
            val containerParams = parentView.layoutParams as android.widget.RelativeLayout.LayoutParams
            containerParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            containerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            parentView.layoutParams = containerParams

            val params = panelView.layoutParams as FrameLayout.LayoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            panelView.layoutParams = params

            // VU meter view to 160dp in portrait (2 button heights)
            val vuParams = vuMeterView.layoutParams as FrameLayout.LayoutParams
            vuParams.height = (160 * panelView.context.resources.displayMetrics.density).toInt()
            vuMeterView.layoutParams = vuParams
        }

        // Reset position only if panel was previously hidden
        if (panelView.visibility != View.VISIBLE) {
            panelView.translationY = 0f
        }

        panelView.visibility = View.VISIBLE
        panelView.alpha = 1f
    }

    fun hide() {
        // Restore container to bottom position
        val containerParams = parentView.layoutParams as android.widget.RelativeLayout.LayoutParams
        containerParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
        containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        containerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        parentView.layoutParams = containerParams

        panelView.visibility = View.GONE
        panelView.translationY = 0f  // Reset position when hiding
        dragEnabled = false
        isDragging = false
        buttonsVisible = false
        val buttonContainer = panelView.findViewById<ViewGroup>(R.id.buttonContainer)
        buttonContainer.visibility = View.GONE
    }

    fun updateOrientation() {
        val orientation = panelView.context.resources.configuration.orientation
        val vuMeterView = panelView.findViewById<View>(R.id.vuMeterView)

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Fullscreen in landscape
            val containerParams = parentView.layoutParams as android.widget.RelativeLayout.LayoutParams
            containerParams.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            containerParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            parentView.layoutParams = containerParams

            val params = panelView.layoutParams as FrameLayout.LayoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            panelView.layoutParams = params

            // VU meter view to match_parent in landscape
            val vuParams = vuMeterView.layoutParams as FrameLayout.LayoutParams
            vuParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            vuMeterView.layoutParams = vuParams
        } else {
            // Normal height in portrait
            val containerParams = parentView.layoutParams as android.widget.RelativeLayout.LayoutParams
            containerParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
            containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            containerParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            parentView.layoutParams = containerParams

            val params = panelView.layoutParams as FrameLayout.LayoutParams
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            panelView.layoutParams = params

            // VU meter view to 160dp in portrait (2 button heights)
            val vuParams = vuMeterView.layoutParams as FrameLayout.LayoutParams
            vuParams.height = (160 * panelView.context.resources.displayMetrics.density).toInt()
            vuMeterView.layoutParams = vuParams
        }
        panelView.requestLayout()
        parentView.requestLayout()
    }
}
