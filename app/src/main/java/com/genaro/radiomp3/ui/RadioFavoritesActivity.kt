package com.genaro.radiomp3.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.genaro.radiomp3.R
import com.genaro.radiomp3.api.RadioApiService
import com.genaro.radiomp3.data.AudioQuality
import com.genaro.radiomp3.data.Favorite
import com.genaro.radiomp3.data.Prefs
import com.genaro.radiomp3.data.Station
import com.genaro.radiomp3.utils.FaviconHelper
import kotlinx.coroutines.launch

class RadioFavoritesActivity : BaseActivity() {

    companion object {
        private const val COLOR_ACTIVE = "#1DB954"
        private const val COLOR_INACTIVE = "#3A3A3A"
        private const val COLOR_ACTIVE_TEXT = "#FFFFFF"
        private const val COLOR_INACTIVE_TEXT = "#888888"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FavoritesAdapter

    // Filter buttons
    private lateinit var btnFilterAll: Button
    private lateinit var btnFilterFlac: Button
    private lateinit var btnFilterHigh: Button
    private lateinit var btnFilterMedium: Button
    private lateinit var btnSortBitrate: Button

    // Cached colors
    private val activeColor by lazy { android.graphics.Color.parseColor(COLOR_ACTIVE) }
    private val inactiveColor by lazy { android.graphics.Color.parseColor(COLOR_INACTIVE) }
    private val activeTextColor by lazy { android.graphics.Color.parseColor(COLOR_ACTIVE_TEXT) }
    private val inactiveTextColor by lazy { android.graphics.Color.parseColor(COLOR_INACTIVE_TEXT) }

    private val activeColorStateList by lazy { android.content.res.ColorStateList.valueOf(activeColor) }
    private val inactiveColorStateList by lazy { android.content.res.ColorStateList.valueOf(inactiveColor) }

    // State
    private var currentFilter: QualityFilter = QualityFilter.ALL
    private var sortByBitrate: Boolean = false
    private var allData = listOf<Pair<Favorite, Station?>>()

    enum class QualityFilter {
        ALL, FLAC, HIGH, MEDIUM
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_favorites)

        // Setup immersive mode with double-tap
        val tapArea = findViewById<View>(R.id.tapArea)
        setupImmersiveMode(tapArea)

        // Back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

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

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        title = "My Radios"

        val favorites = Prefs.getFavorites(this)
        if (favorites.isEmpty()) {
            Toast.makeText(this, "No favorites. Add some in Settings!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        adapter = FavoritesAdapter(
            supportFragmentManager,
            onItemClick = { favorite ->
                val intent = Intent(this, RadioPlayerActivity::class.java).apply {
                    putExtra("station_id", favorite.id)
                    putExtra("station_name", favorite.name)
                    putExtra("station_url", favorite.url)
                    putExtra("station_favicon", favorite.favicon)
                    putExtra("station_homepage", favorite.homepage)
                }
                startActivity(intent)
            }
        )
        recyclerView.adapter = adapter

        // Setup drag & drop with ItemTouchHelper
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0 // no swipe
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition

                // Swap items in the current displayed list
                val mutableData = allData.toMutableList()
                val item = mutableData.removeAt(fromPos)
                mutableData.add(toPos, item)
                allData = mutableData

                adapter.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe action
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // Save new order to preferences when drag is complete
                saveNewOrder()
            }

            override fun isLongPressDragEnabled(): Boolean = true
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        // Initially show favorites without station data
        val initialData = favorites.map { it to null }
        adapter.submitList(initialData)

        // Fetch station data from API in background
        fetchStationDataForFavorites(favorites)
    }

    private fun fetchStationDataForFavorites(favorites: List<Favorite>) {
        lifecycleScope.launch {
            val enrichedData = favorites.map { favorite ->
                val station = RadioApiService.getStationByUuid(this@RadioFavoritesActivity, favorite.id)
                favorite to station
            }
            allData = enrichedData
            applyFiltersAndSort()
        }
    }

    private fun setFilter(filter: QualityFilter) {
        currentFilter = filter
        updateFilterButtonStates()
        applyFiltersAndSort()
    }

    private fun toggleBitrateSort() {
        sortByBitrate = !sortByBitrate
        updateSortButtonState()
        applyFiltersAndSort()
    }

    private fun updateFilterButtonStates() {
        fun setButtonState(button: Button, isActive: Boolean) {
            button.backgroundTintList = if (isActive) activeColorStateList else inactiveColorStateList
            button.setTextColor(if (isActive) activeTextColor else inactiveTextColor)
        }

        setButtonState(btnFilterAll, currentFilter == QualityFilter.ALL)
        setButtonState(btnFilterFlac, currentFilter == QualityFilter.FLAC)
        setButtonState(btnFilterHigh, currentFilter == QualityFilter.HIGH)
        setButtonState(btnFilterMedium, currentFilter == QualityFilter.MEDIUM)
    }

    private fun updateSortButtonState() {
        btnSortBitrate.backgroundTintList = if (sortByBitrate) activeColorStateList else inactiveColorStateList
        btnSortBitrate.setTextColor(if (sortByBitrate) activeTextColor else inactiveTextColor)
        btnSortBitrate.text = if (sortByBitrate) "⬆ BITRATE" else "⬇ BITRATE"
    }

