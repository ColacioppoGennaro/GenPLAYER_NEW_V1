package com.genaro.radiomp3.ui.vu

import android.content.Context
import androidx.appcompat.app.AlertDialog

/**
 * Dialog per opzioni VU Meter - scelta tema Light/Dark
 */
object VuMeterOptionsDialog {
    fun show(
        context: Context,
        current: VuConfig,
        onApply: (VuConfig) -> Unit
    ) {
        val themes = arrayOf("Classic Light (Vintage)", "Dark Mode")
        val currentSelection = if (current.nightMode) 1 else 0

        AlertDialog.Builder(context)
            .setTitle("VU Meter Theme")
            .setSingleChoiceItems(themes, currentSelection) { dialog, which ->
                val newConfig = if (which == 0) {
                    VuConfig.light()
                } else {
                    VuConfig.dark()
                }
                onApply(newConfig)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
