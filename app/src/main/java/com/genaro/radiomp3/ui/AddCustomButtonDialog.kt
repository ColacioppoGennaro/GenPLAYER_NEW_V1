package com.genaro.radiomp3.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.GridLayout
import com.genaro.radiomp3.data.HomePageButton
import java.util.UUID

class AddCustomButtonDialog(
    private val context: Context,
    private val onButtonCreated: (HomePageButton) -> Unit
) {

    private var dialog: AlertDialog? = null
    private var currentEmojiDialog: AlertDialog? = null
    private val colors = listOf(
        "#FF5733", "#33FF57", "#3357FF", "#FF33F1",
        "#F1FF33", "#33FFF1", "#FF8C33", "#8C33FF",
        "#FFD700", "#FF6347", "#7B68EE", "#00CED1"
    )
    private val emojis = listOf(
        "📻", "🎵", "🎬", "🎧", "🎮", "🎨", "📱", "💻",
        "⚽", "🏀", "🎯", "🎪", "🎭", "🎤", "🎸", "🎹",
        "📺", "📷", "📹", "🎥", "📡", "🔊", "🎙️", "📻",
        "⭐", "💫", "🌟", "✨", "🔥", "💎", "🎁", "🎈"
    )

    private var selectedColor = colors[0]
    private var selectedEmoji = emojis[0]
    private var selectedColorButton: android.widget.ImageView? = null
    private var emojiDisplay: TextView? = null

    fun show() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }

        // Title input
        val titleInput = EditText(context).apply {
            hint = "Button Name"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        layout.addView(titleInput)

        // Emoji selector
        val emojiLabel = TextView(context).apply {
            text = "Select Emoji:"
            setPadding(0, 20, 0, 10)
            textSize = 14f
        }
        layout.addView(emojiLabel)

        // Display selected emoji
        emojiDisplay = TextView(context).apply {
            text = selectedEmoji
            textSize = 40f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }
        layout.addView(emojiDisplay)

        // Emoji picker button
        val emojiPickerBtn = Button(context).apply {
            text = "Choose Emoji"
            setOnClickListener {
                showEmojiPicker()
            }
        }
        layout.addView(emojiPickerBtn)

        // Link input
        val linkInput = EditText(context).apply {
            hint = "Package name or URL"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        layout.addView(linkInput)

        // Color selector
        val colorLabel = TextView(context).apply {
            text = "Select Color:"
            setPadding(0, 20, 0, 10)
            textSize = 14f
        }
        layout.addView(colorLabel)

        // Color grid (2 rows)
        val colorGrid = GridLayout(context).apply {
            columnCount = 6
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val density = context.resources.displayMetrics.density

        colors.forEachIndexed { index, color ->
            val colorButton = android.widget.ImageView(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 80
                    height = 80
                    setMargins(8, 8, 8, 8)
                }
                isClickable = true
                isFocusable = true

                // Crea drawable con bordo
                val strokeWidth = (3 * density).toInt()
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor(color))
                    setStroke(if (index == 0) strokeWidth else 0, Color.WHITE)
                    cornerRadius = 8 * density
                }
                background = drawable

                // Salva riferimento al primo (selezionato di default)
                if (index == 0) {
                    selectedColorButton = this
                }

                setOnClickListener {
                    // Rimuovi bordo dal pulsante precedente
                    val prevDrawable = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.parseColor(selectedColor))
                        setStroke(0, Color.WHITE)
                        cornerRadius = 8 * density
                    }
                    selectedColorButton?.background = prevDrawable

                    // Applica nuova selezione
                    selectedColor = color
                    selectedColorButton = this

                    // Aggiungi bordo al nuovo pulsante
                    val newDrawable = android.graphics.drawable.GradientDrawable().apply {
                        setColor(Color.parseColor(color))
                        setStroke(strokeWidth, Color.WHITE)
                        cornerRadius = 8 * density
                    }
                    this.background = newDrawable
                }
            }
            colorGrid.addView(colorButton)
        }
        layout.addView(colorGrid)

        dialog = AlertDialog.Builder(context)
            .setTitle("Add Custom Button")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = titleInput.text.toString().trim()
                val link = linkInput.text.toString().trim()

                if (name.isNotEmpty()) {
                    val customButton = HomePageButton(
                        id = "custom_${UUID.randomUUID()}",
                        name = name,
                        emoji = selectedEmoji,
                        color = selectedColor,
                        link = link.ifEmpty { null },
                        order = -1, // Will be set by activity
                        type = HomePageButton.ButtonType.CUSTOM,
                        isEnabled = true
                    )
                    onButtonCreated(customButton)
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Please fill button name",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEmojiPicker() {
        // Container con sfondo nero
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#000000"))
            setPadding(20, 20, 20, 20)
        }

        val emojiGrid = GridLayout(context).apply {
            columnCount = 5  // Ridotto da 6 a 5 per evitare che escano dalla pagina
            setBackgroundColor(Color.parseColor("#000000"))
        }

        val density = context.resources.displayMetrics.density

        emojis.forEach { emoji ->
            val emojiButton = TextView(context).apply {
                text = emoji
                textSize = 36f  // Ridotto da 48f a 36f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = (density * 60).toInt()  // Ridotto da 70 a 60
                    height = (density * 60).toInt()  // Ridotto da 70 a 60
                    setMargins(6, 6, 6, 6)  // Ridotto margini da 8 a 6
                }
                isClickable = true
                isFocusable = true

                // Sfondo grigio scuro per ogni emoji
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#2A2A2A"))
                    cornerRadius = 8 * density
                }
                background = drawable

                setOnClickListener {
                    selectedEmoji = emoji
                    emojiDisplay?.text = emoji
                    // Trova e chiudi il dialog correttamente
                    var parent = this.parent
                    while (parent != null) {
                        if (parent is android.view.ViewGroup) {
                            val context = parent.context
                            if (context is android.app.Activity) {
                                // Trova il dialog aperto
                                break
                            }
                        }
                        parent = parent.parent
                    }
                    // Salva riferimento al dialog prima di mostrarlo
                    currentEmojiDialog?.dismiss()
                }
            }
            emojiGrid.addView(emojiButton)
        }

        container.addView(emojiGrid)

        currentEmojiDialog = AlertDialog.Builder(context)
            .setTitle("Choose Emoji")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .create()

        currentEmojiDialog?.show()
    }
}