    private fun applyFiltersAndSort() {
        var filteredData = allData

        // Apply quality filter
        when (currentFilter) {
            QualityFilter.FLAC -> {
                filteredData = filteredData.filter { (_, station) ->
                    station?.getQualityLevel() == AudioQuality.LOSSLESS
                }
            }
            QualityFilter.HIGH -> {
                filteredData = filteredData.filter { (_, station) ->
                    station?.getQualityLevel() == AudioQuality.HIGH
                }
            }
            QualityFilter.MEDIUM -> {
                filteredData = filteredData.filter { (_, station) ->
                    val quality = station?.getQualityLevel()
                    quality == AudioQuality.MEDIUM || quality == AudioQuality.HIGH
                }
            }
            QualityFilter.ALL -> {
                // No filtering
            }
        }

        // Apply bitrate sorting
        if (sortByBitrate) {
            filteredData = filteredData.sortedByDescending { (_, station) ->
                station?.bitrate ?: 0
            }
        }

        adapter.submitList(filteredData)
    }

    private fun saveNewOrder() {
        // Update order field in favorites based on current position
        val updatedFavorites = allData.mapIndexed { index, (favorite, _) ->
            favorite.copy(order = index)
        }

        // Save to preferences
        Prefs.setFavorites(this, updatedFavorites)

        Toast.makeText(this, "Order saved", Toast.LENGTH_SHORT).show()
    }

    private class FavoritesAdapter(
        private val fragmentManager: androidx.fragment.app.FragmentManager,
        private val onItemClick: (Favorite) -> Unit
    ) : RecyclerView.Adapter<FavoritesAdapter.ViewHolder>() {

        private var data = listOf<Pair<Favorite, Station?>>()

        fun submitList(newData: List<Pair<Favorite, Station?>>) {
            data = newData
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_radio_favorite, parent, false)
            return ViewHolder(view as ViewGroup)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (favorite, station) = data[position]
            holder.bind(favorite, station, fragmentManager, onItemClick)
        }

        override fun getItemCount() = data.size

        class ViewHolder(private val root: ViewGroup) : RecyclerView.ViewHolder(root) {
            private val imgLogo: ImageView = root.findViewById(R.id.imgLogo)
            private val txtName: TextView = root.findViewById(R.id.txtName)
            private val txtCountry: TextView = root.findViewById(R.id.txtCountry)
            private val txtAudioQuality: TextView = root.findViewById(R.id.txtAudioQuality)
            private val btnPro: TextView = root.findViewById(R.id.btnPro)

            fun bind(
                favorite: Favorite,
                station: Station?,
                fragmentManager: androidx.fragment.app.FragmentManager,
                onClick: (Favorite) -> Unit
            ) {
                txtName.text = favorite.name
                txtCountry.text = favorite.country

                // Show audio quality if available from API
                if (station != null && (station.codec != null || station.bitrate != null)) {
                    txtAudioQuality.text = station.getAudioQuality()
                    txtAudioQuality.visibility = View.VISIBLE
                } else {
                    txtAudioQuality.visibility = View.GONE
                }

                root.setOnClickListener { onClick(favorite) }

                // PRO button click - show technical details
                btnPro.setOnClickListener {
                    val bottomSheet = TechnicalDetailsBottomSheet.newInstance(
                        stationId = favorite.id,
                        stationName = favorite.name
                    )
                    bottomSheet.show(fragmentManager, "TechnicalDetails")
                }

                // Get all possible favicon URLs with fallback strategy
                val faviconUrls = FaviconHelper.getFaviconUrls(
                    apiIconUrl = favorite.favicon,
                    homepage = favorite.homepage,
                    streamUrl = favorite.url
                )

                android.util.Log.d("ImageLoader", "Loading favicon for ${favorite.name}, trying ${faviconUrls.size} URLs")

                // Try loading with cascade fallback
                loadWithFallback(faviconUrls, 0)
            }

            private fun loadWithFallback(urls: List<String>, index: Int) {
                if (index >= urls.size) {
                    // All URLs failed, show placeholder
                    android.util.Log.e("ImageLoader", "All favicon URLs failed, using placeholder")
                    imgLogo.setImageResource(R.drawable.ic_radio_placeholder)
                    return
                }

                val url = urls[index]
                android.util.Log.d("ImageLoader", "Trying favicon URL #${index + 1}/${urls.size}: $url")

                imgLogo.load(url) {
                    crossfade(true)
                    placeholder(R.drawable.ic_radio_placeholder)
                    error(R.drawable.ic_radio_placeholder)
                    listener(
                        onSuccess = { _, _ ->
                            android.util.Log.d("ImageLoader", "✓ SUCCESS loading favicon from URL #${index + 1}: $url")
                        },
                        onError = { _, result ->
                            android.util.Log.w("ImageLoader", "✗ FAILED URL #${index + 1}: ${result.throwable.message}")
                            // Try next URL
                            loadWithFallback(urls, index + 1)
                        }
                    )
                }
            }
        }
    }
}
