package com.genaro.radiomp3.ui.vu

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.genaro.radiomp3.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * DialogFragment per il VU Meter - rimane sempre sopra tutto
 * Supporta drag verticale e button handler semplice
 */
class VuMeterDialogFragment : DialogFragment() {

    private var lastY = 0f
    private var isDragging = false
    private var dragEnabled = false
    private var buttonsVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.vu_meter_panel, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val vuMeterView = view.findViewById<RetroVuMeterView>(R.id.vuMeterView)
        val closeBtn = view.findViewById<TextView>(R.id.btnVuClose)
        val optionsBtn = view.findViewById<TextView>(R.id.btnVuOptions)
        val moveBtn = view.findViewById<TextView>(R.id.btnVuMove)
        val buttonContainer = view.findViewById<LinearLayout>(R.id.buttonContainer)

        // Config della view
        vuMeterView.config = VuConfig.light()

        // Setup callback dal servizio audio
        com.genaro.radiomp3.playback.MusicPlayerService.vuMeterCallback = { levels ->
            android.util.Log.d("VU_DEBUG", "VuMeterDialogFragment: Callback - L=${levels.peakL} R=${levels.peakR}")
            view.post {
                vuMeterView.setLevels(levels)
            }
        }

        // Bottoni
        closeBtn.setOnClickListener {
            android.util.Log.d("VU_DEBUG", "VuMeterDialogFragment: Close clicked")
            dismiss()
        }

        optionsBtn.setOnClickListener {
            android.util.Log.d("VU_DEBUG", "VuMeterDialogFragment: Options clicked")
            showOptions(vuMeterView)
        }

        moveBtn.setOnClickListener {
            android.util.Log.d("VU_DEBUG", "VuMeterDialogFragment: Move clicked")
            dragEnabled = true
            buttonContainer.visibility = View.GONE
            buttonsVisible = false
            Toast.makeText(
                requireContext(),
                "Trascina il pannello su/giù",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Click sul panel per toggle buttons
        view.setOnClickListener {
            android.util.Log.d("VU_DEBUG", "VuMeterDialogFragment: Panel clicked")
            toggleButtons()
        }

        // Touch per drag
        view.setOnTouchListener { _, event ->
            if (dragEnabled) {
                handleDragTouch(view, event)
            } else {
                false
            }
        }
    }

    private fun toggleButtons() {
        val view = view ?: return
        val buttonContainer = view.findViewById<LinearLayout>(R.id.buttonContainer)
        buttonsVisible = !buttonsVisible
        buttonContainer.visibility = if (buttonsVisible) View.VISIBLE else View.GONE
    }

    private fun handleDragTouch(view: View, event: MotionEvent): Boolean {
        // Solo in portrait
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (dragEnabled) {
                    isDragging = true
                    lastY = event.rawY
                    android.util.Log.d("VU_DEBUG", "VuMeterDialogFragment: Drag started at ${event.rawY}")
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && dragEnabled) {
                    val deltaY = event.rawY - lastY
                    val newY = view.translationY + deltaY

                    // Limiti di movimento
                    val maxUp = -(view.height * 0.8f)
                    val maxDown = 300f // Permettere di andare giù fino a 300dp
                    view.translationY = min(maxDown, max(maxUp, newY))

                    lastY = event.rawY
                    android.util.Log.d("VU_DEBUG", "VuMeterDialogFragment: Dragging to ${view.translationY}")
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging && dragEnabled) {
                    isDragging = false
                    dragEnabled = false
                    android.util.Log.d("VU_DEBUG", "VuMeterDialogFragment: Drag released at ${view.translationY}")

                    // Chiudere se trascinato troppo
                    if (view.translationY < -(view.height / 3)) {
                        android.util.Log.d("VU_DEBUG", "VuMeterDialogFragment: Dragged UP - closing")
                        dismiss()
                    } else if (view.translationY > view.height / 2) {
                        android.util.Log.d("VU_DEBUG", "VuMeterDialogFragment: Dragged DOWN - closing")
                        dismiss()
                    }
                    // Altrimenti rimane dove è stato lasciato - nessuna animazione

                    return true
                }
            }
        }
        return false
    }

    private fun showOptions(vuMeterView: RetroVuMeterView) {
        VuMeterOptionsDialog.show(requireActivity(), vuMeterView.config) { newConfig ->
            vuMeterView.config = newConfig
            Toast.makeText(requireContext(), "VU Meter theme updated", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()

        // Make dialog fullscreen for landscape
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            dialog?.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            // Portrait: fullscreen width, limited height
            dialog?.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            VuMeterDialogFragment().show(fragmentManager, "vu_meter")
        }
    }
}
