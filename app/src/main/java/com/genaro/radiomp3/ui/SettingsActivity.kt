package com.genaro.radiomp3.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.genaro.radiomp3.R
import com.genaro.radiomp3.data.BufferMode
import com.genaro.radiomp3.data.Prefs
import com.genaro.radiomp3.net.RadioBrowser
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {

    private lateinit var spinnerCountry: Spinner
    private lateinit var txtFolder: TextView
    private lateinit var swKeepOnCharging: Switch
    private lateinit var swAutoHideUi: Switch
    private lateinit var spinnerBuffer: Spinner

    private val pickTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            Prefs.setTreeUri(this, uri)
            val df = DocumentFile.fromTreeUri(this, uri)
            txtFolder.text = "Folder: ${df?.name ?: uri}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Setup immersive mode with double-tap
        val tapArea = findViewById<View>(R.id.tapArea)
        setupImmersiveMode(tapArea)

        title = "Settings"

        spinnerCountry = findViewById(R.id.spinnerCountry)
        txtFolder = findViewById(R.id.txtFolder)
        swKeepOnCharging = findViewById(R.id.swKeepOnCharging)
        swAutoHideUi = findViewById(R.id.swAutoHideUi)
        spinnerBuffer = findViewById(R.id.spinnerBuffer)

        // Load countries
        lifecycleScope.launch {
            RadioBrowser.getCountries(this@SettingsActivity).onSuccess { countries ->
                val names = countries.map { it.name }
                val adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerCountry.adapter = adapter

                val currentCountry = Prefs.getDefaultCountry(this@SettingsActivity)
                val index = names.indexOf(currentCountry)
                if (index >= 0) spinnerCountry.setSelection(index)
            }
        }

        // Buffer mode spinner
        val bufferModes = BufferMode.values().map { it.name }
        val bufferAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bufferModes)
        bufferAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBuffer.adapter = bufferAdapter
        spinnerBuffer.setSelection(Prefs.getBufferMode(this).ordinal)

        // Load values
        Prefs.getTreeUri(this)?.let {
            val df = DocumentFile.fromTreeUri(this, it)
            txtFolder.text = "Folder: ${df?.name ?: it}"
        }
        swKeepOnCharging.isChecked = Prefs.keepOnCharging(this)
        swAutoHideUi.isChecked = Prefs.autoHide(this)

        findViewById<View>(R.id.btnPickFolder).setOnClickListener {
            pickTree.launch(Prefs.getTreeUri(this))
        }

        findViewById<View>(R.id.btnManageFavorites).setOnClickListener {
            startActivity(Intent(this, RadioPickerActivity::class.java))
        }

        findViewById<View>(R.id.btnSetupHomePage).setOnClickListener {
            startActivity(Intent(this, HomePageSetupActivity::class.java))
        }

        findViewById<View>(R.id.btnSave).setOnClickListener {
            val selectedCountry = spinnerCountry.selectedItem?.toString()
            if (selectedCountry != null) {
                Prefs.setDefaultCountry(this, selectedCountry)
            }
            Prefs.setKeepOnCharging(this, swKeepOnCharging.isChecked)
            Prefs.setAutoHide(this, swAutoHideUi.isChecked)
            Prefs.setBufferMode(this, BufferMode.values()[spinnerBuffer.selectedItemPosition])
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<View>(R.id.btnExit).setOnClickListener {
            finish()
        }
    }
}
