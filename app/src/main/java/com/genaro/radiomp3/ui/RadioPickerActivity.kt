package com.genaro.radiomp3.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.genaro.radiomp3.R
import com.genaro.radiomp3.data.AudioQuality
import com.genaro.radiomp3.data.Favorite
import com.genaro.radiomp3.data.Prefs
import com.genaro.radiomp3.data.Station
import com.genaro.radiomp3.net.RadioBrowser
import kotlinx.coroutines.launch

class RadioPickerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var edtSearch: EditText
    private lateinit var btnSave: Button
    private lateinit var txtStatus: TextView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var adapter: StationPickerAdapter

    // Filter buttons
    private lateinit var btnFilterAll: Button
    private lateinit var btnFilterFlac: Button
    private lateinit var btnFilterHigh: Button
    private lateinit var btnFilterMedium: Button
    private lateinit var btnSortBitrate: Button

    private val selectedIds = mutableSetOf<String>()
    private var allStations = listOf<Station>()
    private var globalStations = listOf<Station>() // International high-quality stations
    private var currentFilter: QualityFilter = QualityFilter.ALL
    private var sortByBitrate: Boolean = false
    private var searchQuery: String = ""
    private var isLoadingGlobal = false

    enum class QualityFilter {
        ALL, FLAC, HIGH, MEDIUM
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_picker)

        recyclerView = findViewById(R.id.recyclerView)
        edtSearch = findViewById(R.id.edtSearch)
        btnSave = findViewById(R.id.btnSave)
        txtStatus = findViewById(R.id.txtStatus)
        progressBar = findViewById(R.id.progressBar)

        // Initialize filter buttons
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterFlac = findViewById(R.id.btnFilterFlac)
        btnFilterHigh = findViewById(R.id.btnFilterHigh)
        btnFilterMedium = findViewById(R.id.btnFilterMedium)
        btnSortBitrate = findViewById(R.id.btnSortBitrate)

        // Setup filter button listeners
        btnFilterAll.setOnClickListener { setFilter(QualityFilter.ALL) }
        btnFilterFlac.setOnClickListener { setFilter(QualityFilter.FLAC) }
        btnFilterHigh.setOnClickListener { setFilter(QualityFilter.HIGH) }
        btnFilterMedium.setOnClickListener { setFilter(QualityFilter.MEDIUM) }
        btnSortBitrate.setOnClickListener { toggleBitrateSort() }

        recyclerView.layoutManager = LinearLayoutManager(this)

        var country = Prefs.getDefaultCountry(this)

        // Fix: convert Italian country names to English for API compatibility
        country = when (country.lowercase()) {
            "italia" -> "Italy"
            "spagna" -> "Spain"
            "francia" -> "France"
            "germania" -> "Germany"
            "regno unito" -> "United Kingdom"
            else -> country
        }

        title = "Stations in $country"

        // Load existing favorites to pre-select
        val existingFavorites = Prefs.getFavorites(this)
        selectedIds.addAll(existingFavorites.map { it.id })

        setupAdapter()

        // Search functionality
        edtSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s.toString()
                applyFiltersAndSort()
            }
        })

        btnSave.setOnClickListener {
            saveSelections()
        }

        loadStations(country)
    }

    private fun setupAdapter() {
        adapter = StationPickerAdapter(supportFragmentManager, selectedIds) { station, isChecked ->
            if (isChecked) {
                selectedIds.add(station.id)
            } else {
                selectedIds.remove(station.id)
            }
        }
        recyclerView.adapter = adapter
    }

    private fun loadStations(country: String) {
        btnSave.isEnabled = false
        txtStatus.visibility = android.view.View.VISIBLE
        progressBar.visibility = android.view.View.VISIBLE
        txtStatus.text = "Loading stations from $country..."

        lifecycleScope.launch {
            try {
                val result = RadioBrowser.getStationsByCountry(this@RadioPickerActivity, country)
                result.onSuccess { stations ->
                    txtStatus.visibility = android.view.View.GONE
                    progressBar.visibility = android.view.View.GONE

                    if (stations.isEmpty()) {
                        txtStatus.visibility = android.view.View.VISIBLE
                        txtStatus.text = "No stations found for $country"
                        Toast.makeText(this@RadioPickerActivity, "No stations found", Toast.LENGTH_LONG).show()
                    } else {
                        allStations = stations
                        adapter.submitList(stations)
                        btnSave.isEnabled = true
                        Toast.makeText(this@RadioPickerActivity, "${stations.size} stations loaded", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { e ->
                    progressBar.visibility = android.view.View.GONE
                    txtStatus.visibility = android.view.View.VISIBLE
                    txtStatus.text = "Error: ${e.message}\n\nCheck internet connection"
                    Toast.makeText(this@RadioPickerActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = android.view.View.GONE
                txtStatus.visibility = android.view.View.VISIBLE
                txtStatus.text = "Unexpected error: ${e.message}"
                Toast.makeText(this@RadioPickerActivity, "Unexpected error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun filterStations(query: String) {
        searchQuery = query
        applyFiltersAndSort()
    }

    private fun setFilter(filter: QualityFilter) {
        // If clicking on FLAC/HIGH/MEDIUM, ask user if they want local or global search
        if (filter != QualityFilter.ALL) {
            showSearchScopeDialog(filter)
        } else {
            currentFilter = filter
            updateFilterButtonStates()
            applyFiltersAndSort()
        }
    }

    private fun showSearchScopeDialog(filter: QualityFilter) {
        val country = Prefs.getDefaultCountry(this)
        val filterName = when (filter) {
            QualityFilter.FLAC -> "FLAC"
            QualityFilter.HIGH -> "HIGH"
            QualityFilter.MEDIUM -> "MEDIUM"
            QualityFilter.ALL -> ""
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("$filterName stations")
            .setMessage("Where do you want to search?")
            .setPositiveButton("Only $country") { _, _ ->
                // Search only in local country
                globalStations = emptyList() // Clear global stations
                currentFilter = filter
                updateFilterButtonStates()
                applyFiltersAndSort()
            }
            .setNegativeButton("Worldwide") { _, _ ->
                // Search worldwide
                currentFilter = filter
                updateFilterButtonStates()
                loadGlobalHighQualityStations()
            }
            .setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                // Reset to ALL filter
                currentFilter = QualityFilter.ALL
                updateFilterButtonStates()
            }
            .show()
    }

    private fun toggleBitrateSort() {
        sortByBitrate = !sortByBitrate
        updateSortButtonState()
        applyFiltersAndSort()
    }

    private fun updateFilterButtonStates() {
        val activeColor = android.graphics.Color.parseColor("#1DB954")
        val inactiveColor = android.graphics.Color.parseColor("#3A3A3A")
        val activeTextColor = android.graphics.Color.parseColor("#FFFFFF")
        val inactiveTextColor = android.graphics.Color.parseColor("#888888")

        fun setButtonState(button: Button, isActive: Boolean) {
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (isActive) activeColor else inactiveColor
            )
            button.setTextColor(if (isActive) activeTextColor else inactiveTextColor)
        }

        setButtonState(btnFilterAll, currentFilter == QualityFilter.ALL)
        setButtonState(btnFilterFlac, currentFilter == QualityFilter.FLAC)
        setButtonState(btnFilterHigh, currentFilter == QualityFilter.HIGH)
        setButtonState(btnFilterMedium, currentFilter == QualityFilter.MEDIUM)
    }

    private fun updateSortButtonState() {
        val activeColor = android.graphics.Color.parseColor("#1DB954")
        val inactiveColor = android.graphics.Color.parseColor("#3A3A3A")
        val activeTextColor = android.graphics.Color.parseColor("#FFFFFF")
        val inactiveTextColor = android.graphics.Color.parseColor("#888888")

        btnSortBitrate.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (sortByBitrate) activeColor else inactiveColor
        )
        btnSortBitrate.setTextColor(if (sortByBitrate) activeTextColor else inactiveTextColor)
        btnSortBitrate.text = if (sortByBitrate) "⬆ BITRATE" else "⬇ BITRATE"
    }

    private fun applyFiltersAndSort() {
        // Combine local and global stations
        val combinedStations = allStations + globalStations
        var filtered = combinedStations

        // Apply search filter
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }

        // Apply quality filter
        when (currentFilter) {
            QualityFilter.FLAC -> {
                filtered = filtered.filter {
                    it.getQualityLevel() == AudioQuality.LOSSLESS
                }
            }
            QualityFilter.HIGH -> {
                filtered = filtered.filter {
                    it.getQualityLevel() == AudioQuality.HIGH
                }
            }
            QualityFilter.MEDIUM -> {
                filtered = filtered.filter {
                    val quality = it.getQualityLevel()
                    quality == AudioQuality.MEDIUM || quality == AudioQuality.HIGH
                }
            }
            QualityFilter.ALL -> {
                // No quality filtering
            }
        }

        // Apply bitrate sorting
        if (sortByBitrate) {
            filtered = filtered.sortedByDescending { it.bitrate ?: 0 }
        }

        adapter.submitList(filtered)
    }

    private fun loadGlobalHighQualityStations() {
        if (isLoadingGlobal) return // Already loading

        isLoadingGlobal = true
        txtStatus.visibility = android.view.View.VISIBLE
        txtStatus.text = "Searching worldwide stations..."

        lifecycleScope.launch {
            try {
                val result = when (currentFilter) {
                    QualityFilter.FLAC -> RadioBrowser.getHighQualityStations(codec = "FLAC")
                    QualityFilter.HIGH -> RadioBrowser.getHighQualityStations(minBitrate = 256)
                    QualityFilter.MEDIUM -> RadioBrowser.getHighQualityStations(minBitrate = 128)
                    QualityFilter.ALL -> return@launch // Should not happen
                }

                result.onSuccess { stations ->
                    globalStations = stations
                    txtStatus.visibility = android.view.View.GONE

                    val filterName = when (currentFilter) {
                        QualityFilter.FLAC -> "FLAC"
                        QualityFilter.HIGH -> "high-quality"
                        QualityFilter.MEDIUM -> "medium-quality"
                        QualityFilter.ALL -> ""
                    }

                    Toast.makeText(
                        this@RadioPickerActivity,
                        "Found ${stations.size} international $filterName stations",
                        Toast.LENGTH_LONG
                    ).show()

                    applyFiltersAndSort()
                }.onFailure { e ->
                    txtStatus.visibility = android.view.View.GONE
                    Toast.makeText(
                        this@RadioPickerActivity,
                        "Error loading worldwide stations: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                isLoadingGlobal = false
            }
        }
    }

    private fun saveSelections() {
        // Prevent saving if stations are not loaded yet
        if (allStations.isEmpty()) {
            Toast.makeText(this, "Stations not loaded yet, please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        val existing = Prefs.getFavorites(this).associateBy { it.id }.toMutableMap()
        var maxOrder = existing.values.maxOfOrNull { it.order } ?: -1

        // Combine local and global stations for saving
        val allAvailableStations = allStations + globalStations

        selectedIds.forEach { id ->
            if (id !in existing) {
                val station = allAvailableStations.find { it.id == id } ?: return@forEach
                existing[id] = Favorite(
                    id = station.id,
                    url = station.url,
                    name = station.name,
                    country = station.country,
                    favicon = station.favicon,
                    homepage = station.homepage,
                    order = ++maxOrder
                )
            }
        }

        // Remove deselected
        existing.keys.retainAll(selectedIds)

        Prefs.setFavorites(this, existing.values.toList())
        Toast.makeText(this, "Favorites saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}

// Modern RecyclerView Adapter using ListAdapter
private class StationPickerAdapter(
    private val fragmentManager: androidx.fragment.app.FragmentManager,
    private val selectedIds: Set<String>,
    private val onCheckChanged: (Station, Boolean) -> Unit
) : ListAdapter<Station, StationPickerAdapter.ViewHolder>(StationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station_picker, parent, false)
        return ViewHolder(view as ViewGroup)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), selectedIds, fragmentManager, onCheckChanged)
    }

    class ViewHolder(private val root: ViewGroup) : RecyclerView.ViewHolder(root) {
        private val checkbox: CheckBox = root.findViewById(R.id.checkbox)
        private val txtName: TextView = root.findViewById(R.id.txtName)
        private val txtDetails: TextView = root.findViewById(R.id.txtDetails)
        private val btnPro: TextView = root.findViewById(R.id.btnPro)

        fun bind(
            station: Station,
            selectedIds: Set<String>,
            fragmentManager: androidx.fragment.app.FragmentManager,
            onCheckChanged: (Station, Boolean) -> Unit
        ) {
            txtName.text = station.name

            val details = buildString {
                station.codec?.let { append(it) }
                val bitrate = station.bitrate ?: 0
                if (bitrate > 0) {
                    if (isNotEmpty()) append(" • ")
                    append("$bitrate kbps")
                }
            }
            txtDetails.text = details.ifEmpty { "Radio station" }

            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = station.id in selectedIds

            checkbox.setOnCheckedChangeListener { _, isChecked ->
                onCheckChanged(station, isChecked)
            }

            root.setOnClickListener {
                checkbox.isChecked = !checkbox.isChecked
            }

            // PRO button click - show technical details
            btnPro.setOnClickListener {
                val bottomSheet = TechnicalDetailsBottomSheet.newInstance(
                    stationId = station.id,
                    stationName = station.name
                )
                bottomSheet.show(fragmentManager, "TechnicalDetails")
            }
        }
    }
}

private class StationDiffCallback : DiffUtil.ItemCallback<Station>() {
    override fun areItemsTheSame(oldItem: Station, newItem: Station): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Station, newItem: Station): Boolean {
        return oldItem == newItem
    }
}
