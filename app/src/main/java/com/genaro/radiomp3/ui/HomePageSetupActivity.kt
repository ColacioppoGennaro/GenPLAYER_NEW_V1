package com.genaro.radiomp3.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.genaro.radiomp3.R
import com.genaro.radiomp3.data.HomePageButton
import com.genaro.radiomp3.data.Prefs

class HomePageSetupActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HomePageButtonAdapter
    private val buttons = mutableListOf<HomePageButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_homepage_setup)

        title = "Setup HomePage"

        // Setup RecyclerView
        recyclerView = findViewById(R.id.recyclerViewButtons)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Load buttons and ensure all built-in buttons exist
        buttons.clear()
        val loadedButtons = Prefs.getHomePageButtons(this).toMutableList()

        android.util.Log.d("HomePageSetup", "=== SETUP START ===")
        android.util.Log.d("HomePageSetup", "Loaded ${loadedButtons.size} buttons from Prefs:")
        loadedButtons.forEach { btn ->
            android.util.Log.d("HomePageSetup", "  - ${btn.id}: enabled=${btn.isEnabled}, order=${btn.order}")
        }

        // Ensure all built-in buttons exist (ripristina pulsanti mancanti)
        val builtInIds = listOf("mini_player", "web_radio", "mp3", "youtube", "spotify", "vu_meter")
        val existingIds = loadedButtons.map { it.id }.toSet()
        var hasChanges = false

        // Aggiungi solo i built-in completamente mancanti (abilitati di default)
        val defaultButtons = HomePageButton.getDefaultButtons()
        android.util.Log.d("HomePageSetup", "Checking ${defaultButtons.size} default buttons:")
        defaultButtons.forEach { defaultButton ->
            android.util.Log.d("HomePageSetup", "  Checking default: ${defaultButton.id} (exists=${defaultButton.id in existingIds})")
            if (defaultButton.id !in existingIds) {
                // Aggiungi il pulsante mancante con order alla fine e ABILITATO
                val newButton = defaultButton.copy(
                    order = loadedButtons.size,
                    isEnabled = true  // Solo i pulsanti ripristinati sono abilitati di default
                )
                loadedButtons.add(newButton)
                hasChanges = true
                android.util.Log.w("HomePageSetup", "RESTORED missing button: ${defaultButton.name} (id=${defaultButton.id})")
            }
        }

        buttons.addAll(loadedButtons.sortedBy { it.order })

        android.util.Log.d("HomePageSetup", "Final buttons for adapter (${buttons.size}):")
        buttons.forEach { btn ->
            android.util.Log.d("HomePageSetup", "  - ${btn.id}: enabled=${btn.isEnabled}, type=${btn.type}")
        }
        val miniPlayerInList = buttons.any { it.id == "mini_player" }
        android.util.Log.d("HomePageSetup", "mini_player in list? $miniPlayerInList")

        // Salva subito se abbiamo ripristinato pulsanti mancanti
        if (hasChanges) {
            Prefs.setHomePageButtons(this, buttons)
            Toast.makeText(this, "Missing buttons restored", Toast.LENGTH_SHORT).show()
            android.util.Log.d("HomePageSetup", "Saved changes to Prefs")
        }

        // Create adapter (pass layout resource)
        adapter = HomePageButtonAdapter(
            buttons,
            onCheckedChange = { updatePrefs() },
            onDeleteClick = { button -> deleteButton(button) }
        )
        recyclerView.adapter = adapter

        // Setup drag-to-reorder
        val touchHelper = ItemTouchHelper(HomePageButtonTouchHelper(adapter))
        touchHelper.attachToRecyclerView(recyclerView)

        // Add Custom Button
        findViewById<Button>(R.id.btnAddCustom).setOnClickListener {
            showAddCustomButtonDialog()
        }

        // Reset to Defaults
        findViewById<Button>(R.id.btnResetDefaults).setOnClickListener {
            resetToDefaults()
        }

        // Save & Exit
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            updatePrefs()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            finish()
        }
    }

    private fun updatePrefs() {
        // Aggiorna gli order numbers
        buttons.forEachIndexed { index, button ->
            buttons[index] = button.copy(order = index)
        }

        android.util.Log.d("HomePageSetup", "updatePrefs called, saving ${buttons.size} buttons:")
        buttons.forEach { btn ->
            android.util.Log.d("HomePageSetup", "  SAVE: ${btn.id} enabled=${btn.isEnabled} order=${btn.order} type=${btn.type}")
        }
        val hasMiniPlayer = buttons.any { it.id == "mini_player" }
        android.util.Log.d("HomePageSetup", "mini_player in buttons to save? $hasMiniPlayer")

        Prefs.setHomePageButtons(this, buttons)
        android.util.Log.d("HomePageSetup", "updatePrefs complete")
    }

    private fun deleteButton(button: HomePageButton) {
        if (button.type == HomePageButton.ButtonType.CUSTOM) {
            buttons.remove(button)
            adapter.notifyDataSetChanged()
            updatePrefs()
            Toast.makeText(this, "Button deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Cannot delete built-in buttons", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddCustomButtonDialog() {
        val dialog = AddCustomButtonDialog(this) { customButton ->
            // Aggiungi con order = buttons.size
            val newButton = customButton.copy(order = buttons.size)
            buttons.add(newButton)
            adapter.notifyDataSetChanged()
            updatePrefs()
            Toast.makeText(this, "Button added: ${customButton.name}", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun resetToDefaults() {
        // Conferma con l'utente
        android.app.AlertDialog.Builder(this)
            .setTitle("Reset to Defaults")
            .setMessage("This will remove all custom buttons and restore default buttons. Continue?")
            .setPositiveButton("Reset") { _, _ ->
                // Ripristina i pulsanti default
                buttons.clear()
                buttons.addAll(HomePageButton.getDefaultButtons())
                adapter.notifyDataSetChanged()
                updatePrefs()
                Toast.makeText(this, "Buttons reset to defaults", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
